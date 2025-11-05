package ch.so.agi.gretlgt.logging;

/**
 * {@link LogFactory} implementation that delegates to Gradle's logging
 * infrastructure. Each created logger is backed by
 * {@link org.gradle.api.logging.Logger} and therefore honours the build's log
 * level configuration.
 */
public class GradleLogFactory implements LogFactory {

    GradleLogFactory() {}

    @Override
    public GretlLogger getLogger(Class<?> logSource) {
        return new GradleLogAdaptor(logSource);
    }
}
