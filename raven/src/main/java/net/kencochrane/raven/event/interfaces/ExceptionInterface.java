package net.kencochrane.raven.event.interfaces;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * The Exception interface for Sentry allowing to add an Exception details to an event.
 */
public class ExceptionInterface implements SentryInterface {
    /**
     * Name of the exception interface in Sentry.
     */
    public static final String EXCEPTION_INTERFACE = "sentry.interfaces.Exception";
    private static final Logger logger = LoggerFactory.getLogger(ExceptionInterface.class);
    private final Deque<ExceptionWithStackTrace> exceptions;

    /**
     * Creates a new instance from the given {@code throwable}.
     *
     * @param throwable the {@link Throwable} to build this instance from
     */
    public ExceptionInterface(final Throwable throwable) {
        this(extractExceptionQueue(throwable));
    }

    /**
     * Creates a new instance from the given {@code exceptions}.
     *
     * @param exceptions a {@link Deque} of {@link ExceptionWithStackTrace} to build this instance from
     */
    public ExceptionInterface(final Deque<ExceptionWithStackTrace> exceptions) {
        this.exceptions = exceptions;
    }

    /**
     * Transforms a {@link Throwable} into a Queue of {@link ExceptionWithStackTrace}.
     *
     * @param throwable throwable to transform in a queue of exceptions.
     * @return a queue of exception with stacktrace
     */
    public static Deque<ExceptionWithStackTrace> extractExceptionQueue(Throwable throwable) {
        Deque<ExceptionWithStackTrace> exceptions = new ArrayDeque<ExceptionWithStackTrace>();
        Set<Throwable> circularityDetector = new HashSet<Throwable>();
        StackTraceElement[] enclosingStackTrace = new StackTraceElement[0];

        //Stack the exceptions to send them in the reverse order
        while (throwable != null) {
            if (!circularityDetector.add(throwable)) {
                logger.warn("Exiting a circular exception!");
                break;
            }

            StackTraceInterface stackTrace = new StackTraceInterface(throwable.getStackTrace(), enclosingStackTrace);
            exceptions.push(createExceptionWithStackTraceFrom(throwable, stackTrace));
            enclosingStackTrace = throwable.getStackTrace();
            throwable = throwable.getCause();
        }

        return exceptions;
    }

    private static ExceptionWithStackTrace createExceptionWithStackTraceFrom(final Throwable throwable,
                                                                             final StackTraceInterface stackTrace) {
        final String exceptionMessage = throwable.getMessage();
        final String exceptionClassName = throwable.getClass().getSimpleName();
        final String exceptionPackageName = extractPackageName(throwable);
        return new ExceptionWithStackTrace(exceptionMessage, exceptionClassName, exceptionPackageName, stackTrace);
    }

    private static String extractPackageName(final Throwable throwable) {

        final Package exceptionPackage = throwable.getClass().getPackage();

        if (exceptionPackage != null) {
            return exceptionPackage.getName();
        }

        return null;
    }

    @Override
    public String getInterfaceName() {
        return EXCEPTION_INTERFACE;
    }

    public Deque<ExceptionWithStackTrace> getExceptions() {
        return exceptions;
    }
}
