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
# don't warn about missing classes, as we are checking for their presence at runtime
-dontwarn io.sentry.android.timber.SentryTimberIntegration
-dontwarn io.sentry.android.fragment.FragmentLifecycleIntegration
-dontwarn io.sentry.compose.gestures.ComposeGestureTargetLocator
-dontwarn io.sentry.compose.viewhierarchy.ComposeViewHierarchyExporter

# To ensure that stack traces is unambiguous
# https://developer.android.com/studio/build/shrink-code#decode-stack-trace
-keepattributes LineNumberTable,SourceFile

# Keep Classnames for integrations
-keepnames class * implements io.sentry.Integration

-dontwarn io.sentry.apollo.SentryApolloInterceptor
-keepnames class io.sentry.apollo.SentryApolloInterceptor

-dontwarn io.sentry.apollo3.SentryApollo3HttpInterceptor
-keepnames class io.sentry.apollo3.SentryApollo3HttpInterceptor

-dontwarn io.sentry.android.okhttp.SentryOkHttpInterceptor
-keepnames class io.sentry.android.okhttp.SentryOkHttpInterceptor

-dontwarn io.sentry.android.navigation.SentryNavigationListener
-keepnames class io.sentry.android.navigation.SentryNavigationListener

-keepnames class io.sentry.android.core.ScreenshotEventProcessor
-keepnames class io.sentry.android.core.ViewHierarchyEventProcessor

# Keep any custom option classes like SentryAndroidOptions, as they're loaded via reflection
# Also keep method names, as they're e.g. used by native via JNI calls
-keep class * extends io.sentry.SentryOptions { *; }

-keepnames class io.sentry.android.core.ApplicationNotResponding

##---------------End: proguard configuration for android-core  ----------

##---------------Begin: proguard configuration for sentry-apollo-3  ----------

# don't warn about missing classes, as it depends on the sentry-apollo-3 jar dependency.
-dontwarn io.sentry.apollo3.SentryApollo3ClientException

# we don't want this class to be obfuscated, otherwise issue's titles are obfuscated as well.
-keep class io.sentry.apollo3.SentryApollo3ClientException { <init>(...); }

##---------------End: proguard configuration for sentry-apollo-3  ----------

##---------------Begin: proguard configuration for sentry-okhttp  ----------

# we don't want this class to be obfuscated, otherwise issue's titles are obfuscated as well.
-keepnames class io.sentry.exception.SentryHttpClientException

##---------------End: proguard configuration for sentry-okhttp  ----------

##---------------Begin: proguard configuration for sentry-android-replay  ----------
-dontwarn io.sentry.android.replay.ReplayIntegration
-dontwarn io.sentry.android.replay.DefaultReplayBreadcrumbConverter
-keepnames class io.sentry.android.replay.ReplayIntegration
##---------------End: proguard configuration for sentry-android-replay  ----------

##---------------Begin: proguard configuration for sentry-android-distribution  ----------
-dontwarn io.sentry.android.distribution.internal.DistributionIntegration
-keepnames class io.sentry.android.distribution.internal.DistributionIntegration
##---------------End: proguard configuration for sentry-android-distribution  ----------
