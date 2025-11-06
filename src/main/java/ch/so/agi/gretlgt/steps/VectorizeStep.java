package ch.so.agi.gretlgt.steps;

import ch.so.agi.gretlgt.logging.GretlLogger;
import ch.so.agi.gretlgt.logging.LogEnvironment;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.GridFormatFinder;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.Geometries;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.geotools.process.ProcessException;
import org.geotools.process.raster.PolygonExtractionProcess;
import org.jaitools.numeric.Range;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/**
 * Extracts polygons representing raster cells that match a specific value and persists the result
 * as a dissolved multipolygon in a GeoPackage layer.
 */
public class VectorizeStep {

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
     * @param taskName optional label used in lifecycle log messages; if {@code null} the class name
     *     is used
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
     * Vectorizes raster cells matching {@code cellValue} from the supplied raster and writes the
     * dissolved multipolygon to a GeoPackage layer.
     *
     * @param rasterPath path to the raster that should be vectorized
     * @param geopackagePath target GeoPackage file; existing files are replaced
     * @param band the zero-based index of the raster band to inspect
     * @param cellValue the raster value that should be converted into polygons
     * @throws IOException if the raster cannot be read or the GeoPackage cannot be written
     * @throws ProcessException if the polygon extraction fails
     * @throws FactoryException if the coordinate reference system cannot be mapped to an EPSG code
     */
    public void execute(Path rasterPath, Path geopackagePath, int band, double cellValue)
            throws IOException, ProcessException, FactoryException {
        Objects.requireNonNull(rasterPath, "rasterPath");
        Objects.requireNonNull(geopackagePath, "geopackagePath");
        if (band < 0) {
            throw new IllegalArgumentException("Band index must be zero or greater");
        }

        log.lifecycle(
                String.format(
                        "Start VectorizeStep(Name: %s rasterPath: %s geopackagePath: %s band: %d cellValue: %s)",
                        taskName,
                        rasterPath,
                        geopackagePath,
                        band,
                        cellValue));

        GridCoverage2D coverage = readCoverage(rasterPath);
        SimpleFeatureCollection features = extractPolygons(coverage, band, cellValue);

        GeometryFactory geometryFactory = new GeometryFactory();
        MultiPolygon dissolved = dissolveToMultiPolygon(features, geometryFactory);

        CoordinateReferenceSystem crs = coverage.getCoordinateReferenceSystem2D();
        ReferencedEnvelope coverageBounds = new ReferencedEnvelope(
                coverage.getEnvelope2D().getMinX(),
                coverage.getEnvelope2D().getMaxX(),
                coverage.getEnvelope2D().getMinY(),
                coverage.getEnvelope2D().getMaxY(),
                crs);
        writeGeoPackage(geopackagePath, rasterPath, crs, coverageBounds, dissolved, cellValue);

        log.lifecycle(String.format("End VectorizeStep(Name: %s)", taskName));
    }

    private GridCoverage2D readCoverage(Path rasterPath) throws IOException {
        AbstractGridFormat format = GridFormatFinder.findFormat(rasterPath.toFile());
        GridCoverage2DReader reader = null;
        try {
            reader = format.getReader(rasterPath.toFile());
            return reader.read(null);
        } finally {
            if (reader != null) {
                reader.dispose();
            }
        }
    }

    private SimpleFeatureCollection extractPolygons(GridCoverage2D coverage, int band, double cellValue)
            throws ProcessException {
        PolygonExtractionProcess process = new PolygonExtractionProcess();
        Range<Double> range = Range.create(cellValue, true, cellValue, true);
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<Range> ranges = (List) Collections.singletonList(range);
        return process.execute(coverage, Integer.valueOf(band), Boolean.TRUE, null, null, ranges, null);
    }

    private MultiPolygon dissolveToMultiPolygon(
            SimpleFeatureCollection features, GeometryFactory geometryFactory) {
        List<Geometry> geometries = new ArrayList<>();
        try (SimpleFeatureIterator iterator = features.features()) {
            while (iterator.hasNext()) {
                SimpleFeature feature = iterator.next();
                Object geometry = feature.getDefaultGeometry();
                if (!(geometry instanceof Geometry)) {
                    continue;
                }
                Geometry geom = (Geometry) geometry;
                if (!geom.isEmpty()) {
                    geometries.add(geom);
                }
            }
        }

        if (geometries.isEmpty()) {
            return geometryFactory.createMultiPolygon(new Polygon[0]);
        }

        Geometry union = UnaryUnionOp.union(geometries);
        return toMultiPolygon(union, geometryFactory);
    }

    private MultiPolygon toMultiPolygon(Geometry geometry, GeometryFactory factory) {
        if (geometry == null || geometry.isEmpty()) {
            return factory.createMultiPolygon(new Polygon[0]);
        }
        if (geometry instanceof MultiPolygon) {
            return (MultiPolygon) geometry;
        }
        if (geometry instanceof Polygon) {
            return factory.createMultiPolygon(new Polygon[] {(Polygon) geometry});
        }
        if (geometry instanceof GeometryCollection) {
            Collection<Polygon> polygons = new ArrayList<>();
            GeometryCollection collection = (GeometryCollection) geometry;
            for (int i = 0; i < collection.getNumGeometries(); i++) {
                Geometry part = collection.getGeometryN(i);
                if (part instanceof Polygon) {
                    polygons.add((Polygon) part);
                } else if (part instanceof MultiPolygon) {
                    MultiPolygon mp = (MultiPolygon) part;
                    for (int j = 0; j < mp.getNumGeometries(); j++) {
                        polygons.add((Polygon) mp.getGeometryN(j));
                    }
                }
            }
            if (polygons.isEmpty()) {
                return factory.createMultiPolygon(new Polygon[0]);
            }
            return factory.createMultiPolygon(polygons.toArray(new Polygon[0]));
        }
        throw new IllegalArgumentException(
                "Vectorization produced a non-polygon geometry: " + geometry.getGeometryType());
    }

    private void writeGeoPackage(
            Path geopackagePath,
            Path rasterPath,
            CoordinateReferenceSystem crs,
            ReferencedEnvelope coverageBounds,
            MultiPolygon geometry,
            double cellValue)
            throws IOException, FactoryException {
        File geopackageFile = geopackagePath.toFile();
        File parent = geopackageFile.getParentFile();
        if (parent != null && !parent.exists()) {
            Files.createDirectories(parent.toPath());
        }
        if (geopackageFile.exists() && !geopackageFile.delete()) {
            throw new IOException("Unable to replace existing GeoPackage: " + geopackageFile);
        }

        String layerName = deriveLayerName(rasterPath);

        SimpleFeatureType featureType = buildFeatureType(layerName, crs);
        FeatureEntry entry = buildFeatureEntry(layerName, rasterPath, crs, geometry, coverageBounds, cellValue);

        try (GeoPackage geopackage = new GeoPackage(geopackageFile)) {
            geopackage.init();

            if (geometry.isEmpty()) {
                geopackage.create(entry, featureType);
            } else {
                SimpleFeature feature = buildFeature(featureType, geometry, cellValue);
                ListFeatureCollection collection = new ListFeatureCollection(featureType);
                collection.add(feature);
                geopackage.add(entry, collection);
            }
        }
    }

    private FeatureEntry buildFeatureEntry(
            String layerName,
            Path rasterPath,
            CoordinateReferenceSystem crs,
            MultiPolygon geometry,
            ReferencedEnvelope coverageBounds,
            double cellValue)
            throws FactoryException {
        FeatureEntry entry = new FeatureEntry();
        entry.setTableName(layerName);
        entry.setIdentifier(layerName);
        entry.setDescription(String.format("Polygons for raster value %.3f in %s", cellValue, rasterPath));
        entry.setGeometryColumn("the_geom");
        entry.setGeometryType(Geometries.MULTIPOLYGON);

        if (crs != null) {
            Integer srid = org.geotools.referencing.CRS.lookupEpsgCode(crs, true);
            if (srid != null) {
                entry.setSrid(srid);
                geometry.setSRID(srid);
            }
        }

        if (!geometry.isEmpty()) {
            entry.setBounds(new ReferencedEnvelope(geometry.getEnvelopeInternal(), crs));
        } else if (coverageBounds != null) {
            entry.setBounds(coverageBounds);
        }

        return entry;
    }

    private SimpleFeatureType buildFeatureType(String layerName, CoordinateReferenceSystem crs) {
        SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
        typeBuilder.setName(layerName);
        if (crs != null) {
            typeBuilder.setCRS(crs);
        }
        typeBuilder.add("the_geom", MultiPolygon.class);
        typeBuilder.add("value", Double.class);
        return typeBuilder.buildFeatureType();
    }

    private SimpleFeature buildFeature(
            SimpleFeatureType featureType, MultiPolygon geometry, double cellValue) {
        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(featureType);
        featureBuilder.set("the_geom", geometry);
        featureBuilder.set("value", Double.valueOf(cellValue));
        return featureBuilder.buildFeature("0");
    }

    private String deriveLayerName(Path rasterPath) {
        String fileName = rasterPath.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex);
        }
        return fileName;
    }
}

