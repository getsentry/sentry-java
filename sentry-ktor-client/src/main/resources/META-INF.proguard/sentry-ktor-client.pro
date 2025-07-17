##---------------Begin: proguard configuration for Ktor Client  ----------

-keepclassmembers class io.ktor.client.engine.okhttp.OkHttpConfig {
    kotlin.jvm.functions.Function1 config;
}

-keepnames class io.sentry.sentry.okhttp.**

##---------------End: proguard configuration for Ktor Client  ----------
