package io.sentry.util;

/**
 * backport of Objects Utils which is not available on Android yet.
 */
public final class Objects {
    private Objects() {}

    /**
     * backport of Objects.requireNonNull which is not available on Android yet.
     * @param obj object to NPE check
     * @param message custom message for NullPointerException
     * @param <T> obj type
     * @return returns obj itself if it's not null
     */
    public static <T> T requireNonNull(T obj, String message) {
        if (obj == null) {
            throw new NullPointerException(message);
        }
        return obj;
    }

    /**
     * backport of Objects.requireNonNull which is not available on Android yet.
     * @param obj object to NPE check
     * @param <T> obj type
     * @return returns obj itself if it's not null
     */
    public static <T> T requireNonNull(T obj) {
        if (obj == null) {
            throw new NullPointerException();
        }
        return obj;
    }
}
