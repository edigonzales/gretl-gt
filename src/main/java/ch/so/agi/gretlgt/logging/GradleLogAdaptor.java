package ch.so.agi.gretlgt.logging;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

/**
 * {@link GretlLogger} implementation that bridges to Gradle's structured
 * logging API. Messages are forwarded to the build's logger so they end up in
 * the Gradle console with the expected formatting and grouping.
 */
public class GradleLogAdaptor implements GretlLogger {

    private final Logger logger;

    GradleLogAdaptor(Class<?> logSource) {
        this.logger = Logging.getLogger(logSource);
    }

    @Override
    public void info(String msg) {
        logger.info(msg);
    }

    @Override
    public void debug(String msg) {
        logger.debug(msg);
    }

    @Override
    public void lifecycle(String msg) {
        logger.lifecycle(msg);
    }

    @Override
    public void error(String msg, Throwable thrown) {
        logger.error(msg, thrown);
    }
}
