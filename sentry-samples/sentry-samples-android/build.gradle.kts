plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdk = Config.Android.compileSdkVersion
    namespace = "io.sentry.samples.android"

    defaultConfig {
        applicationId = "io.sentry.samples.android"
        minSdk = Config.Android.minSdkVersionCompose
        targetSdk = Config.Android.targetSdkVersion
        versionCode = 2
        versionName = project.version.toString()

        externalNativeBuild {
            val sentryNativeSrc = if (File("${project.projectDir}/../../sentry-android-ndk/sentry-native-local").exists()) {
                "sentry-native-local"
            } else {
                "sentry-native"
            }
            println("sentry-samples-android: $sentryNativeSrc")

            cmake {
                arguments.add(0, "-DANDROID_STL=c++_static")
                arguments.add(0, "-DSENTRY_NATIVE_SRC=$sentryNativeSrc")
            }
        }

        ndk {
            abiFilters.addAll(Config.Android.abiFilters)
        }
    }

    buildFeatures {
        // Determines whether to support View Binding.
        // Note that the viewBinding.enabled property is now deprecated.
        viewBinding = true
        compose = true
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

    variantFilter {
        if (Config.Android.shouldSkipDebugVariant(buildType.name)) {
            ignore = true
        }
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
    implementation(projects.sentryAndroidOkhttp)
    implementation(projects.sentryAndroidFragment)
    implementation(projects.sentryAndroidTimber)
    implementation(projects.sentryCompose)
    implementation(projects.sentryComposeHelper)
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

//    debugImplementation(Config.Libs.leakCanary)
}
