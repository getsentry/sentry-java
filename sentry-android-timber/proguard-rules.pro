##---------------Begin: proguard configuration for Timber  ----------

# The Android SDK checks at runtime if these classes are available via Class.forName
-keepnames class io.sentry.android.timber.SentryTimberIntegration
-keepnames class timber.log.Timber

# To ensure that stack traces is unambiguous
# https://developer.android.com/studio/build/shrink-code#decode-stack-trace
-keepattributes LineNumberTable,SourceFile

##---------------End: proguard configuration for Timber  ----------
