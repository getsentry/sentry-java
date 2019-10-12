plugins {
    id("com.android.library")
}

android {
    compileSdkVersion(Config.Android.compileSdkVersion)
    buildToolsVersion(Config.Android.buildToolsVersion)

    defaultConfig {
        minSdkVersion(Config.Android.minSdkVersion)

        missingDimensionStrategy(Config.Flavors.dimension, Config.Flavors.production)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    api(project(":sentry-android-core"))
    // TODO: Add NDK: api(project(":sentry-android-ndk"))
}
