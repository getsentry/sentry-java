package io.sentry.android.gradle

class SentryPluginExtension {
    def boolean autoProguardConfig = true;
    def boolean autoUpload = true;
    def String manifestPath = null;
}
