package ch.so.agi.gretlgt.utils;

/**
 * Baseclass for all Exceptions thrown in the steps code.
 *
 * The Tasks convert pure GretlExceptions to GradleExceptions to avoid wrapping
 * the GretlException in the GradleException, aiming at less confusing Exception
 * nesting.
 */
public class GretlException extends RuntimeException {

    private String type;

    public GretlException() {}

    public GretlException(String message) {
        super(message);
    }

    public GretlException(String message, Throwable cause) {
        super(message, cause);
    }

    public GretlException(Throwable cause) {
        super(cause);
    }

    public GretlException(String type, String message) {
        super(message);
        this.type = type;
    }

    public String getType() {
        return this.type;
    }
}
