package io.sentry.marshaller.json.connector.classloading;

/**
 * Exception when class not found in runtime classpath.
 */
class UnknownClassException extends RuntimeException {
    /**
     * Class not found in runtime exception.
     *
     * @param msg Reason message.
     */
    UnknownClassException(String msg) {
        super(msg);
    }
}
