##---------------Begin: proguard configuration for Timber  ----------

-keep class io.sentry.android.timber.** { *; }

# To ensure that stack traces is unambiguous
# https://developer.android.com/studio/build/shrink-code#decode-stack-trace
-keepattributes LineNumberTable,SourceFile

##---------------End: proguard configuration for Timber  ----------
