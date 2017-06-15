package io.sentry.jvmti;

/**
 * Utility class used by the Sentry Java Agent (https://github.com/getsentry/sentry-java-agent) to
 * store per-frame local variable information for the last thrown exception.
 */
public final class FrameCache {
    private static ThreadLocal<Frame[]> result = new ThreadLocal<>();

    /**
     * Utility class, no public ctor.
     */
    private FrameCache() {

    }

    /**
     * Store the per-frame local variable information for the last exception thrown on this thread.
     *
     * @param frames Array of {@link Frame}s to store
     */
    public static void add(Frame[] frames) {
        result.set(frames);
    }

    /**
     * Retrieve the per-frame local variable information for the last exception thrown on this thread.
     *
     * @return Array of {@link Frame}s
     */
    public static Frame[] get() {
        return result.get();
    }
}
