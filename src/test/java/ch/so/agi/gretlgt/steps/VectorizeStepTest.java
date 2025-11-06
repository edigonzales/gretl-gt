package ch.so.agi.gretlgt.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.MultiPolygon;
import org.opengis.feature.simple.SimpleFeature;

class VectorizeStepTest {

    @TempDir
    Path tempDir;

    @Test
    void vectorizeExtractsRequestedValueAsMultipolygon() throws Exception {
        Path inputRaster = Path.of("src/test/data/VectorizeStep/reclass.tif");
        Path geopackage = tempDir.resolve("vectorized.gpkg");

        VectorizeStep step = new VectorizeStep("test");
        step.execute(inputRaster, geopackage, 0, 55d);

        assertTrue(Files.exists(geopackage), "Vectorize step should create the geopackage output");

        try (GeoPackage gpkg = new GeoPackage(geopackage.toFile())) {
            List<FeatureEntry> entries = gpkg.features();
            assertEquals(1, entries.size(), "A single feature layer should be written");

            FeatureEntry entry = entries.get(0);
            assertEquals("reclass", entry.getTableName(), "Layer name should match raster name");
            assertEquals("reclass", entry.getIdentifier(), "Identifier should match the table name");
            assertNotNull(entry.getSrid(), "GeoPackage entry should record the SRID");

            try (SimpleFeatureReader reader = gpkg.reader(entry, null, null)) {
                assertTrue(reader.hasNext(), "Vectorized layer should contain one feature");
                SimpleFeature feature = reader.next();
                assertFalse(reader.hasNext(), "Vectorized layer should contain exactly one feature");

                Object geometry = feature.getDefaultGeometry();
                assertTrue(geometry instanceof MultiPolygon, "Output geometry should be a multipolygon");
                MultiPolygon multiPolygon = (MultiPolygon) geometry;
                assertFalse(multiPolygon.isEmpty(), "Vectorized geometry should contain polygons");

                Double value = (Double) feature.getAttribute("value");
                assertEquals(55d, value, 1e-9, "Attribute value should match the requested cell value");
            }
        }
    }

    @Test
    void vectorizeCreatesEmptyGeometryWhenValueMissing() throws Exception {
        Path inputRaster = Path.of("src/test/data/VectorizeStep/reclass.tif");
        Path geopackage = tempDir.resolve("vectorized-empty.gpkg");

        VectorizeStep step = new VectorizeStep("test");
        step.execute(inputRaster, geopackage, 0, 999d);

        assertTrue(Files.exists(geopackage), "Vectorize step should create the geopackage output");

        try (GeoPackage gpkg = new GeoPackage(geopackage.toFile())) {
            FeatureEntry entry = gpkg.feature("reclass");
            assertNotNull(entry, "Vectorized layer should be retrievable by name");

            try (SimpleFeatureReader reader = gpkg.reader(entry, null, null)) {
                assertFalse(reader.hasNext(), "No feature should be stored when no cells match");
            }
        }
    }
}

