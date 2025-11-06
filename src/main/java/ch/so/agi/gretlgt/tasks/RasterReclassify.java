package ch.so.agi.gretlgt.tasks;

import ch.so.agi.gretlgt.logging.GretlLogger;
import ch.so.agi.gretlgt.logging.LogEnvironment;
import ch.so.agi.gretlgt.steps.RasterReclassifyStep;
import ch.so.agi.gretlgt.utils.TaskUtil;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.opengis.referencing.FactoryException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public abstract class RasterReclassify extends DefaultTask {
    private GretlLogger log;
    
    private static final List<Double> DEFAULT_BREAKS = List.of(0d, 55d, 60d, 65d, 70d, 500d);
    private static final double DEFAULT_NO_DATA = -100d;

    public RasterReclassify() {
        getBreaks().convention(DEFAULT_BREAKS);
        getNoData().convention(DEFAULT_NO_DATA);
    }

    /**
     * Zu reklassifizierende Input-Rasterdatei.
     */
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getInputRaster();

    /**
     * Klassifizierte Output-Rasterdatei.
     */
    @OutputFile
    public abstract RegularFileProperty getOutputRaster();

    /**
     * Liste mit Klassenintervallen. Rasterzellen erhalten jeweils den tieferen Wert des Intervalls.
     */
    @Input
    public abstract ListProperty<Double> getBreaks();

    /**
     * NoData-Wert der Output-Rasterdatei.
     */
    @Input
    public abstract Property<Double> getNoData();

    @TaskAction
    public void execute() {
        log = LogEnvironment.getLogger(RasterReclassify.class);
        
        RasterReclassifyStep step = new RasterReclassifyStep(getName());

        Path inputPath = getInputRaster().get().getAsFile().toPath();
        Path outputPath = getOutputRaster().get().getAsFile().toPath();

        List<Double> breakValues = getBreaks().get();
        if (breakValues == null || breakValues.isEmpty()) {
            throw new IllegalStateException("breaks must not be empty");
        }

        double[] breaks = breakValues.stream()
                .mapToDouble(Double::doubleValue)
                .toArray();

        double noData = getNoData().get();

        try {
            step.execute(inputPath, outputPath, breaks, noData);
        } catch (IOException | FactoryException e) {
            log.error("Failed to reclassify raster " + inputPath, e);
            GradleException ge = TaskUtil.toGradleException(e);
            throw ge;
        }
    }
}
