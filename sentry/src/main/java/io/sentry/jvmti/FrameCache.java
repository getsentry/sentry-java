package io.sentry.jvmti;

import java.util.*;

/**
 * Utility class used by the Sentry Java Agent to store per-frame local variable
 * information for the last thrown exception.
 */
public final class FrameCache {
    private static final Set<String> appPackages = new HashSet<>();

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

    public static boolean shouldCacheThrowable(Throwable throwable, int numFrames) {
        if (appPackages.isEmpty()) {
            // only cache frames when 'in app' packages are provided
            return false;
        }

        Map<Throwable, Frame[]> weakMap = result.get();
        Frame[] existing = weakMap.get(throwable);
        if (existing != null && numFrames <= existing.length) {
            return false;
        }

        for (StackTraceElement stackTraceElement : throwable.getStackTrace()) {
            for (String appFrame : appPackages) {
                if (stackTraceElement.getClassName().startsWith(appFrame)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static void addAppPackage(String newAppPackage) {
        appPackages.add(newAppPackage);
    }

    public static int getCacheSize() {
        return result.get().size();
    }
}
