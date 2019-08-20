package io.sentry.event.interfaces;

/**
 * A throwable decorator that holds an {@link ExceptionMechanism} related to the decorated {@link Throwable}.
 */
public final class ExceptionMechanismThrowable extends Throwable {

    private final ExceptionMechanism exceptionMechanism;
    private final Throwable throwable;

    /**
     * A {@link Throwable} that decorates another with a Sentry {@link ExceptionMechanism}.
     * @param mechanism The {@link ExceptionMechanism}.
     * @param throwable The {@link java.lang.Throwable}.
     */
    public ExceptionMechanismThrowable(ExceptionMechanism mechanism, Throwable throwable) {
        this.exceptionMechanism = mechanism;
        this.throwable = throwable;
    }

    public ExceptionMechanism getExceptionMechanism() {
        return exceptionMechanism;
    }

    public Throwable getThrowable() {
        return throwable;
    }
}
