package io.sentry.connection;

/**
 * Exception thrown when attempting to send Events while in a lockdown.
 */
public class LockedDownException extends RuntimeException {

}
