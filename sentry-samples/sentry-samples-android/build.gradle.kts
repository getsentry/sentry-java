plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdk = Config.Android.compileSdkVersion
    namespace = "io.sentry.samples.android"

    defaultConfig {
        applicationId = "io.sentry.samples.android"
        minSdk = Config.Android.minSdkVersion
        targetSdk = Config.Android.targetSdkVersion
        versionCode = 2
        versionName = project.version.toString()

        externalNativeBuild {
            cmake {
                // Android 15: As we're using an older version of AGP / NDK, the STL is not 16kb page aligned yet
                // Our example code doesn't use the STL, so we simply disable it
                // See https://developer.android.com/guide/practices/page-sizes
                arguments.add(0, "-DANDROID_STL=none")
            }
        }

        ndk {
            abiFilters.addAll(Config.Android.abiFilters)
        }
    }

    lint {
        disable.addAll(
            listOf(
                "Typos",
                "PluralsCandidate",
                "MonochromeLauncherIcon",
                "TextFields",
                "ContentDescription",
                "LabelFor",
                "HardcodedText"
            )
        )
    }

    buildFeatures {
        // Determines whether to support View Binding.
        // Note that the viewBinding.enabled property is now deprecated.
        viewBinding = true
        compose = true
        buildConfig = true
        prefab = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = Config.androidComposeCompilerVersion
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    externalNativeBuild {
        cmake {
            path("CMakeLists.txt")
        }
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
                    "sentryDebug" to true,
                    "sentryEnvironment" to "debug"
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
                    "sentryDebug" to false,
                    "sentryEnvironment" to "release"
                )
            )
        }
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }

    androidComponents.beforeVariants {
        it.enable = !Config.Android.shouldSkipDebugVariant(it.buildType)
    }

    @Suppress("UnstableApiUsage")
    packagingOptions {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(kotlin(Config.kotlinStdLib, org.jetbrains.kotlin.config.KotlinCompilerVersion.VERSION))

    implementation(projects.sentryAndroid)
    implementation(projects.sentryAndroidFragment)
    implementation(projects.sentryAndroidTimber)
    implementation(projects.sentryCompose)
    implementation(projects.sentryOkhttp)
    implementation(Config.Libs.fragment)
    implementation(Config.Libs.timber)

//    how to exclude androidx if release health feature is disabled
//    implementation(projects.sentryAndroid) {
//        exclude(group = "androidx.lifecycle", module = "lifecycle-process")
//        exclude(group = "androidx.lifecycle", module = "lifecycle-common-java8")
//        exclude(group = "androidx.core", module = "core")
//    }

    implementation(Config.Libs.appCompat)
    implementation(Config.Libs.androidxRecylerView)
    implementation(Config.Libs.retrofit2)
    implementation(Config.Libs.retrofit2Gson)

    implementation(Config.Libs.composeActivity)
    implementation(Config.Libs.composeFoundation)
    implementation(Config.Libs.composeFoundationLayout)
    implementation(Config.Libs.composeNavigation)
    implementation(Config.Libs.composeMaterial)
    implementation(Config.Libs.composeCoil)
    implementation(Config.Libs.sentryNativeNdk)

    debugImplementation(Config.Libs.leakCanary)
}
