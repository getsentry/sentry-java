package io.sentry.android.gradle

class SentryPluginExtension {
    def boolean autoProguardConfig = true;
    def boolean autoUpload = true;
    def String manifestPath = null;
    def boolean includeNativeSources = false;
    def boolean uploadNativeSymbols = false;
}
