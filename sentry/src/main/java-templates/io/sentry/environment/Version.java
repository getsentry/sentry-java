package io.sentry.environment;

public final class Version {
    /**
     * This constant field is prone to subtle bugs caused by constant inlining, see
     * https://revapi.org/modules/revapi-java/index.html#constant_field_changed_value
     *
     * @deprecated use {@link #sdkVersion()} instead
     */
    @Deprecated
    public static final String SDK_VERSION = "${buildNumber}";

    private Version() {

    }

    /**
     * Returns the version of the Sentry SDK.
     */
    public static String sdkVersion() {
        return SDK_VERSION;
    }
}
