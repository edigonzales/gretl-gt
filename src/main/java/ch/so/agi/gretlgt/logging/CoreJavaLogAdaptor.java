package ch.so.agi.gretlgt.logging;

import java.util.logging.Logger;

/**
 * {@link GretlLogger} implementation for environments where Gradle's logging is
 * unavailable (unit tests, standalone execution). Messages are mapped to
 * {@link java.util.logging.Logger} levels according to GRETL's semantic levels.
 */
public class CoreJavaLogAdaptor implements GretlLogger {

    private final Logger logger;

    CoreJavaLogAdaptor(Class<?> logSource, Level logLevel) {
        this.logger = java.util.logging.Logger.getLogger(logSource.getName());
        this.logger.setLevel(logLevel.getInnerLevel());
    }

    @Override
    public void info(String msg) {
        logger.fine(msg);
    }

    @Override
    public void debug(String msg) {
        logger.finer(msg);
    }

    @Override
    public void error(String msg, Throwable thrown) {
        logger.log(java.util.logging.Level.SEVERE, msg, thrown);
    }

    @Override
    public void lifecycle(String msg) {
        logger.config(msg);
    }

    Logger getInnerLogger() {
        return logger;
    }
}
