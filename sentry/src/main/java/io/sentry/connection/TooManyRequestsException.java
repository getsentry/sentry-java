package io.sentry.connection;

/**
 * Exception thrown when attempting to send Events while in a lockdown.
 */
public class TooManyRequestsException extends ConnectionException {

    //CHECKSTYLE.OFF: JavadocMethod
    public TooManyRequestsException(
            String message,
            Throwable cause,
            Long recommendedLockdownTime,
            Integer responseCode) {
        super(message, cause, recommendedLockdownTime, responseCode);
    }
    //CHECKSTYLE.ON: JavadocMethod

}
