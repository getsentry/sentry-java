rootProject.name = "sentry"
rootProject.buildFileName = "build.gradle.kts"

include("sentry-android",
        "sentry-android-ndk",
        "sentry-android-core",
        "sentry-core",
        "sentry-samples:sentry-samples-android",
        "sentry-samples:sentry-samples-console",
        "sentry-android-timber")
