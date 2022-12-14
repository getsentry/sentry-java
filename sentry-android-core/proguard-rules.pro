##---------------Begin: proguard configuration for android-core  ----------

##---------------Begin: proguard configuration for androidx.core  ----------
-keep class androidx.core.view.GestureDetectorCompat { <init>(...); }
-keep class androidx.core.app.FrameMetricsAggregator { <init>(...); }
-keep interface androidx.core.view.ScrollingView { *; }
##---------------End: proguard configuration for androidx.core  ----------

##---------------Begin: proguard configuration for androidx.lifecycle  ----------
-keep interface androidx.lifecycle.DefaultLifecycleObserver { *; }
-keep class androidx.lifecycle.ProcessLifecycleOwner { <init>(...); }
##---------------End: proguard configuration for androidx.lifecycle  ----------

# To mitigate the issue on R8 site (https://issuetracker.google.com/issues/235733922)
# which comes through AGP 7.3.0-betaX and 7.4.0-alphaX
-keepclassmembers enum io.sentry.** { *; }

# To filter out io.sentry frames from stacktraces
-keeppackagenames io.sentry.**

# don't warn jetbrains annotations
-dontwarn org.jetbrains.annotations.**
# don't warn about missing classes (mainly for Guardsquare's proguard).
# We are checking for their presence at runtime
-dontwarn io.sentry.android.timber.SentryTimberIntegration
-dontwarn io.sentry.android.fragment.FragmentLifecycleIntegration

# To ensure that stack traces is unambiguous
# https://developer.android.com/studio/build/shrink-code#decode-stack-trace
-keepattributes LineNumberTable,SourceFile

##---------------End: proguard configuration for android-core  ----------
