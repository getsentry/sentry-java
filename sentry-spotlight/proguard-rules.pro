##---------------Begin: proguard configuration for sentry-spotlight  ----------

# The SDK checks at runtime if this class is available via Class.forName
-keep class io.sentry.spotlight.SpotlightIntegration { <init>(...); }

# To ensure that stack traces is unambiguous
# https://developer.android.com/studio/build/shrink-code#decode-stack-trace
-keepattributes LineNumberTable,SourceFile

##---------------End: proguard configuration for sentry-spotlight  ----------
