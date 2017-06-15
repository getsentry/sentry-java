package io.sentry.event.interfaces;

import io.sentry.jvmti.Frame;

import java.util.Arrays;

/**
 * The StackTrace interface for Sentry, allowing to add a stackTrace to an event.
 */
public class StackTraceInterface implements SentryInterface {
    /**
     * Name of the Sentry interface allowing to send a StackTrace.
     */
    public static final String STACKTRACE_INTERFACE = "sentry.interfaces.Stacktrace";
    private final SentryStackTraceElement[] stackTrace;
    private final int framesCommonWithEnclosing;

    /**
     * Creates a StackTrace for an {@link io.sentry.event.Event}.
     *
     * @param stackTrace StackTrace to provide to Sentry.
     */
    public StackTraceInterface(StackTraceElement[] stackTrace) {
        this(stackTrace, new StackTraceElement[0], null);
    }

    /**
     * Creates a StackTrace for an {@link io.sentry.event.Event}.
     * <p>
     * With the help of the enclosing StackTrace, figure out which frames are in common with the parent exception
     * to potentially hide them later in Sentry.
     *
     * @param stackTrace          StackTrace to provide to Sentry.
     * @param enclosingStackTrace StackTrace of the enclosing exception, to determine how many Stack frames
     *                            are in common.
     */
    public StackTraceInterface(StackTraceElement[] stackTrace, StackTraceElement[] enclosingStackTrace) {
        this(stackTrace, enclosingStackTrace, null);
    }

    /**
     * Creates a StackTrace for an {@link io.sentry.event.Event}.
     * <p>
     * With the help of the enclosing StackTrace, figure out which frames are in common with the parent exception
     * to potentially hide them later in Sentry.
     *
     * @param stackTrace StackTrace to provide to Sentry.
     * @param enclosingStackTrace StackTrace of the enclosing exception, to determine how many Stack frames
     *                            are in common.
     * @param cachedFrames Array of cached {@link Frame}s (from the Sentry agent) if available,
     *                     or null.
     */
    public StackTraceInterface(StackTraceElement[] stackTrace, StackTraceElement[] enclosingStackTrace,
                               Frame[] cachedFrames) {
        this.stackTrace = SentryStackTraceElement.fromStackTraceElements(stackTrace, cachedFrames);

        int m = stackTrace.length - 1;
        int n = enclosingStackTrace.length - 1;
        while (m >= 0 && n >= 0 && stackTrace[m].equals(enclosingStackTrace[n])) {
            m--;
            n--;
        }
        framesCommonWithEnclosing = stackTrace.length - 1 - m;
    }

    /**
     * Creates a StackTrace for an {@link io.sentry.event.Event}.
     *
     * @param stackTrace StackTrace to provide to Sentry.
     */
    public StackTraceInterface(SentryStackTraceElement[] stackTrace) {
        this.stackTrace = Arrays.copyOf(stackTrace, stackTrace.length);;
        this.framesCommonWithEnclosing = 0;
    }

    @Override
    public String getInterfaceName() {
        return STACKTRACE_INTERFACE;
    }

    public SentryStackTraceElement[] getStackTrace() {
        return Arrays.copyOf(stackTrace, stackTrace.length);
    }

    public int getFramesCommonWithEnclosing() {
        return framesCommonWithEnclosing;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        StackTraceInterface that = (StackTraceInterface) o;

        return Arrays.equals(stackTrace, that.stackTrace);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(stackTrace);
    }

    @Override
    public String toString() {
        return "StackTraceInterface{"
                + "stackTrace=" + Arrays.toString(stackTrace)
                + '}';
    }
}
