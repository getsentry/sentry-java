plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdk = Config.Android.compileSdkVersion

    defaultConfig {
        applicationId = "io.sentry.samples.android.minsdk"
        minSdk = Config.Android.minSdkVersion
        targetSdk = Config.Android.targetSdkVersion
        versionCode = 2
        versionName = "1.1.0"
    }

    buildFeatures {
        // Determines whether to support View Binding.
        // Note that the viewBinding.enabled property is now deprecated.
        viewBinding = true
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            addManifestPlaceholders(
                mapOf(
                    "sentryDebug" to true
                )
            )
        }
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug") // to be able to run release mode
            isShrinkResources = true

            addManifestPlaceholders(
                mapOf(
                    "sentryDebug" to false
                )
            )
        }
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
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
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(projects.sentryAndroid)
    implementation(projects.sentryAndroidFragment)
    implementation(projects.sentryAndroidTimber)
    implementation(projects.sentryApollo)

    implementation(Config.Libs.appCompat)
}
