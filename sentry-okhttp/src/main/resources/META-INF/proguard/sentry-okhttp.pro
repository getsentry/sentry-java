##---------------Begin: proguard configuration for OkHttp  ----------

# To ensure that stack traces is unambiguous
# https://developer.android.com/studio/build/shrink-code#decode-stack-trace
-keepattributes LineNumberTable,SourceFile

# https://square.github.io/okhttp/features/r8_proguard/
# If you use OkHttp as a dependency in an Android project which uses R8 as a default compiler you
# don’t have to do anything. The specific rules are already bundled into the JAR which can
# be interpreted by R8 automatically.
# https://raw.githubusercontent.com/square/okhttp/master/okhttp/src/jvmMain/resources/META-INF/proguard/okhttp3.pro

# Keep the class names of the Sentry OkHttp integration so stack traces stay unambiguous.
# sentry-okhttp is a plain JVM jar, but R8/AGP still apply rules shipped under
# META-INF/proguard, so these travel with the module regardless of sentry-android-core.
-keepnames class io.sentry.okhttp.SentryOkHttpInterceptor
-keepnames class io.sentry.okhttp.SentryOkHttpEventListener

##---------------End: proguard configuration for OkHttp  ----------
