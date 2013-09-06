package net.kencochrane.raven.event.interfaces;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Arrays;

/**
 * Wrapper for {@link Throwable} to make it immutable.
 * <p>
 * This throwable is made for the sole purpose of making actual {@link Throwable} instance immutable.<br />
 * Under no circumstances an instance of ImmutableThrowable should be used for flow interruption.
 * </p>
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

    /**
     * Returns the class of the original Throwable.
     * <p>
     * As ImmutableThrowable is encapsulating a Throwable, it's important to be able to retrieve the type of the
     * original exception.
     * </p>
     *
     * @return the class of the original Throwable
     */
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
        Throwable cause = actualThrowable.getCause();
        return (cause != null) ? new ImmutableThrowable(cause) : null;
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
        return null;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        return actualThrowable == ((ImmutableThrowable) o).actualThrowable;
    }

    @Override
    public int hashCode() {
        return actualThrowable.hashCode();
    }
}
