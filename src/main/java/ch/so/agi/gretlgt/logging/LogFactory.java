package ch.so.agi.gretlgt.logging;

/**
 * Returns a Logger instance
 */
public interface LogFactory {
    public GretlLogger getLogger(Class logSource);
}
