package io.sentry.event.interfaces;

import java.util.Deque;

/**
 * The Exception interface for Sentry allowing to add an Exception details to an event.
 */
public class ExceptionInterface implements SentryInterface {
    /**
     * Name of the exception interface in Sentry.
     */
    public static final String EXCEPTION_INTERFACE = "sentry.interfaces.Exception";
    private final Deque<SentryException> exceptions;

    /**
     * Creates a new instance from the given {@code throwable}.
     *
     * @param throwable the {@link Throwable} to build this instance from
     */
    public ExceptionInterface(final Throwable throwable) {
        this(SentryException.extractExceptionQueue(throwable));
    }

    /**
     * Creates a new instance from the given {@code exceptions}.
     *
     * @param exceptions a {@link Deque} of {@link SentryException} to build this instance from
     */
    public ExceptionInterface(final Deque<SentryException> exceptions) {
        this.exceptions = exceptions;
    }

    @Override
    public String getInterfaceName() {
        return EXCEPTION_INTERFACE;
    }

    public Deque<SentryException> getExceptions() {
        return exceptions;
    }

    @Override
    public String toString() {
        return "ExceptionInterface{"
                + "exceptions=" + exceptions
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ExceptionInterface that = (ExceptionInterface) o;

        return exceptions.equals(that.exceptions);

    }

    @Override
    public int hashCode() {
        return exceptions.hashCode();
    }
}
