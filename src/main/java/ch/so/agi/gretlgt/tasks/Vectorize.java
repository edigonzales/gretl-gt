package ch.so.agi.gretlgt.tasks;

import ch.so.agi.gretlgt.logging.GretlLogger;
import ch.so.agi.gretlgt.logging.LogEnvironment;
import ch.so.agi.gretlgt.steps.VectorizeStep;
import ch.so.agi.gretlgt.utils.TaskUtil;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public abstract class Vectorize extends DefaultTask {
    private GretlLogger log;

    public Vectorize() {
        getBand().convention(0);
    }

    /**
     * Input-Rasterdatei, aus der die Multipolygone extrahiert werden sollen.
     */
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getInputRaster();

    /**
     * Ziel-GeoPackage-Datei, welche die extrahierten Multipolygone beinhaltet.
     */
    @OutputFile
    public abstract RegularFileProperty getOutputGeopackage();

    /**
     * Index (beginnend bei null) des Rasterbands, aus dem Polygone extrahiert werden.
     */
    @Input
    @Optional
    public abstract Property<Integer> getBand();

    /**
     * Rasterzellenwerte, die extrahiert und vektorisiert werden sollen.
     */
    @Input
    public abstract ListProperty<Double> getCellValues();

    @TaskAction
    public void execute() {
        log = LogEnvironment.getLogger(Vectorize.class);

        VectorizeStep step = new VectorizeStep(getName());

        Path rasterPath = getInputRaster().get().getAsFile().toPath();
        Path geopackagePath = getOutputGeopackage().get().getAsFile().toPath();
        int band = getBand().get();
        List<Double> cellValues = getCellValues().get();

        if (cellValues == null || cellValues.isEmpty()) {
            throw new IllegalStateException("cellValues must not be empty");
        }
        if (cellValues.stream().anyMatch(Objects::isNull)) {
            throw new IllegalStateException("cellValues must not contain null values");
        }

        try {
            step.execute(rasterPath, geopackagePath, band, cellValues);
        } catch (IOException e) {
            log.error("Failed to vectorize raster " + rasterPath, e);
            GradleException ge = TaskUtil.toGradleException(e);
            throw ge;
        }
    }
}