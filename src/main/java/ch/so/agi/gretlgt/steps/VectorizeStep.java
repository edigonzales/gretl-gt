package ch.so.agi.gretlgt.steps;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.GridFormatFinder;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureSource;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.process.ProcessException;
import org.geotools.process.raster.PolygonExtractionProcess;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import ch.so.agi.gretlgt.logging.GretlLogger;
import ch.so.agi.gretlgt.logging.LogEnvironment;

/**
 * Converts raster cell groups with identical values into multipolygon vector features and
 * persists them in a GeoPackage layer.
 * <p>
 * The step reads the provided raster, extracts polygons per raster value and dissolves adjacent
 * polygons with the same value into multipolygon geometries. Each vector feature stores the
 * dissolves geometry together with the originating raster value in an attribute named {@code value}.
 * The resulting layer is written to the provided GeoPackage using the raster file name (without
 * extension) as layer name.
 * </p>
 */
public class VectorizeStep {
    private static final String VALUE_ATTRIBUTE_NAME = "value";

    private final GretlLogger log;
    private final String taskName;

    /**
     * Creates a step instance using the class name for logging context.
     */
    public VectorizeStep() {
        this(null);
    }

    /**
     * Creates a step instance that logs progress messages with the provided task name.
     *
     * @param taskName optional label used in lifecycle log messages; if {@code null} the class name is used
     */
    public VectorizeStep(String taskName) {
        if (taskName == null) {
            this.taskName = VectorizeStep.class.getSimpleName();
        } else {
            this.taskName = taskName;
        }
        this.log = LogEnvironment.getLogger(this.getClass());
    }

    /**
     * Vectorizes the raster band into multipolygon features grouped by cell value and writes them to a GeoPackage layer.
     *
     * @param rasterPath      path to the raster that should be vectorized
     * @param band            zero-based raster band index to analyze
     * @param geopackagePath  path to the GeoPackage where the vector layer should be written
     * @throws IOException if the raster cannot be read or the GeoPackage cannot be written
     */
    public void execute(Path rasterPath, int band, Path geopackagePath) throws IOException {
        Objects.requireNonNull(rasterPath, "rasterPath");
        Objects.requireNonNull(geopackagePath, "geopackagePath");
        if (band < 0) {
            throw new IllegalArgumentException("Band index must be zero or positive");
        }

        log.lifecycle(String.format(
                "Start VectorizeStep(Name: %s rasterPath: %s band: %d geopackage: %s)",
                taskName,
                rasterPath,
                band,
                geopackagePath));

        GridCoverage2D coverage = readCoverage(rasterPath);
        try {
            List<Double> noDataValues = determineNoDataValues(coverage, band);
            String layerName = deriveLayerName(rasterPath);

            SimpleFeatureCollection dissolved = dissolveByValue(
                    extractPolygons(coverage, band, noDataValues),
                    coverage.getCoordinateReferenceSystem2D(),
                    layerName);
            writeGeoPackageLayer(geopackagePath, layerName, dissolved);
        } finally {
            if (coverage != null) {
                coverage.dispose(true);
            }
        }
    }

    private GridCoverage2D readCoverage(Path rasterPath) throws IOException {
        AbstractGridFormat format = GridFormatFinder.findFormat(rasterPath.toFile());
        GridCoverage2DReader reader = null;
        try {
            reader = format.getReader(rasterPath.toFile());
            if (reader == null) {
                throw new IOException("Unable to create reader for raster: " + rasterPath);
            }
            return reader.read(null);
        } finally {
            if (reader != null) {
                reader.dispose();
            }
        }
    }

    private List<Double> determineNoDataValues(GridCoverage2D coverage, int band) {
        if (band >= coverage.getNumSampleDimensions()) {
            throw new IllegalArgumentException("Band index " + band + " is outside available dimensions");
        }
        double[] noData = coverage.getSampleDimensions()[band].getNoDataValues();
        if (noData == null) {
            return List.of();
        }
        List<Double> result = new ArrayList<>(noData.length);
        for (double value : noData) {
            result.add(value);
        }
        return result;
    }

    private SimpleFeatureCollection extractPolygons(GridCoverage2D coverage,
                                                    int band,
                                                    List<Double> noDataValues) throws IOException {
        PolygonExtractionProcess process = new PolygonExtractionProcess();
        Collection<Number> noDataNumbers = toNumberCollection(noDataValues);
        try {
            return process.execute(coverage,
                    band,
                    Boolean.FALSE,
                    null,
                    noDataNumbers,
                    null,
                    null);
        } catch (ProcessException e) {
            throw new IOException("Failed to extract polygons from raster coverage", e);
        }
    }

    private Collection<Number> toNumberCollection(List<Double> values) {
        if (values.isEmpty()) {
            return List.of();
        }
        List<Number> numbers = new ArrayList<>(values.size());
        for (Double value : values) {
            numbers.add(value);
        }
        return numbers;
    }

    private SimpleFeatureCollection dissolveByValue(SimpleFeatureCollection polygons,
                                                    CoordinateReferenceSystem crs,
                                                    String layerName) {
        Map<Double, List<Geometry>> geometriesByValue = collectGeometriesByValue(polygons);

        GeometryFactory geometryFactory = new GeometryFactory();
        SimpleFeatureType featureType = buildFeatureType(crs, layerName);
        DefaultFeatureCollection collection = new DefaultFeatureCollection();

        int featureIndex = 0;
        for (Map.Entry<Double, List<Geometry>> entry : geometriesByValue.entrySet()) {
            Geometry merged = UnaryUnionOp.union(entry.getValue());
            Geometry geometry = ensureMultiPolygon(merged, geometryFactory);
            SimpleFeature feature = SimpleFeatureBuilder.build(
                    featureType,
                    new Object[]{geometry, entry.getKey()},
                    featureType.getTypeName() + "." + featureIndex++);
            collection.add(feature);
        }

        return collection;
    }

    private Map<Double, List<Geometry>> collectGeometriesByValue(SimpleFeatureCollection polygons) {
        Map<Double, List<Geometry>> geometriesByValue = new LinkedHashMap<>();
        try (SimpleFeatureIterator iterator = polygons.features()) {
            while (iterator.hasNext()) {
                SimpleFeature feature = iterator.next();
                Geometry geometry = (Geometry) feature.getDefaultGeometry();
                if (geometry == null || geometry.isEmpty()) {
                    continue;
                }

                Object attribute = feature.getAttribute(VALUE_ATTRIBUTE_NAME);
                if (!(attribute instanceof Number)) {
                    throw new IllegalStateException("Polygon extraction returned feature without numeric value attribute");
                }

                double value = ((Number) attribute).doubleValue();
                geometriesByValue.computeIfAbsent(value, ignored -> new ArrayList<>()).add(geometry);
            }
        }
        return geometriesByValue;
    }

    private SimpleFeatureType buildFeatureType(CoordinateReferenceSystem crs, String layerName) {
        SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
        typeBuilder.setName(layerName);
        typeBuilder.setCRS(crs);
        typeBuilder.add("geom", MultiPolygon.class);
        typeBuilder.setDefaultGeometry("geom");
        typeBuilder.add(VALUE_ATTRIBUTE_NAME, Double.class);
        return typeBuilder.buildFeatureType();
    }

    private Geometry ensureMultiPolygon(Geometry geometry, GeometryFactory geometryFactory) {
        if (geometry instanceof MultiPolygon) {
            return geometry;
        } else if (geometry instanceof Polygon) {
            return geometryFactory.createMultiPolygon(new Polygon[]{(Polygon) geometry});
        } else if (geometry instanceof GeometryCollection) {
            List<Polygon> polygons = new ArrayList<>(geometry.getNumGeometries());
            for (int i = 0; i < geometry.getNumGeometries(); i++) {
                Geometry part = geometry.getGeometryN(i);
                if (part instanceof Polygon) {
                    polygons.add((Polygon) part);
                }
            }
            if (!polygons.isEmpty()) {
                return geometryFactory.createMultiPolygon(polygons.toArray(new Polygon[0]));
            }
        }
        throw new IllegalArgumentException("Expected polygonal geometry but received: " + geometry.getGeometryType());
    }

    private void writeGeoPackageLayer(Path geopackagePath, String layerName, SimpleFeatureCollection collection)
            throws IOException {
        SimpleFeatureType targetType = collection.getSchema();
        File targetFile = geopackagePath.toFile();
        File parent = targetFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        Map<String, Object> params = Map.of(
                "dbtype", "geopkg",
                "database", geopackagePath.toAbsolutePath().toString());

        DataStore dataStore = null;
        try {
            dataStore = DataStoreFinder.getDataStore(params);
            if (dataStore == null) {
                throw new IOException("Unable to create GeoPackage datastore for " + geopackagePath);
            }
            dropExistingSchema(dataStore, layerName);
            dataStore.createSchema(targetType);
            FeatureSource<SimpleFeatureType, SimpleFeature> source = dataStore.getFeatureSource(layerName);
            if (!(source instanceof SimpleFeatureStore)) {
                throw new IOException("GeoPackage datastore does not provide a writable feature store for " + layerName);
            }

            SimpleFeatureStore store = (SimpleFeatureStore) source;
            try (Transaction transaction = new DefaultTransaction("write")) {
                store.setTransaction(transaction);
                store.addFeatures(collection);
                transaction.commit();
            }
        } finally {
            if (dataStore != null) {
                dataStore.dispose();
            }
        }
    }

    private void dropExistingSchema(DataStore dataStore, String typeName) throws IOException {
        for (String existing : dataStore.getTypeNames()) {
            if (existing.equalsIgnoreCase(typeName)) {
                dataStore.removeSchema(existing);
                break;
            }
        }
    }

    private String deriveLayerName(Path rasterPath) {
        String fileName = rasterPath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            return fileName.substring(0, dot);
        }
        return fileName;
    }
}
