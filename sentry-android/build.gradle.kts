plugins {
    id("com.android.library")
}

android {
    compileSdkVersion(Config.Android.compileSdkVersion)
    buildToolsVersion(Config.Android.buildToolsVersion)
}

dependencies {
    api(project(":sentry-core"))
    // TODO: Add NDK: api(project(":sentry-android-ndk"))
}
