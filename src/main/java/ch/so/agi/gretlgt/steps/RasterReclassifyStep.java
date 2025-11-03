package ch.so.agi.gretlgt.steps;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

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

public class RasterReclassifyStep {
    private GretlLogger log;
    private String taskName;
    
    public RasterReclassifyStep() {
        this(null);
    }
    
    public RasterReclassifyStep(String taskName) {
        if (taskName == null) {
            this.taskName = RasterReclassifyStep.class.getSimpleName();
        } else {
            this.taskName = taskName;
        }
        this.log = LogEnvironment.getLogger(this.getClass());
    }
    
    public void execute(Path inputPath, Path outputPath) throws IOException, NoSuchAuthorityCodeException, FactoryException {
        log.lifecycle(String.format("Start RasterReclassifyStep(Name: %s inputPath: %s outputPath: %s)", taskName, inputPath, outputPath));
        
        AbstractGridFormat format = GridFormatFinder.findFormat(outputPath.toFile());

        System.out.println(outputPath);
        
        GridCoverage2DReader reader = format.getReader(outputPath.toFile());
        GridCoverage2D cov = reader.read(null);
        
        CoordinateReferenceSystem swiss = CRS.decode("EPSG:2056", true);
        GridCoverage2D stamped = RasterReclassify.ensureCrs(cov, swiss);
        
        
        double[] breaks = {0, 55, 60, 65, 70, 500};
        int[] classValues = {0, 55, 60, 65, 70};
        double noData = -100;
        GridCoverage2D out1 = RasterReclassify.reclassifyByBreaks(stamped, 0, breaks, classValues, noData);

        GeoTiffWriter writer = null;
        try {
            writer = new GeoTiffWriter(new File("/Users/stefan/tmp/reclass.tif"));
            writer.write(out1, null);
        } finally {
            if (writer != null) writer.dispose();  // important: releases resources
        }
    }
}
