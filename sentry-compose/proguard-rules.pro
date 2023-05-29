##---------------Begin: proguard configuration for Compose  ----------

# The Android SDK checks at runtime if these classes are available via Class.forName
-keep class io.sentry.compose.gestures.ComposeGestureTargetLocator { <init>(...); }
-keep class io.sentry.compose.viewhierarchy.ComposeViewHierarchyExporter { <init>(...); }

-keepnames interface androidx.compose.ui.node.Owner
-keepclassmembers class androidx.compose.ui.node.LayoutNode {
    private androidx.compose.ui.node.LayoutNodeLayoutDelegate layoutDelegate;
}

# R8 will warn about missing classes if people don't have androidx.compose-navigation on their
# classpath, but this is fine, these classes are used in an internal class which is only used when
# someone is using withSentryObservableEffect extension function (which, in turn, cannot be used
# without having androidx.compose-navigation on the classpath)
-dontwarn androidx.navigation.NavController$OnDestinationChangedListener
-dontwarn androidx.navigation.NavController

# To ensure that stack traces is unambiguous
# https://developer.android.com/studio/build/shrink-code#decode-stack-trace
-keepattributes LineNumberTable,SourceFile

##---------------End: proguard configuration for Compose  ----------
