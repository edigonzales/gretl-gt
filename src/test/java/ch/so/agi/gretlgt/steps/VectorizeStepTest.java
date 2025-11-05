package ch.so.agi.gretlgt.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.GridFormatFinder;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opengis.feature.simple.SimpleFeature;

import org.geotools.data.simple.SimpleFeatureIterator;

class VectorizeStepTest {

    @TempDir
    Path tempDir;

    @Test
    void vectorizeCreatesMultipolygonLayerWithUniqueValues() throws IOException {
        Path input = Path.of("src/test/data/VectorizeStep/reclass.tif");
        Path geopackage = tempDir.resolve("vectorized.gpkg");

        VectorizeStep step = new VectorizeStep("test");
        step.execute(input, 0, geopackage);

        Map<String, Object> params = new HashMap<>();
        params.put("dbtype", "geopkg");
        params.put("database", geopackage.toAbsolutePath().toString());

        DataStore dataStore = DataStoreFinder.getDataStore(params);
        assertNotNull(dataStore, "GeoPackage datastore should be accessible after vectorization");
        try {
            String[] typeNames = dataStore.getTypeNames();
            assertEquals(1, typeNames.length, "Vectorization should create exactly one layer");
            assertEquals("reclass", typeNames[0], "Layer name should match the raster file name");

            SimpleFeatureSource source = dataStore.getFeatureSource("reclass");
            SimpleFeatureCollection features = source.getFeatures();

            Set<Double> values = new HashSet<>();
            try (SimpleFeatureIterator iterator = features.features()) {
                while (iterator.hasNext()) {
                    SimpleFeature feature = iterator.next();
                    Object geometry = feature.getDefaultGeometry();
                    assertTrue(geometry instanceof org.locationtech.jts.geom.MultiPolygon,
                            "Vectorized geometries must be multipolygons");

                    Number valueAttribute = (Number) feature.getAttribute("value");
                    values.add(valueAttribute.doubleValue());
                }
            }

            Set<Double> expectedValues = readRasterValues(input);
            assertEquals(expectedValues, values, "Vectorized layer should contain one feature per raster value");
        } finally {
            if (dataStore != null) {
                dataStore.dispose();
            }
        }
    }

    private Set<Double> readRasterValues(Path rasterPath) throws IOException {
        GridCoverage2D coverage = readCoverage(rasterPath);
        try {
            Set<Double> values = collectDistinctValues(coverage);
            double[] noDataValues = coverage.getSampleDimensions()[0].getNoDataValues();
            if (noDataValues != null) {
                for (double nd : noDataValues) {
                    values.remove(nd);
                }
            }
            return values;
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
            return reader.read(null);
        } finally {
            if (reader != null) {
                reader.dispose();
            }
        }
    }

    private Set<Double> collectDistinctValues(GridCoverage2D coverage) {
        RenderedImage image = coverage.getRenderedImage();
        Raster raster = image.getData();

        Set<Double> values = new HashSet<>();
        int width = raster.getWidth();
        int height = raster.getHeight();
        int minX = raster.getMinX();
        int minY = raster.getMinY();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double sample = raster.getSampleDouble(minX + x, minY + y, 0);
                values.add(sample);
            }
        }
        return values;
    }
}
