package ch.so.agi.gretlgt.logging;

/**
 * Factory that supplies {@link GretlLogger} instances for a given log source.
 * Implementations decide which backend to use (Gradle or core Java) and how
 * levels are mapped.
 */
public interface LogFactory {
    /**
     * Creates a logger for the supplied class.
     *
     * @param logSource class that emits log events; used for backend specific
     *                  categorisation
     * @return configured logger implementation
     */
    public GretlLogger getLogger(Class<?> logSource);
}
