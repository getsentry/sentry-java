package io.sentry.connection;

/**
 * Exception thrown when attempting to send Events while in a lockdown.
 */
public class TooManyRequestsException extends ConnectionException {

    //CHECKSTYLE.OFF: JavadocMethod
    public TooManyRequestsException(String message) {
        super(message);
    }

    public TooManyRequestsException(String message, Throwable cause) {
        super(message, cause);
    }

    public TooManyRequestsException(String message, Throwable cause, Long recommendedLockdownTime) {
        super(message, cause, recommendedLockdownTime);
    }
    //CHECKSTYLE.ON: JavadocMethod

}
