package ch.so.agi.gretlgt.steps;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.GridFormatFinder;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geopkg.Entry;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.geotools.geometry.jts.Geometries;
import org.geotools.geometry.jts.ReferencedEnvelope;
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
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import ch.so.agi.gretlgt.logging.GretlLogger;
import ch.so.agi.gretlgt.logging.LogEnvironment;
import org.geotools.referencing.CRS;

/**
 * Converts raster cells with a matching value into a dissolved multipolygon and
 * persists the result in a GeoPackage layer.
 * <p>
 * The step relies on {@link PolygonExtractionProcess} (which in turn uses JAI)
 * to vectorise contiguous raster cells. Only cells whose band value equals one
 * of the provided {@code cellValues} are considered. The extracted polygons are
 * dissolved into {@link MultiPolygon} geometries per value, their attribute
 * named {@code value} is set to the extracted cell value and the result is
 * stored in a GeoPackage layer whose table name matches the source raster file
 * name.
 * </p>
 */
public class VectorizeStep {
    private final GretlLogger log;
    private final String taskName;

    /**
     * Creates a vectorize step instance using the class name as logging context.
     */
    public VectorizeStep() {
        this(null);
    }

    /**
     * Creates a vectorize step instance that logs messages using the supplied task name.
     *
     * @param taskName optional descriptive name reported in lifecycle log messages
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
     * Executes the vectorisation pipeline for a raster band and writes the dissolved multipolygon to a GeoPackage.
     *
     * @param rasterPath      path to the raster to analyse
     * @param geopackagePath  destination GeoPackage path
     * @param band            zero-based raster band index to inspect
     * @param cellValues      raster cell values to vectorise
     * @throws IOException              if the raster cannot be read or the GeoPackage cannot be written
     * @throws ProcessException         if the polygon extraction process fails
     * @throws IllegalArgumentException if {@code cellValues} is empty or contains {@code null}
     */
    public void execute(Path rasterPath, Path geopackagePath, int band, Collection<Double> cellValues)
            throws IOException, ProcessException {
        Objects.requireNonNull(rasterPath, "rasterPath");
        Objects.requireNonNull(geopackagePath, "geopackagePath");
        Objects.requireNonNull(cellValues, "cellValues");
        if (cellValues.isEmpty()) {
            throw new IllegalArgumentException("cellValues must not be empty");
        }

        log.lifecycle(String.format(Locale.ROOT,
                "Start VectorizeStep(Name: %s rasterPath: %s geopackagePath: %s band: %d cellValues: %s)",
                taskName,
                rasterPath,
                geopackagePath,
                band,
                cellValues));

        GridCoverage2D coverage = readCoverage(rasterPath);
        PolygonExtractionProcess process = new PolygonExtractionProcess();
        List<DissolvedFeature> dissolvedFeatures = new ArrayList<>();
        SimpleFeatureType extractedType = null;

        for (Double cellValue : cellValues) {
            if (cellValue == null) {
                throw new IllegalArgumentException("cellValues must not contain null values");
            }
            @SuppressWarnings({"rawtypes", "unchecked"})
            List<Range> classificationRanges = new ArrayList<>();
            classificationRanges.add(Range.create(cellValue, true, cellValue, true));
            
            log.lifecycle("JAI-EXT enabled: " + Boolean.getBoolean("org.geotools.coverage.jaiext.enabled"));
            try {
                Class.forName("org.jaitools.media.jai.vectorize.VectorizeDescriptor");
                log.lifecycle("jt-vectorize present ✔");
            } catch (ClassNotFoundException e) {
                log.lifecycle("jt-vectorize missing ✖");
            }
            
            SimpleFeatureCollection extracted =
                    process.execute(coverage, Integer.valueOf(band), Boolean.FALSE, null, null, classificationRanges, null);

            if (extractedType == null) {
                extractedType = extracted.getSchema();
            }

            MultiPolygon dissolved = dissolveToMultipolygon(extracted);
            if (!dissolved.isEmpty()) {
                dissolvedFeatures.add(new DissolvedFeature(dissolved, cellValue));
            }
        }

        if (extractedType == null) {
            throw new IOException("Unable to determine feature type from raster extraction");
        }

        writeToGeoPackage(rasterPath, geopackagePath, extractedType, dissolvedFeatures);

        log.lifecycle(String.format(Locale.ROOT,
                "Finished VectorizeStep(Name: %s rasterPath: %s geopackagePath: %s)",
                taskName,
                rasterPath,
                geopackagePath));
    }

    private GridCoverage2D readCoverage(Path rasterPath) throws IOException {
        File rasterFile = rasterPath.toFile();
        AbstractGridFormat format = GridFormatFinder.findFormat(rasterFile);
        if (format == null) {
            throw new IOException("Unable to determine raster format for " + rasterPath);
        }
        GridCoverage2DReader reader = null;
        try {
            reader = format.getReader(rasterFile);
            if (reader == null) {
                throw new IOException("No reader found for raster " + rasterPath);
            }
            GridCoverage2D coverage = reader.read(null);
            if (coverage == null) {
                throw new IOException("Unable to read raster coverage from " + rasterPath);
            }
            return coverage;
        } finally {
            if (reader != null) {
                reader.dispose();
            }
        }
    }

    private MultiPolygon dissolveToMultipolygon(SimpleFeatureCollection extracted) {
        GeometryFactory geometryFactory = new GeometryFactory();
        List<Geometry> geometries = new ArrayList<>();
        try (SimpleFeatureIterator iterator = extracted.features()) {
            while (iterator.hasNext()) {
                SimpleFeature feature = iterator.next();
                Object geometry = feature.getDefaultGeometry();
                if (geometry instanceof Geometry) {
                    Geometry geom = (Geometry) geometry;
                    if (!geom.isEmpty()) {
                        geometries.add(geom);
                    }
                }
            }
        }

        if (geometries.isEmpty()) {
            return geometryFactory.createMultiPolygon(new Polygon[0]);
        }

        Geometry union = UnaryUnionOp.union(geometries);
        return enforceMultiPolygon(union, geometryFactory);
    }

    private MultiPolygon enforceMultiPolygon(Geometry geometry, GeometryFactory factory) {
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
            List<Polygon> polygons = new ArrayList<>();
            collectPolygons((GeometryCollection) geometry, polygons);
            return factory.createMultiPolygon(polygons.toArray(new Polygon[0]));
        }
        return factory.createMultiPolygon(new Polygon[0]);
    }

    private void collectPolygons(GeometryCollection collection, List<Polygon> target) {
        for (int i = 0; i < collection.getNumGeometries(); i++) {
            Geometry component = collection.getGeometryN(i);
            if (component instanceof Polygon) {
                target.add((Polygon) component);
            } else if (component instanceof MultiPolygon) {
                MultiPolygon mp = (MultiPolygon) component;
                for (int j = 0; j < mp.getNumGeometries(); j++) {
                    target.add((Polygon) mp.getGeometryN(j));
                }
            } else if (component instanceof GeometryCollection) {
                collectPolygons((GeometryCollection) component, target);
            }
        }
    }

    private void writeToGeoPackage(Path rasterPath, Path geopackagePath, SimpleFeatureType extractedType,
            List<DissolvedFeature> dissolvedFeatures) throws IOException {
        String layerName = deriveLayerName(rasterPath);
        GeometryDescriptor geometryDescriptor = extractedType.getGeometryDescriptor();
        CoordinateReferenceSystem crs = geometryDescriptor != null ? geometryDescriptor.getCoordinateReferenceSystem() : null;

        SimpleFeatureType targetType = buildTargetType(layerName, geometryDescriptor, crs);
        ListFeatureCollection collection = new ListFeatureCollection(targetType);
        ReferencedEnvelope bounds = null;
        String geometryName = targetType.getGeometryDescriptor().getLocalName();
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(targetType);

        int featureIndex = 0;
        for (DissolvedFeature featureData : dissolvedFeatures) {
            builder.set(geometryName, featureData.geometry);
            builder.set("value", featureData.value);
            collection.add(builder.buildFeature(Integer.toString(featureIndex++)));

            if (bounds == null) {
                bounds = new ReferencedEnvelope(featureData.geometry.getEnvelopeInternal(), crs);
            } else {
                bounds.expandToInclude(featureData.geometry.getEnvelopeInternal());
            }
            builder.reset();
        }

        File geopackageFile = geopackagePath.toFile();
        File parent = geopackageFile.getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
        Files.deleteIfExists(geopackagePath);

        FeatureEntry entry = new FeatureEntry();
        entry.setDataType(Entry.DataType.Feature);
        entry.setTableName(layerName);
        entry.setGeometryColumn(targetType.getGeometryDescriptor().getLocalName());
        entry.setGeometryType(Geometries.MULTIPOLYGON);
        if (bounds != null) {
            entry.setBounds(bounds);
        }
        if (crs != null) {
            try {
                Integer srid = CRS.lookupEpsgCode(crs, true);
                if (srid != null) {
                    entry.setSrid(srid);
                }
            } catch (FactoryException e) {
                throw new IOException("Unable to determine SRID for GeoPackage entry", e);
            }
        }

        try (GeoPackage geoPackage = new GeoPackage(geopackageFile)) {
            geoPackage.init();
            geoPackage.add(entry, collection);
        }
    }

    private SimpleFeatureType buildTargetType(String layerName, GeometryDescriptor sourceGeometry,
            CoordinateReferenceSystem crs) {
        org.geotools.feature.simple.SimpleFeatureTypeBuilder typeBuilder =
                new org.geotools.feature.simple.SimpleFeatureTypeBuilder();
        typeBuilder.setName(layerName);
        if (crs != null) {
            typeBuilder.setCRS(crs);
        }
        String geometryName = sourceGeometry != null ? sourceGeometry.getLocalName() : "the_geom";
        typeBuilder.add(geometryName, MultiPolygon.class);
        typeBuilder.add("value", Double.class);
        return typeBuilder.buildFeatureType();
    }

    private String deriveLayerName(Path rasterPath) {
        Path fileName = rasterPath.getFileName();
        if (fileName == null) {
            return "vectorized";
        }
        String name = fileName.toString();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            return name.substring(0, dotIndex);
        }
        return name;
    }

    private static final class DissolvedFeature {
        private final MultiPolygon geometry;
        private final double value;

        private DissolvedFeature(MultiPolygon geometry, double value) {
            this.geometry = geometry;
            this.value = value;
        }
    }
}
