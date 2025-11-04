package ch.so.agi.gretlgt.steps;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;

class RasterReclassifyStepTest {

    @TempDir
    Path tempDir;

    @Test
    void reclassifyProducesOnlyConfiguredClassValues() throws IOException, NoSuchAuthorityCodeException, FactoryException {
        Path input = Path.of("src/test/data/RasterReclassifyStep/Beispiel_Rasterfile.asc");
        Path output = tempDir.resolve("reclass.tif");

        RasterReclassifyStep step = new RasterReclassifyStep("test");
        step.execute(input, output);

        AbstractGridFormat format = GridFormatFinder.findFormat(output.toFile());
        GridCoverage2DReader reader = null;
        try {
            reader = format.getReader(output.toFile());
            GridCoverage2D coverage = reader.read(null);
            RenderedImage image = coverage.getRenderedImage();
            Raster raster = image.getData();

            Set<Double> allowedValues = new HashSet<>(Set.of(0d, 55d, 60d, 65d, 70d, -100d));
            Set<Double> classifiedValues = new HashSet<>();

            int width = raster.getWidth();
            int height = raster.getHeight();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    double sample = raster.getSampleDouble(x, y, 0);
                    assertTrue(allowedValues.contains(sample), "Pixel value " + sample + " not part of allowed classes");
                    if (sample != -100d) {
                        classifiedValues.add(sample);
                    }
                }
            }

            assertTrue(!classifiedValues.isEmpty(), "Raster should contain at least one classified pixel");
        } finally {
            if (reader != null) {
                reader.dispose();
            }
        }
    }
}
