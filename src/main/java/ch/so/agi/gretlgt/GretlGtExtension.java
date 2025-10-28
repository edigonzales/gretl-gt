package ch.so.agi.gretlgt;

import org.gradle.api.Project;
import org.gradle.api.provider.Property;

public class GretlGtExtension {
    private final Property<String> defaultCrsCode;

    public GretlGtExtension(Project project) {
        this.defaultCrsCode = project.getObjects().property(String.class);
        this.defaultCrsCode.convention("EPSG:4326");
    }

    public Property<String> getDefaultCrsCode() {
        return defaultCrsCode;
    }
}
