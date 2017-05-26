package io.sentry.jvmti;

/**
 * Utility class used by the Sentry Java Agent (https://github.com/getsentry/sentry-java-agent) to
 * store local variable information for the last thrown exception.
 */
public final class LocalsCache {
    private static ThreadLocal<Frame[]> result = new ThreadLocal<Frame[]>() {
        @Override
        protected Frame[] initialValue() {
            return new Frame[0];
        }
    };

    /**
     * Utility class, no public ctor.
     */
    private LocalsCache() {

    }

    /**
     * Store the local variable information for the last exception thrown on this thread.
     *
     * @param frames Array of {@link Frame}s to store
     */
    public static void setCache(Frame[] frames) {
        result.set(frames);
    }

    /**
     * Retrieve the local variable information for the last exception thrown on this thread.
     *
     * @return Array of {@link Frame}s
     */
    public static Frame[] getCache() {
        return result.get();
    }
}
