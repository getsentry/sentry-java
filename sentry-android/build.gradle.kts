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

    lint {
        warningsAsErrors = true
        checkDependencies = true

        // We run a full lint analysis as build part in CI, so skip vital checks for assemble tasks.
        checkReleaseBuilds = false
        checkAllWarnings = true
        disable.addAll(listOf("TrulyRandom", "SyntheticAccessor"))
    }
}

dependencies {
    api(projects.sentryAndroidCore)
    api(projects.sentryAndroidNdk)
}
