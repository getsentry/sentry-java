package io.sentry.jvmti;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Utility class used by the Sentry Java Agent to store per-frame local variable
 * information for the last thrown exception.
 */
public final class FrameCache {
    private static ThreadLocal<WeakHashMap<Throwable, Frame[]>> result =
        new ThreadLocal<WeakHashMap<Throwable, Frame[]>>() {
            @Override
            protected WeakHashMap<Throwable, Frame[]> initialValue() {
                return new WeakHashMap<>();
            }
        };

    /**
     * Utility class, no public ctor.
     */
    private FrameCache() {

    }

    /**
     * Store the per-frame local variable information for the last exception thrown on this thread.
     *
     * @param throwable Throwable that the provided {@link Frame}s represent.
     * @param frames Array of {@link Frame}s to store
     */
    public static void add(Throwable throwable, Frame[] frames) {
        Map<Throwable, Frame[]> weakMap = result.get();
        weakMap.put(throwable, frames);
    }

    /**
     * Retrieve the per-frame local variable information for the last exception thrown on this thread.
     *
     * @param throwable Throwable to look up cached {@link Frame}s for.
     * @return Array of {@link Frame}s
     */
    public static Frame[] get(Throwable throwable) {
        Map<Throwable, Frame[]> weakMap = result.get();
        return weakMap.get(throwable);
    }
}
