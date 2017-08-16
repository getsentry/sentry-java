package io.sentry.event.interfaces;

import io.sentry.jvmti.FrameCache;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Class associating a Sentry exception to its {@link StackTraceInterface}.
 */
public final class SentryException implements Serializable {
    /**
     * Name used when the class' package is the default one.
     */
    public static final String DEFAULT_PACKAGE_NAME = "(default)";
    private final String exceptionMessage;
    private final String exceptionClassName;
    private final String exceptionPackageName;
    private final StackTraceInterface stackTraceInterface;

    /**
     * Creates a Sentry exception based on a Java Throwable.
     * <p>
     * The {@code childExceptionStackTrace} parameter is used to define the common frames with the child exception
     * (Exception caused by {@code throwable}).
     *
     * @param throwable                Java exception to send to Sentry.
     * @param childExceptionStackTrace StackTrace of the exception caused by {@code throwable}.
     */
    public SentryException(Throwable throwable, StackTraceElement[] childExceptionStackTrace) {
        this.exceptionMessage = throwable.getMessage();
        this.exceptionClassName = throwable.getClass().getSimpleName();
        Package exceptionPackage = throwable.getClass().getPackage();
        this.exceptionPackageName = exceptionPackage != null ? exceptionPackage.getName() : null;
        this.stackTraceInterface = new StackTraceInterface(
            throwable.getStackTrace(),
            childExceptionStackTrace,
            FrameCache.get(throwable));
    }

    /**
     * Creates a Sentry exception.
     *
     * @param exceptionMessage     message of the exception.
     * @param exceptionClassName   exception's class name (simple name).
     * @param exceptionPackageName exception's package name.
     * @param stackTraceInterface  {@code StackTraceInterface} holding the StackTrace information of the exception.
     */
    public SentryException(String exceptionMessage,
                           String exceptionClassName,
                           String exceptionPackageName,
                           StackTraceInterface stackTraceInterface) {
        this.exceptionMessage = exceptionMessage;
        this.exceptionClassName = exceptionClassName;
        this.exceptionPackageName = exceptionPackageName;
        this.stackTraceInterface = stackTraceInterface;
    }

    /**
     * Transforms a {@link Throwable} into a Queue of {@link SentryException}.
     * <p>
     * Exceptions are stored in the queue from the most recent one to the oldest one.
     *
     * @param throwable throwable to transform in a queue of exceptions.
     * @return a queue of exception with StackTrace.
     */
    public static Deque<SentryException> extractExceptionQueue(Throwable throwable) {
        Deque<SentryException> exceptions = new ArrayDeque<>();
        Set<Throwable> circularityDetector = new HashSet<>();
        StackTraceElement[] childExceptionStackTrace = new StackTraceElement[0];

        //Stack the exceptions to send them in the reverse order
        while (throwable != null && circularityDetector.add(throwable)) {
            exceptions.add(new SentryException(throwable, childExceptionStackTrace));
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
     *
     * @return the exception package name or {@link #DEFAULT_PACKAGE_NAME} if it isn't defined.
     */
    public String getExceptionPackageName() {
        return exceptionPackageName != null ? exceptionPackageName : DEFAULT_PACKAGE_NAME;
    }

    public StackTraceInterface getStackTraceInterface() {
        return stackTraceInterface;
    }

    @Override
    public String toString() {
        return "SentryException{"
            + "exceptionMessage='" + exceptionMessage + '\''
            + ", exceptionClassName='" + exceptionClassName + '\''
            + ", exceptionPackageName='" + exceptionPackageName + '\''
            + ", stackTraceInterface=" + stackTraceInterface
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

        SentryException that = (SentryException) o;

        if (!exceptionClassName.equals(that.exceptionClassName)) {
            return false;
        }
        if (exceptionMessage != null ? !exceptionMessage.equals(that.exceptionMessage)
            : that.exceptionMessage != null) {
            return false;
        }
        if (exceptionPackageName != null ? !exceptionPackageName.equals(that.exceptionPackageName)
            : that.exceptionPackageName != null) {
            return false;
        }

        return stackTraceInterface.equals(that.stackTraceInterface);
    }

    @Override
    public int hashCode() {
        int result = exceptionMessage != null ? exceptionMessage.hashCode() : 0;
        result = 31 * result + exceptionClassName.hashCode();
        result = 31 * result + (exceptionPackageName != null ? exceptionPackageName.hashCode() : 0);
        return result;
    }
}
