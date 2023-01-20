##---------------Begin: proguard configuration for Compose  ----------

# The Android SDK checks at runtime if these classes are available via Class.forName
-keep class io.sentry.compose.gestures.ComposeGestureTargetLocator { <init>(...); }
-keepnames interface androidx.compose.ui.node.Owner

# To ensure that stack traces is unambiguous
# https://developer.android.com/studio/build/shrink-code#decode-stack-trace
-keepattributes LineNumberTable,SourceFile

##---------------End: proguard configuration for Compose  ----------
