plugins {
    id("com.android.library")
}

dependencies {
    api(project(":sentry-core"))
}

android {
    compileSdkVersion(29)
    defaultConfig {
        minSdkVersion(14)
    }
}
