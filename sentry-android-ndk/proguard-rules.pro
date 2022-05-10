##---------------Begin: proguard configuration for NDK  ----------

# The Android SDK checks at runtime if this class is available via Class.forName
-keep class io.sentry.android.ndk.SentryNdk { *; }

# For native methods, see http://proguard.sourceforge.net/manual/examples.html#native
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# don't warn jetbrains annotations
-dontwarn org.jetbrains.annotations.**

# To ensure that stack traces is unambiguous
# https://developer.android.com/studio/build/shrink-code#decode-stack-trace
-keepattributes LineNumberTable,SourceFile
-renamesourcefileattribute SourceFile

##---------------End: proguard configuration for NDK  ----------
