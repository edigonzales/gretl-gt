package ch.so.agi.gretlgt.logging;

/**
 * {@link LogFactory} that creates {@link GretlLogger} instances backed by
 * {@link java.util.logging}. The factory shares a single configured
 * {@link Level} across all created loggers.
 */
public class CoreJavaLogFactory implements LogFactory {

    private final Level globalLogLevel;

    CoreJavaLogFactory(Level globalLogLevel) {
        this.globalLogLevel = globalLogLevel;
    }

    @Override
    public GretlLogger getLogger(Class<?> logSource) {
        return new CoreJavaLogAdaptor(logSource, globalLogLevel);
    }
}
