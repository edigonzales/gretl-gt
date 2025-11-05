package ch.so.agi.gretlgt.logging;

/**
 * Core GRETL logging contract that abstracts over the underlying logging
 * backend (Gradle's logger when executed inside a build, or
 * {@code java.util.logging} when running standalone).
 * <p>
 * GRETL distinguishes the following semantic levels:
 * <ul>
 *   <li>{@link #lifecycle(String)} &ndash; emitted exactly twice per step to
 *       announce start and completion after validation/cleanup.</li>
 *   <li>{@link #info(String)} &ndash; additional progress information aimed at
 *       end users.</li>
 *   <li>{@link #debug(String)} &ndash; verbose diagnostic details to help with
 *       troubleshooting.</li>
 *   <li>{@link #error(String, Throwable)} &ndash; failure summaries including
 *       the underlying exception.</li>
 * </ul>
 * Backends decide which messages to show according to their configured
 * threshold (e.g. {@code lifecycle} implies lifecycle and error output).
 */
public interface GretlLogger {

    public void info(String msg);

    public void debug(String msg);

    public void error(String msg, Throwable thrown);

    public void lifecycle(String msg);
}
