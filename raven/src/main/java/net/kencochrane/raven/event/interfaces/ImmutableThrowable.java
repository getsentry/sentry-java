package net.kencochrane.raven.event.interfaces;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Arrays;

/**
 * Wrapper for {@code Throwable} to make it immutable.
 */
public class ImmutableThrowable extends Throwable {
    private final Throwable actualThrowable;

    /**
     * Creates an immutable wrapper for the given throwable.
     *
     * @param actualThrowable exception wrapped.
     */
    public ImmutableThrowable(Throwable actualThrowable) {
        this.actualThrowable = actualThrowable;
    }

    public Class<? extends Throwable> getActualClass() {
        return actualThrowable.getClass();
    }

    @Override
    public String getMessage() {
        return actualThrowable.getMessage();
    }

    @Override
    public String getLocalizedMessage() {
        return actualThrowable.getLocalizedMessage();
    }

    @Override
    public ImmutableThrowable getCause() {
        return new ImmutableThrowable(actualThrowable.getCause());
    }

    @Override
    public Throwable initCause(Throwable cause) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return actualThrowable.toString();
    }

    @Override
    public void printStackTrace() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void printStackTrace(PrintStream s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void printStackTrace(PrintWriter s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Throwable fillInStackTrace() {
        throw new UnsupportedOperationException();
    }

    @Override
    public StackTraceElement[] getStackTrace() {
        StackTraceElement[] stackTrace = actualThrowable.getStackTrace();
        return Arrays.copyOf(stackTrace, stackTrace.length);
    }

    @Override
    public void setStackTrace(StackTraceElement[] stackTrace) {
        throw new UnsupportedOperationException();
    }
}
