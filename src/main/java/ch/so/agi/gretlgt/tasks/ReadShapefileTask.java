package ch.so.agi.gretlgt.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import java.io.File;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.referencing.CRS;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

public abstract class ReadShapefileTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getShapefile();

    @Input
    public abstract Property<String> getCrsCode();

    @TaskAction
    public void run() {
        File shp = getShapefile().get().getAsFile();
        if (!shp.exists()) {
            throw new IllegalStateException("Shapefile not found: " + shp.getAbsolutePath());
        }
        try {
            FileDataStore store = FileDataStoreFinder.getDataStore(shp);
            SimpleFeatureSource featureSource = store.getFeatureSource();

            String code = getCrsCode().getOrNull();
            CoordinateReferenceSystem crs = null;
            if (code != null && !code.isBlank()) {
                crs = CRS.decode(code, true);
            }

            long count = featureSource.getFeatures().size();
            getLogger().lifecycle("ReadShapefileTask:");
            getLogger().lifecycle("  File: {}", shp.getName());
            getLogger().lifecycle("  Feature count: {}", count);
            if (crs != null) {
                getLogger().lifecycle("  Target CRS: {} — {}", code, crs.getName());
            }
            store.dispose();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read shapefile " + shp.getAbsolutePath(), e);
        }
    }
}
