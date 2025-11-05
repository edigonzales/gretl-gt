package ch.so.agi.gretlgt.steps;

import java.awt.geom.Point2D;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.locationtech.jts.geom.Coordinate;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.GridFormatFinder;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureSource;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.DirectPosition2D;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.metadata.spatial.PixelOrientation;
import org.opengis.referencing.operation.MathTransform2D;
import org.opengis.referencing.operation.TransformException;

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

            SimpleFeatureCollection dissolved = dissolveByValue(coverage, band, noDataValues, layerName);
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

    private SimpleFeatureCollection dissolveByValue(GridCoverage2D coverage, int band, List<Double> noDataValues,
                                                    String layerName) throws IOException {
        Map<Double, List<Polygon>> polygonsByValue = collectPolygonsByValue(coverage, band, noDataValues);

        GeometryFactory geometryFactory = new GeometryFactory();
        SimpleFeatureType featureType = buildFeatureType(coverage.getCoordinateReferenceSystem2D(), layerName);
        DefaultFeatureCollection collection = new DefaultFeatureCollection();

        int featureIndex = 0;
        for (Map.Entry<Double, List<Polygon>> entry : polygonsByValue.entrySet()) {
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

    private Map<Double, List<Polygon>> collectPolygonsByValue(GridCoverage2D coverage, int band,
                                                              List<Double> noDataValues) throws IOException {
        RenderedImage image = coverage.getRenderedImage();
        Raster raster = image.getData();

        if (band >= raster.getNumBands()) {
            throw new IllegalArgumentException("Band index " + band + " is outside available raster bands");
        }

        GeometryFactory geometryFactory = new GeometryFactory();
        GridGeometry2D gridGeometry = coverage.getGridGeometry();
        MathTransform2D gridToCrs = gridGeometry.getGridToCRS2D(PixelOrientation.UPPER_LEFT);

        Map<Double, List<Polygon>> polygonsByValue = new LinkedHashMap<>();
        GridEnvelope2D gridRange = gridGeometry.getGridRange2D();
        int gridMinX = gridRange.x;
        int gridMinY = gridRange.y;
        int width = gridRange.width;
        int height = gridRange.height;

        int rasterMinX = raster.getMinX();
        int rasterMinY = raster.getMinY();

        for (int y = 0; y < height; y++) {
            int rasterY = rasterMinY + y;
            int gridY = gridMinY + y;
            for (int x = 0; x < width; x++) {
                int rasterX = rasterMinX + x;
                int gridX = gridMinX + x;
                double value = raster.getSampleDouble(rasterX, rasterY, band);
                if (Double.isNaN(value) || containsValue(noDataValues, value)) {
                    continue;
                }
                Polygon cellPolygon = buildCellPolygon(gridToCrs, gridX, gridY, geometryFactory);
                polygonsByValue.computeIfAbsent(value, ignored -> new ArrayList<>()).add(cellPolygon);
            }
        }

        return polygonsByValue;
    }

    private Polygon buildCellPolygon(MathTransform2D gridToCrs,
                                     double gridX, double gridY,
                                     GeometryFactory geometryFactory) throws IOException {
        try {
            Coordinate[] coordinates = new Coordinate[5];
            coordinates[0] = transform(gridToCrs, gridX, gridY);
            coordinates[1] = transform(gridToCrs, gridX + 1, gridY);
            coordinates[2] = transform(gridToCrs, gridX + 1, gridY + 1);
            coordinates[3] = transform(gridToCrs, gridX, gridY + 1);
            coordinates[4] = coordinates[0];
            return geometryFactory.createPolygon(coordinates);
        } catch (TransformException e) {
            throw new IOException("Failed to transform raster cell to CRS coordinates", e);
        }
    }

    private Coordinate transform(MathTransform2D transform,
                                 double x, double y) throws TransformException {
        DirectPosition2D source = new DirectPosition2D(x, y);
        DirectPosition2D target = new DirectPosition2D();
        transform.transform((Point2D) source, target);
        return new Coordinate(target.x, target.y);
    }

    private boolean containsValue(List<Double> values, double candidate) {
        for (Double value : values) {
            if (value != null && Double.compare(value, candidate) == 0) {
                return true;
            }
        }
        return false;
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
