package ch.so.agi.gretlgt.steps;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.geom.AffineTransform;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.GridFormatFinder;
import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.MultiPolygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.metadata.spatial.PixelOrientation;
import org.opengis.filter.Filter;

/**
 * Tests for {@link VectorizeStep}.
 */
class VectorizeStepTest {

    @TempDir
    Path tempDir;

    @Test
    void vectorizesRasterCellsIntoMultipolygon() throws Exception {
        Path raster = Paths.get("src/test/data/VectorizeStep/reclass.tif");
        Path geopackage = tempDir.resolve("vectorized.gpkg");
        double cellValue = 55d;

        VectorizeStep step = new VectorizeStep("test");
        step.execute(raster, geopackage, 0, List.of(cellValue));

        assertTrue(Files.exists(geopackage), "Vectorize step must create the GeoPackage file");

        double expectedArea = calculateExpectedArea(raster, 0, cellValue);

        try (GeoPackage gpkg = new GeoPackage(geopackage.toFile())) {
            gpkg.init();
            FeatureEntry entry = gpkg.feature("reclass");
            assertNotNull(entry, "Expected GeoPackage layer named after the raster");

            try (SimpleFeatureReader reader = gpkg.reader(entry, Filter.INCLUDE, null)) {
                assertTrue(reader.hasNext(), "Result layer must contain a feature");
                SimpleFeature feature = reader.next();
                assertTrue(feature.getDefaultGeometry() instanceof MultiPolygon,
                        "Geometry should be a multipolygon");
                MultiPolygon geometry = (MultiPolygon) feature.getDefaultGeometry();
                assertFalse(geometry.isEmpty(), "Output geometry must not be empty");
                assertEquals(cellValue, ((Number) feature.getAttribute("value")).doubleValue(), 1e-9,
                        "Attribute 'value' must hold the extracted cell value");

                double tolerance = Math.max(1e-6, expectedArea * 1e-6);
                assertEquals(expectedArea, geometry.getArea(), tolerance,
                        "Area of dissolved geometry must equal matching raster cells");

                assertFalse(reader.hasNext(), "Only a single dissolved feature is expected");
            }
        }
    }

    @Test
    void vectorizesMultipleCellValuesIntoSeparateFeatures() throws Exception {
        Path raster = Paths.get("src/test/data/VectorizeStep/reclass.tif");
        Path geopackage = tempDir.resolve("vectorized_multi.gpkg");
        List<Double> cellValues = List.of(55d, 60d, 99d);

        Map<Double, Double> expectedAreas = new HashMap<>();
        for (double value : cellValues) {
            expectedAreas.put(value, calculateExpectedArea(raster, 0, value));
        }

        VectorizeStep step = new VectorizeStep("test");
        step.execute(raster, geopackage, 0, cellValues);

        assertTrue(Files.exists(geopackage), "Vectorize step must create the GeoPackage file");

        try (GeoPackage gpkg = new GeoPackage(geopackage.toFile())) {
            gpkg.init();
            FeatureEntry entry = gpkg.feature("reclass");
            assertNotNull(entry, "Expected GeoPackage layer named after the raster");

            Map<Double, MultiPolygon> geometriesByValue = new HashMap<>();
            try (SimpleFeatureReader reader = gpkg.reader(entry, Filter.INCLUDE, null)) {
                while (reader.hasNext()) {
                    SimpleFeature feature = reader.next();
                    double value = ((Number) feature.getAttribute("value")).doubleValue();
                    assertTrue(feature.getDefaultGeometry() instanceof MultiPolygon,
                            "Geometry should be a multipolygon");
                    geometriesByValue.put(value, (MultiPolygon) feature.getDefaultGeometry());
                }
            }

            long expectedFeatureCount = expectedAreas.values().stream().filter(area -> area > 0).count();
            assertEquals(expectedFeatureCount, geometriesByValue.size(),
                    "Each cell value with matching raster cells should yield a feature");

            for (Map.Entry<Double, Double> expected : expectedAreas.entrySet()) {
                double value = expected.getKey();
                double expectedArea = expected.getValue();
                if (expectedArea > 0) {
                    assertTrue(geometriesByValue.containsKey(value),
                            "Expected feature for value " + value);
                    MultiPolygon geometry = geometriesByValue.get(value);
                    assertFalse(geometry.isEmpty(), "Geometry must not be empty for value " + value);
                    double tolerance = Math.max(1e-6, expectedArea * 1e-6);
                    assertEquals(expectedArea, geometry.getArea(), tolerance,
                            "Area of dissolved geometry must equal matching raster cells for value " + value);
                } else {
                    assertFalse(geometriesByValue.containsKey(value),
                            "No feature should be created for absent value " + value);
                }
            }
        }
    }

    private double calculateExpectedArea(Path rasterPath, int band, double targetValue) throws IOException {
        File rasterFile = rasterPath.toFile();
        AbstractGridFormat format = GridFormatFinder.findFormat(rasterFile);
        GridCoverage2DReader reader = null;
        try {
            reader = format.getReader(rasterFile);
            GridCoverage2D coverage = reader.read(null);
            AffineTransform transform =
                    (AffineTransform) coverage.getGridGeometry().getGridToCRS2D(PixelOrientation.UPPER_LEFT);
            double determinant = transform.getScaleX() * transform.getScaleY()
                    - transform.getShearX() * transform.getShearY();
            double cellArea = Math.abs(determinant);

            Raster raster = coverage.getRenderedImage().getData();
            int minX = raster.getMinX();
            int minY = raster.getMinY();
            int maxX = minX + raster.getWidth();
            int maxY = minY + raster.getHeight();
            int matches = 0;
            for (int y = minY; y < maxY; y++) {
                for (int x = minX; x < maxX; x++) {
                    double sample = raster.getSampleDouble(x, y, band);
                    if (Math.abs(sample - targetValue) < 1e-6) {
                        matches++;
                    }
                }
            }
            return matches * cellArea;
        } finally {
            if (reader != null) {
                reader.dispose();
            }
        }
    }
}
