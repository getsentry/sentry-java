package net.kencochrane.raven.event.interfaces;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Class associating an exception to its {@link net.kencochrane.raven.event.interfaces.StackTraceInterface}.
 */
public final class ExceptionWithStackTrace {
    private static final Logger logger = LoggerFactory.getLogger(ExceptionWithStackTrace.class);
    /**
     * Name used when the class' package is the default one.
     */
    private static final String DEFAULT_PACKAGE_NAME = "(default)";
    private final String exceptionMessage;
    private final String exceptionClassName;
    private final String exceptionPackageName;
    private final StackTraceInterface stackTraceInterface;

    /**
     * Creates a new instance from the given exceptions data.
     *
     * @param exceptionMessage     the message of the exception
     * @param exceptionClassName   the exception's class name
     * @param exceptionPackageName the exception's package name
     * @param stackTraceInterface  the stack trace interface holding the stack trace information of the exception
     */
    public ExceptionWithStackTrace(String exceptionMessage,
                                   String exceptionClassName,
                                   String exceptionPackageName,
                                   StackTraceInterface stackTraceInterface) {
        this.exceptionMessage = exceptionMessage;
        this.exceptionClassName = exceptionClassName;
        this.exceptionPackageName = exceptionPackageName;
        this.stackTraceInterface = stackTraceInterface;
    }

    /**
     * Transforms a {@link Throwable} into a Queue of {@link ExceptionWithStackTrace}.
     *
     * @param throwable throwable to transform in a queue of exceptions.
     * @return a queue of exception with StackTrace.
     */
    public static Deque<ExceptionWithStackTrace> extractExceptionQueue(Throwable throwable) {
        Deque<ExceptionWithStackTrace> exceptions = new ArrayDeque<ExceptionWithStackTrace>();
        Set<Throwable> circularityDetector = new HashSet<Throwable>();
        StackTraceElement[] enclosingStackTrace = new StackTraceElement[0];

        //Stack the exceptions to send them in the reverse order
        while (throwable != null) {
            if (!circularityDetector.add(throwable)) {
                //TODO: Send a more helpful log message here.
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

    private static ExceptionWithStackTrace createExceptionWithStackTraceFrom(Throwable throwable,
                                                                             StackTraceInterface stackTrace) {
        String exceptionMessage = throwable.getMessage();
        String exceptionClassName = throwable.getClass().getSimpleName();
        String exceptionPackageName = extractPackageName(throwable);
        return new ExceptionWithStackTrace(exceptionMessage, exceptionClassName, exceptionPackageName, stackTrace);
    }

    private static String extractPackageName(Throwable throwable) {
        Package exceptionPackage = throwable.getClass().getPackage();

        if (exceptionPackage != null) {
            return exceptionPackage.getName();
        }

        return null;
    }

    /**
     * Gets the exception message.
     *
     * @return the exception message
     */
    public String getExceptionMessage() {
        return exceptionMessage;
    }

    /**
     * Gets the exception's class name.
     *
     * @return the exception's class name
     */
    public String getExceptionClassName() {
        return exceptionClassName;
    }

    /**
     * Gets the exception's package name.
     *
     * @return the exception's package name
     */
    public String getExceptionPackageName() {

        if (exceptionPackageName == null) {
            return DEFAULT_PACKAGE_NAME;
        }

        return exceptionPackageName;
    }

    /**
     * Gets the exceptions stack trace interface.
     *
     * @return the exceptions stack trace interface
     */
    public StackTraceInterface getStackTraceInterface() {
        return stackTraceInterface;
    }

    /**
     * Gets the exception's stack trace.
     *
     * @return the exception's stack trace
     */
    public StackTraceElement[] getStackTrace() {
        return stackTraceInterface.getStackTrace();
    }
}
