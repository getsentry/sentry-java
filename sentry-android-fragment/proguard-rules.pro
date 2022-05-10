##---------------Begin: proguard configuration for Fragment  ----------

# The Android SDK checks at runtime if this class is available via Class.forName
-keep class io.sentry.android.fragment.FragmentLifecycleIntegration { <init>(...); }

# To ensure that stack traces is unambiguous
# https://developer.android.com/studio/build/shrink-code#decode-stack-trace
-keepattributes LineNumberTable,SourceFile
-renamesourcefileattribute SourceFile

##---------------End: proguard configuration for Fragment  ----------
