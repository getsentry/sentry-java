package net.kencochrane.raven.event.interfaces;

import java.util.Deque;

/**
 * The Exception interface for Sentry allowing to add an Exception details to an event.
 */
public class ExceptionInterface implements SentryInterface {
    /**
     * Name of the exception interface in Sentry.
     */
    public static final String EXCEPTION_INTERFACE = "sentry.interfaces.Exception";
    private final Deque<ExceptionWithStackTrace> exceptions;

    /**
     * Creates a new instance from the given {@code throwable}.
     *
     * @param throwable the {@link Throwable} to build this instance from
     */
    public ExceptionInterface(final Throwable throwable) {
        this(ExceptionWithStackTrace.extractExceptionQueue(throwable));
    }

    /**
     * Creates a new instance from the given {@code exceptions}.
     *
     * @param exceptions a {@link Deque} of {@link ExceptionWithStackTrace} to build this instance from
     */
    public ExceptionInterface(final Deque<ExceptionWithStackTrace> exceptions) {
        this.exceptions = exceptions;
    }

    @Override
    public String getInterfaceName() {
        return EXCEPTION_INTERFACE;
    }

    public Deque<ExceptionWithStackTrace> getExceptions() {
        return exceptions;
    }
}
