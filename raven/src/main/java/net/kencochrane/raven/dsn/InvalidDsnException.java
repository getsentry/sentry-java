package net.kencochrane.raven.dsn;

public class InvalidDsnException extends RuntimeException {
    public InvalidDsnException() {
    }

    public InvalidDsnException(String message) {
        super(message);
    }

    public InvalidDsnException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidDsnException(Throwable cause) {
        super(cause);
    }
}
