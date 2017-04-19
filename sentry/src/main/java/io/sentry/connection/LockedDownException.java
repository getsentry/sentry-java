package io.sentry.connection;

/**
 * Exception thrown when attempting to send Events while in a lockdown.
 */
public class LockedDownException extends RuntimeException {

    /**
     * Construct a LockedDownException with a message.
     *
     * @param message Exception message.
     */
    public LockedDownException(String message) {
        super(message);
    }

}
