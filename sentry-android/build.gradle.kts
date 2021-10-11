plugins {
    id("com.android.library")
    kotlin("android")
    id(Config.QualityPlugins.gradleVersions)
}

android {
    compileSdk = Config.Android.compileSdkVersion

    defaultConfig {
        targetSdk = Config.Android.targetSdkVersion
        minSdk = Config.Android.minSdkVersionNdk
    }

    buildFeatures {
        // Determines whether to generate a BuildConfig class.
        buildConfig = false
    }

    variantFilter {
        if (Config.Android.shouldSkipDebugVariant(buildType.name)) {
            ignore = true
        }
    }
}

dependencies {
    api(projects.sentryAndroidCore)
    api(projects.sentryAndroidNdk)
}
