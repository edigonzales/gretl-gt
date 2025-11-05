package ch.so.agi.gretlgt.logging;

/**
 * Adapts GRETL's semantic log levels to {@link java.util.logging.Level} values
 * used by the standalone logging backend. The mapping preserves the intention
 * of each level even though the underlying names differ (e.g. {@code INFO}
 * corresponds to {@link java.util.logging.Level#FINE}).
 */
public class Level {

    public static final Level ERROR = new Level(java.util.logging.Level.SEVERE);
    public static final Level LIFECYCLE = new Level(java.util.logging.Level.CONFIG);
    public static final Level INFO = new Level(java.util.logging.Level.FINE);
    public static final Level DEBUG = new Level(java.util.logging.Level.FINER);

    private final java.util.logging.Level innerLevel;

    private Level(java.util.logging.Level innerLevel) {
        if (innerLevel == null)
            throw new IllegalArgumentException("innerLevel must not be null");

        this.innerLevel = innerLevel;
    }

    /**
     * Exposes the wrapped {@link java.util.logging.Level} so adaptors can pass
     * it to the underlying logger implementation.
     *
     * @return the mapped {@code java.util.logging.Level}
     */
    java.util.logging.Level getInnerLevel() {
        return innerLevel;
    }
}
