package ch.so.agi.gretlgt.steps;

import ch.so.agi.gretlgt.logging.GretlLogger;
import ch.so.agi.gretlgt.logging.LogEnvironment;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.GridFormatFinder;
import org.geotools.data.DataStore;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.geopkg.GeoPkgDataStoreFactory;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.process.raster.PolygonExtractionProcess;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;

/**
 * Converts raster cells into polygons grouped by identical sample values and writes the result as a
 * multipolygon layer to a GeoPackage.
 */
public class VectorizeStep {
    private final GretlLogger log;
    private final String taskName;

    /**
     * Creates the step using the class name for lifecycle log messages.
     */
    public VectorizeStep() {
        this(null);
    }

    /**
     * Creates the step and tags log messages with the provided task name.
     *
     * @param taskName optional label used when emitting lifecycle log messages; if {@code null} the
     *     class name is used
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
     * Vectorizes the supplied raster band and persists the resulting multipolygon features in a
     * GeoPackage layer whose name matches the raster file name.
     *
     * @param rasterPath path to the raster that should be converted
     * @param band index of the raster band to inspect (zero-based)
     * @param geopackagePath path where the GeoPackage should be written
     * @throws IOException if reading the raster or writing the GeoPackage fails
     */
    public void execute(Path rasterPath, int band, Path geopackagePath) throws IOException {
        Objects.requireNonNull(rasterPath, "rasterPath");
        Objects.requireNonNull(geopackagePath, "geopackagePath");
        if (band < 0) {
            throw new IllegalArgumentException("band must be zero or positive");
        }

        String layerName = deriveLayerName(rasterPath);
        log.lifecycle(
                String.format(
                        "Start VectorizeStep(Name: %s raster: %s band: %d geopackage: %s layer: %s)",
                        taskName,
                        rasterPath,
                        band,
                        geopackagePath,
                        layerName));

        GridCoverage2D coverage = readCoverage(rasterPath);
        PolygonExtractionProcess process = new PolygonExtractionProcess();
        SimpleFeatureCollection polygons =
                process.execute(
                        coverage,
                        Integer.valueOf(band),
                        Boolean.FALSE,
                        null,
                        null,
                        null,
                        null);

        SimpleFeatureType targetType = buildTargetType(layerName, polygons.getSchema());
        writeCollection(polygons, targetType, geopackagePath, layerName);

        log.lifecycle(
                String.format(
                        "Finish VectorizeStep(Name: %s features: %d layer: %s)",
                        taskName,
                        Integer.valueOf(polygons.size()),
                        layerName));
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

    private static String deriveLayerName(Path rasterPath) {
        String fileName = rasterPath.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex);
        }
        return fileName;
    }

    private SimpleFeatureType buildTargetType(String layerName, SimpleFeatureType sourceType) {
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName(layerName);
        builder.setCRS(sourceType.getCoordinateReferenceSystem());
        for (AttributeDescriptor descriptor : sourceType.getAttributeDescriptors()) {
            if (descriptor instanceof GeometryDescriptor) {
                builder.add(descriptor.getLocalName(), MultiPolygon.class);
                builder.setDefaultGeometry(descriptor.getLocalName());
            } else {
                builder.add(descriptor);
            }
        }
        return builder.buildFeatureType();
    }

    private void writeCollection(
            SimpleFeatureCollection source,
            SimpleFeatureType targetType,
            Path geopackagePath,
            String layerName)
            throws IOException {
        File file = geopackagePath.toFile();
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        Files.deleteIfExists(geopackagePath);

        Map<String, Object> params = new HashMap<>();
        params.put("dbtype", "geopkg");
        params.put("database", file.getAbsolutePath());

        GeoPkgDataStoreFactory factory = new GeoPkgDataStoreFactory();
        DataStore dataStore = null;
        try {
            dataStore = factory.createDataStore(params);
            dataStore.createSchema(targetType);

            try (FeatureWriter<SimpleFeatureType, SimpleFeature> writer =
                            dataStore.getFeatureWriterAppend(layerName, Transaction.AUTO_COMMIT);
                    SimpleFeatureIterator iterator = source.features()) {
                while (iterator.hasNext()) {
                    SimpleFeature sourceFeature = iterator.next();
                    SimpleFeature targetFeature = writer.next();

                    for (AttributeDescriptor descriptor : targetType.getAttributeDescriptors()) {
                        String name = descriptor.getLocalName();
                        if (descriptor instanceof GeometryDescriptor) {
                            Geometry geometry = (Geometry) sourceFeature.getDefaultGeometry();
                            targetFeature.setAttribute(name, toMultipolygon(geometry));
                        } else {
                            targetFeature.setAttribute(name, sourceFeature.getAttribute(name));
                        }
                    }
                    writer.write();
                }
            }
        } finally {
            if (dataStore != null) {
                dataStore.dispose();
            }
        }
    }

    private static Geometry toMultipolygon(Geometry geometry) {
        if (geometry == null || geometry.isEmpty()) {
            return geometry;
        }
        if (geometry instanceof MultiPolygon) {
            return geometry;
        }
        if (geometry instanceof Polygon) {
            Polygon polygon = (Polygon) geometry;
            return polygon.getFactory().createMultiPolygon(new Polygon[] {polygon});
        }
        throw new IllegalArgumentException(
                "Expected polygonal geometry but received: " + geometry.getGeometryType());
    }
}

