package net.kencochrane.raven.event.interfaces;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Class associating a Sentry exception to its {@link StackTraceInterface}.
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
     * Creates a Sentry exception based on a Java Throwable.
     * <p>
     * The {@code childExceptionStackTrace} parameter is used to define the common frames with the child exception
     * (Exception caused by {@code throwable}).
     * </p>
     *
     * @param throwable                Java exception to send to Sentry.
     * @param childExceptionStackTrace StackTrace of the exception caused by {@code throwable}.
     */
    public ExceptionWithStackTrace(Throwable throwable, StackTraceElement[] childExceptionStackTrace) {
        this.exceptionMessage = throwable.getMessage();
        this.exceptionClassName = throwable.getClass().getSimpleName();
        Package exceptionPackage = throwable.getClass().getPackage();
        this.exceptionPackageName = exceptionPackage != null ? exceptionPackage.getName() : null;
        this.stackTraceInterface = new StackTraceInterface(throwable.getStackTrace(), childExceptionStackTrace);
    }

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
     * <p>
     * Exceptions are stored in the queue from the most recent one to the oldest one.
     * </p>
     *
     * @param throwable throwable to transform in a queue of exceptions.
     * @return a queue of exception with StackTrace.
     */
    public static Deque<ExceptionWithStackTrace> extractExceptionQueue(Throwable throwable) {
        Deque<ExceptionWithStackTrace> exceptions = new ArrayDeque<ExceptionWithStackTrace>();
        Set<Throwable> circularityDetector = new HashSet<Throwable>();
        StackTraceElement[] childExceptionStackTrace = new StackTraceElement[0];

        //Stack the exceptions to send them in the reverse order
        while (throwable != null) {
            if (!circularityDetector.add(throwable)) {
                //TODO: Send a more helpful log message here.
                logger.warn("Exiting a circular exception!");
                break;
            }

            exceptions.add(new ExceptionWithStackTrace(throwable, childExceptionStackTrace));
            childExceptionStackTrace = throwable.getStackTrace();
            throwable = throwable.getCause();
        }

        return exceptions;
    }

    public String getExceptionMessage() {
        return exceptionMessage;
    }

    public String getExceptionClassName() {
        return exceptionClassName;
    }

    /**
     * Gets the exception package name.
     * <p>
     * If there is no package, the value will be {@link #DEFAULT_PACKAGE_NAME}.
     * </p>
     *
     * @return the exception package name or {@link #DEFAULT_PACKAGE_NAME} if it isn't defined.
     */
    public String getExceptionPackageName() {

        if (exceptionPackageName == null) {
            return DEFAULT_PACKAGE_NAME;
        }

        return exceptionPackageName;
    }

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
