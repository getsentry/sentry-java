package io.sentry.connection;

/**
 * Exception thrown by a {@link io.sentry.connection.Connection} if something went wrong temporarily.
 * <p>
 * This allows connections to know when to back off for a while.
 */
public class ConnectionException extends RuntimeException {
    /**
     * Recommended duration to initiate a lockdown for, in milliseconds.
     */
    private Long recommendedLockdownTime = null;

    //CHECKSTYLE.OFF: JavadocMethod
    public ConnectionException() {

    }

    public ConnectionException(String message) {
        super(message);
    }

    public ConnectionException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConnectionException(String message, Throwable cause, Long recommendedLockdownTime) {
        super(message, cause);
        this.recommendedLockdownTime = recommendedLockdownTime;
    }

    public ConnectionException(Throwable cause) {
        super(cause);
    }

    public Long getRecommendedLockdownTime() {
        return recommendedLockdownTime;
    }
    //CHECKSTYLE.ON: JavadocMethod
}
