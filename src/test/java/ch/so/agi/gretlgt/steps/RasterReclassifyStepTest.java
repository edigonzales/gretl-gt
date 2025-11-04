package ch.so.agi.gretlgt.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.GridFormatFinder;
import org.geotools.coverage.util.CoverageUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;

import it.geosolutions.jaiext.range.NoDataContainer;

class RasterReclassifyStepTest {

    private static final double DEFAULT_NO_DATA = -100d;

    @TempDir
    Path tempDir;

    @Test
    void reclassifyProducesOnlyConfiguredClassValues() throws IOException, NoSuchAuthorityCodeException, FactoryException {
        Path input = Path.of("src/test/data/RasterReclassifyStep/Beispiel_Rasterfile.asc");
        Path output = tempDir.resolve("reclass.tif");

        RasterReclassifyStep step = new RasterReclassifyStep("test");
        step.execute(input, output);

        GridCoverage2D coverage = readCoverage(output);
        Set<Double> values = readClassValues(coverage);
        Set<Double> allowed = Set.of(0d, 55d, 60d, 65d, 70d);

        values.forEach(value ->
                assertTrue(allowed.contains(value), "Unexpected class value: " + value));

        assertTrue(values.stream().anyMatch(value -> value != DEFAULT_NO_DATA),
                "Default reclassification should classify at least one pixel");

        NoDataContainer noData = CoverageUtilities.getNoDataProperty(coverage);
        assertNotNull(noData, "Default configuration should retain a noData marker");
        assertEquals(DEFAULT_NO_DATA, noData.getAsSingleValue(),
                "Default configuration should expose the default noData value");
    }

    @Test
    void customBreaksAndNoDataAreApplied() throws IOException, NoSuchAuthorityCodeException, FactoryException {
        Path input = Path.of("src/test/data/RasterReclassifyStep/Beispiel_Rasterfile.asc");
        Path output = tempDir.resolve("custom-reclass.tif");

        double[] breaks = {0, 40, 42, 45};
        double customNoData = -5d;

        RasterReclassifyStep step = new RasterReclassifyStep("test");
        step.execute(input, output, breaks, customNoData);

        GridCoverage2D coverage = readCoverage(output);
        Set<Double> values = readClassValues(coverage);
        Set<Double> expected = Set.of(0d, 40d, 42d);

        assertEquals(expected, values, "Custom reclassification should only yield derived classes");

        NoDataContainer noData = CoverageUtilities.getNoDataProperty(coverage);
        assertNotNull(noData, "Custom configuration should retain a noData marker");
        assertEquals(customNoData, noData.getAsSingleValue(),
                "Custom configuration should expose the provided noData value");
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

    private Set<Double> readClassValues(GridCoverage2D coverage) {
        RenderedImage image = coverage.getRenderedImage();
        Raster raster = image.getData();

        Set<Double> values = new HashSet<>();

        int width = raster.getWidth();
        int height = raster.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double sample = raster.getSampleDouble(x, y, 0);
                values.add(sample);
            }
        }
        return values;
    }
}
