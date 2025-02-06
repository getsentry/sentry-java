# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-dontshrink

-dontwarn com.google.errorprone.**
-dontwarn com.sun.jna.**
-dontwarn edu.umd.cs.findbugs.**
-dontwarn java.lang.instrument.**
-dontwarn java.lang.management.**
-dontwarn okhttp3.Handshake$Companion
-dontwarn okhttp3.Headers$Companion
-dontwarn okhttp3.HttpUrl$Companion
-dontwarn okhttp3.Protocol$Companion
-dontwarn okhttp3.internal.**
-dontwarn okio.ByteString$Companion
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.opentest4j.AssertionFailedError
-dontwarn org.mockito.internal.**
-dontwarn org.jetbrains.annotations.**
-dontwarn io.sentry.android.replay.ReplayIntegration
-keep class curtains.** { *; }
