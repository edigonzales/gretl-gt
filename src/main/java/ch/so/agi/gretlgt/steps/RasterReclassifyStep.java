package ch.so.agi.gretlgt.steps;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridFormatFinder;
import org.geotools.gce.geotiff.GeoTiffWriter;
import org.geotools.referencing.CRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.geotools.coverage.grid.io.GridCoverage2DReader;

import ch.so.agi.gretlgt.logging.GretlLogger;
import ch.so.agi.gretlgt.logging.LogEnvironment;

/**
 * Reclassifies a raster by applying user provided break points and writing the
 * resulting coverage as a GeoTIFF.
 * <p>
 * The step reads the input raster, ensures the Swiss LV95 (EPSG:2056) coordinate
 * reference system is present, performs the reclassification and persists the
 * result. Break values define the consecutive intervals that are assigned to a
 * class value; by default the lower bound of each interval becomes the class
 * value. The caller can optionally override the value that represents missing
 * data ("no data").
 * </p>
 */
public class RasterReclassifyStep {
    private GretlLogger log;
    private String taskName;

    private static final double[] DEFAULT_BREAKS = {0, 55, 60, 65, 70, 500};
    private static final int[] DEFAULT_CLASS_VALUES = {0, 55, 60, 65, 70};
    private static final double DEFAULT_NO_DATA = -100d;
    
    /**
     * Creates a step instance using the class name for logging context.
     */
    public RasterReclassifyStep() {
        this(null);
    }

    /**
     * Creates a step instance that logs progress messages with the provided task name.
     *
     * @param taskName optional label used in lifecycle log messages; if {@code null} the class name is used
     */
    public RasterReclassifyStep(String taskName) {
        if (taskName == null) {
            this.taskName = RasterReclassifyStep.class.getSimpleName();
        } else {
            this.taskName = taskName;
        }
        this.log = LogEnvironment.getLogger(this.getClass());
    }

    /**
     * Executes the reclassification with the default break points, class values and no-data marker.
     *
     * @param inputPath   path to the raster to reclassify
     * @param outputPath  path where the GeoTIFF result should be written
     * @throws IOException                   if the raster cannot be read or written
     * @throws NoSuchAuthorityCodeException  if the Swiss LV95 CRS cannot be resolved
     * @throws FactoryException              if CRS creation fails for other reasons
     */
    public void execute(Path inputPath, Path outputPath) throws IOException, NoSuchAuthorityCodeException, FactoryException {
        executeInternal(inputPath, outputPath, DEFAULT_BREAKS, DEFAULT_CLASS_VALUES, DEFAULT_NO_DATA);
    }

    /**
     * Executes the reclassification with caller-supplied break points and default no-data handling.
     *
     * @param inputPath   path to the raster to reclassify
     * @param outputPath  path where the GeoTIFF result should be written
     * @param breaks      ordered break points defining the class intervals
     * @throws IOException                   if the raster cannot be read or written
     * @throws NoSuchAuthorityCodeException  if the Swiss LV95 CRS cannot be resolved
     * @throws FactoryException              if CRS creation fails for other reasons
     */
    public void execute(Path inputPath, Path outputPath, double[] breaks) throws IOException, NoSuchAuthorityCodeException, FactoryException {
        execute(inputPath, outputPath, breaks, DEFAULT_NO_DATA);
    }

    /**
     * Executes the reclassification with caller-supplied break points and no-data value.
     *
     * <p>Class values are derived by rounding the lower bound of each break interval. This method delegates to the
     * internal implementation that handles reading, reclassifying and writing the raster.</p>
     *
     * @param inputPath   path to the raster to reclassify
     * @param outputPath  path where the GeoTIFF result should be written
     * @param breaks      ordered break points defining the class intervals
     * @param noData      the value that should represent missing data in the output coverage
     * @throws IOException                   if the raster cannot be read or written
     * @throws NoSuchAuthorityCodeException  if the Swiss LV95 CRS cannot be resolved
     * @throws FactoryException              if CRS creation fails for other reasons
     */
    public void execute(Path inputPath, Path outputPath, double[] breaks, double noData)
            throws IOException, NoSuchAuthorityCodeException, FactoryException {
        Objects.requireNonNull(breaks, "breaks");
        int[] classValues = deriveClassValuesFromBreaks(breaks);
        executeInternal(inputPath, outputPath, breaks, classValues, noData);
    }

    private void executeInternal(Path inputPath, Path outputPath, double[] breaks, int[] classValues, double noData)
            throws IOException, NoSuchAuthorityCodeException, FactoryException {
        log.lifecycle(String.format(
                "Start RasterReclassifyStep(Name: %s inputPath: %s outputPath: %s breaks: %s noData: %s)",
                taskName,
                inputPath,
                outputPath,
                Arrays.toString(breaks),
                noData));

        AbstractGridFormat format = GridFormatFinder.findFormat(inputPath.toFile());
        GridCoverage2DReader reader = null;
        GridCoverage2D cov;
        try {
            reader = format.getReader(inputPath.toFile());
            cov = reader.read(null);
        } finally {
            if (reader != null) {
                reader.dispose();
            }
        }

        CoordinateReferenceSystem swiss = CRS.decode("EPSG:2056", true);
        GridCoverage2D stamped = RasterReclassify.ensureCrs(cov, swiss);

        GridCoverage2D out1 = RasterReclassify.reclassifyByBreaks(stamped, 0, breaks, classValues, noData);

        File outFile = outputPath.toFile();
        File parent = outFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        GeoTiffWriter writer = null;
        try {
            writer = new GeoTiffWriter(outFile);
            writer.write(out1, null);
        } finally {
            if (writer != null) {
                writer.dispose();  // important: releases resources
            }
        }
    }

    private static int[] deriveClassValuesFromBreaks(double[] breaks) {
        if (breaks.length < 2) {
            throw new IllegalArgumentException("Provide at least two break values");
        }
        int bins = breaks.length - 1;
        int[] classValues = new int[bins];
        for (int i = 0; i < bins; i++) {
            classValues[i] = (int) Math.round(breaks[i]);
        }
        return classValues;
    }
}
