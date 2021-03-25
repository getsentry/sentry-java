rootProject.name = "sentry-root"
rootProject.buildFileName = "build.gradle.kts"

include(
    "sentry",
    "sentry-android-core",
    "sentry-android-ndk",
    "sentry-android",
    "sentry-android-timber",
    "sentry-android-okhttp",
    "sentry-test-support",
    "sentry-log4j2",
    "sentry-logback",
    "sentry-jul",
    "sentry-servlet",
    "sentry-apache-http-client-5",
    "sentry-spring",
    "sentry-datasource-proxy",
    "sentry-p6spy",
    "sentry-spring-boot-starter",
    "sentry-samples:sentry-samples-android",
    "sentry-samples:sentry-samples-console",
    "sentry-samples:sentry-samples-jul",
    "sentry-samples:sentry-samples-log4j2",
    "sentry-samples:sentry-samples-logback",
    "sentry-samples:sentry-samples-servlet",
    "sentry-samples:sentry-samples-spring",
    "sentry-samples:sentry-samples-spring-boot")
