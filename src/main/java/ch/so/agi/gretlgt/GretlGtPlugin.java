package ch.so.agi.gretlgt;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import ch.so.agi.gretlgt.tasks.ReadShapefileTask;

public class GretlGtPlugin implements Plugin<Project> {
    public static final String EXTENSION_NAME = "gretlgt";

    @Override
    public void apply(Project project) {
        GretlGtExtension ext = project.getExtensions()
            .create(EXTENSION_NAME, GretlGtExtension.class, project);

        project.getTasks().register("readShapefile", ReadShapefileTask.class, t -> {
            t.setGroup("gretl");
            t.setDescription("Reads a shapefile and prints feature info");
            t.getShapefile().convention(
                project.getLayout().getProjectDirectory().file("data/example.shp")
            );
            t.getCrsCode().convention(ext.getDefaultCrsCode());
        });
    }
}
