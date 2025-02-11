plugins {
    id("com.android.library")
    kotlin("android")
    id(Config.QualityPlugins.gradleVersions)
}

android {
    compileSdk = Config.Android.compileSdkVersion
    namespace = "io.sentry.android"

    defaultConfig {
        minSdk = Config.Android.minSdkVersion
    }

    buildFeatures {
        // Determines whether to generate a BuildConfig class.
        buildConfig = false
    }

    buildTypes {
        getByName("debug")
        getByName("release") {
            consumerProguardFiles("proguard-rules.pro")
        }
    }

    androidComponents.beforeVariants {
        it.enable = !Config.Android.shouldSkipDebugVariant(it.buildType)
    }
}

dependencies {
    api(projects.sentryAndroidCore)
    api(projects.sentryAndroidNdk)
    api(projects.sentryAndroidReplay)
}
