package ch.so.agi.gretlgt.logging;

/**
 * Central access point for GRETL logging. The environment lazily initialises a
 * {@link LogFactory} that supplies {@link GretlLogger} instances backed either
 * by Gradle's logging system or by {@code java.util.logging} when Gradle is not
 * on the classpath (e.g. in unit tests).
 */
public class LogEnvironment {

    private static LogFactory currentLogFactory = null;

    /**
     * Replaces the global factory. Mostly intended for tests.
     *
     * @param factory the {@link LogFactory} to use from now on
     */
    public static void setLogFactory(LogFactory factory) {
        currentLogFactory = factory;
    }

    /**
     * Initialises the environment with a {@link GradleLogFactory} if no factory
     * was set previously.
     */
    public static void initGradleIntegrated() {
        if (currentLogFactory == null) {
            setLogFactory(new GradleLogFactory());
        }
    }

    /**
     * Initialises the environment with {@link Level#DEBUG} using
     * {@link java.util.logging} if no factory was set previously.
     */
    public static void initStandalone() {
        initStandalone(Level.DEBUG);
    }

    /**
     * Initialises the environment with {@link java.util.logging} at the provided
     * level if no factory was set previously.
     *
     * @param logLevel desired minimum log level
     */
    public static void initStandalone(Level logLevel) {
        if (currentLogFactory == null) {
            setLogFactory(new CoreJavaLogFactory(logLevel));
        }
    }

    /**
     * Returns a logger for the given class, lazily selecting a factory when none
     * has been configured yet. Gradle is preferred when available on the
     * classpath; otherwise the core Java implementation is used.
     *
     * @param logSource the class requesting logging
     * @return a configured {@link GretlLogger}
     * @throws IllegalArgumentException if {@code logSource} is {@code null}
     */
    public static GretlLogger getLogger(Class<?> logSource) {
        if (currentLogFactory == null) {
            try {
                if (Class.forName("org.gradle.api.logging.Logger") != null) {

                }
                setLogFactory(new GradleLogFactory());
            } catch (ClassNotFoundException e) {
                // use java logging if no gradle in classpath
                setLogFactory(new CoreJavaLogFactory(Level.DEBUG));
            }
        }
        if (logSource == null)
            throw new IllegalArgumentException("The logSource must not be null");

        return currentLogFactory.getLogger(logSource);
    }
}
