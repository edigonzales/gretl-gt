package ch.so.agi.gretlgt.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.geotools.data.DataStore;
import org.geotools.geopkg.GeoPkgDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;

class VectorizeStepTest {

    @TempDir Path tempDir;

    @Test
    void rasterCellsAreGroupedIntoMultipolygons() throws IOException {
        Path rasterPath = Path.of("src/test/data/VectorizeStep/reclass.tif");
        Path geopackagePath = tempDir.resolve("vectorized.gpkg");

        VectorizeStep step = new VectorizeStep("test");
        step.execute(rasterPath, 0, geopackagePath);

        Map<String, Object> params =
                Map.of(
                        "dbtype", "geopkg",
                        "database", geopackagePath.toFile().getAbsolutePath());

        GeoPkgDataStoreFactory factory = new GeoPkgDataStoreFactory();
        DataStore store = null;
        try {
            store = factory.createDataStore(params);
            String[] typeNames = store.getTypeNames();
            assertTrue(typeNames.length > 0, "GeoPackage should contain at least one layer");

            String expectedLayerName = "reclass";
            assertTrue(
                    Arrays.asList(typeNames).contains(expectedLayerName),
                    "Layer names should include " + expectedLayerName);

            SimpleFeatureSource source = store.getFeatureSource(expectedLayerName);
            SimpleFeatureType schema = source.getSchema();

            GeometryDescriptor geometryDescriptor = schema.getGeometryDescriptor();
            assertNotNull(geometryDescriptor, "Layer must expose a geometry attribute");
            assertEquals(
                    MultiPolygon.class,
                    geometryDescriptor.getType().getBinding(),
                    "Vectorized layer must use MultiPolygon geometries");

            AttributeDescriptor valueDescriptor =
                    schema.getAttributeDescriptors().stream()
                            .filter(descriptor -> !(descriptor instanceof GeometryDescriptor))
                            .findFirst()
                            .orElseThrow(
                                    () -> new AssertionError("Expected a raster value attribute"));

            SimpleFeatureCollection features = source.getFeatures();
            assertFalse(features.isEmpty(), "Vectorized layer should contain polygons");

            Set<Double> observedValues = new HashSet<>();
            int featureCount = 0;
            try (SimpleFeatureIterator iterator = features.features()) {
                while (iterator.hasNext()) {
                    SimpleFeature feature = iterator.next();
                    featureCount++;

                    Geometry geometry = (Geometry) feature.getDefaultGeometry();
                    assertTrue(
                            geometry instanceof MultiPolygon,
                            "Each feature should provide a MultiPolygon geometry");

                    Object value = feature.getAttribute(valueDescriptor.getLocalName());
                    assertNotNull(value, "Raster class attribute must not be null");
                    if (value instanceof Number) {
                        observedValues.add(((Number) value).doubleValue());
                    }
                }
            }

            assertTrue(featureCount > 0, "Vectorized layer should contain features");
            assertFalse(
                    observedValues.isEmpty(),
                    "Raster vectorization should yield at least one class value");
        } finally {
            if (store != null) {
                store.dispose();
            }
        }
    }
}

