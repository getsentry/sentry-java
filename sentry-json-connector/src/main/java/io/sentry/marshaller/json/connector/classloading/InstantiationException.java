package io.sentry.marshaller.json.connector.classloading;

/**
 * Exception when classpath runtime instantiation fails.
 */
class InstantiationException extends RuntimeException {
    /**
     * Class instantiation failed issue.
     *
     * @param message Reason message.
     * @param cause Original instantiation Throwable cause.
     */
    InstantiationException(String message, Throwable cause) {
        super(message, cause);
    }
}
