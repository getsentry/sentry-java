plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    compileSdk = Config.Android.compileSdkVersion

    defaultConfig {
        minSdk = Config.Android.minSdkVersionOkHttp
        targetSdk = Config.Android.targetSdkVersion

        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
        // Runs each test in its own instance of Instrumentation. This way they are isolated from
        // one another and get their own Application instance.
        // https://developer.android.com/training/testing/instrumented-tests/androidx-test-libraries/runner#enable-gradle
        // This doesn't work on some devices with Android 11+. Clearing package data resets permissions.
        // Check the readme for more info.
//        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
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
        named("debug") {
            // Since debuggable can"t be modified by gradle for library modules,
            // it must be done in a manifest - see src/androidTest/AndroidManifest.xml
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "sentry-uitest-android-benchmark-proguard-rules.pro")
        }
        named("release") {
            isMinifyEnabled = true
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug") // to be able to run release mode
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "sentry-uitest-android-benchmark-proguard-rules.pro")
        }
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation(kotlin(Config.kotlinStdLib, org.jetbrains.kotlin.config.KotlinCompilerVersion.VERSION))

    implementation(projects.sentryAndroid)
    implementation(projects.sentryUitest.sentryUitestAndroid)
    implementation(Config.Libs.appCompat)
    implementation(Config.Libs.androidxCore)
    implementation(Config.Libs.androidxRecylerView)
    implementation(Config.Libs.constraintLayout)
    implementation(Config.TestLibs.espressoIdlingResource)

    androidTestImplementation(Config.TestLibs.kotlinTestJunit)
    androidTestImplementation(Config.TestLibs.androidxBenchmarkJunit)
    androidTestImplementation(Config.TestLibs.espressoCore)
    androidTestImplementation(Config.TestLibs.androidxTestCoreKtx)
    androidTestImplementation(Config.TestLibs.androidxRunner)
    androidTestImplementation(Config.TestLibs.androidxTestRules)
    androidTestImplementation(Config.TestLibs.androidxJunit)
    androidTestUtil(Config.TestLibs.androidxTestOrchestrator)
}
