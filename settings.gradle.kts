rootProject.name = "sentry-root"
rootProject.buildFileName = "build.gradle.kts"

include("sentry-android",
    "sentry-android-ndk",
    "sentry-android-core",
    "sentry",
    "sentry-test-support",
    "sentry-log4j2",
    "sentry-logback",
    "sentry-servlet",
    "sentry-spring",
    "sentry-spring-boot-starter",
    "sentry-android-timber",
    "sentry-samples:sentry-samples-android",
    "sentry-samples:sentry-samples-console",
    "sentry-samples:sentry-samples-log4j2",
    "sentry-samples:sentry-samples-logback",
    "sentry-samples:sentry-samples-servlet",
    "sentry-samples:sentry-samples-spring",
    "sentry-samples:sentry-samples-spring-boot")
