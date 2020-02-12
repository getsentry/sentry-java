package io.sentry.android.gradle

class SentryPluginExtension {
    def boolean autoProguardConfig = true;
    def boolean autoUpload = true;
    def String manifestPath = null;

    /**
     * Disables or enables the automatic configuration of Native Symbols
     * for Sentry. This executes sentry-cli automatically so
     * you don't need to do it manually.
     * Default is disabled.
     */
    def boolean uploadNativeSymbols = false;

    /**
     * Includes or not the source code of native code for Sentry.
     * This executes sentry-cli with the --include-sources param. automatically so
     * you don't need to do it manually.
     * Default is disabled.
     */
    def boolean includeNativeSources = false;
}
