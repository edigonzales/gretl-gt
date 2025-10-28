package ch.so.agi.gretlgt;

import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.api.Project;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class GretlGtPluginTest {
    @Test
    void pluginRegistersTask() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("gretl-gt");
        assertNotNull(project.getTasks().findByName("readShapefile"));
    }
}
