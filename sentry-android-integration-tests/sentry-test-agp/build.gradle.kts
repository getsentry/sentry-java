plugins {
    id("com.android.application")
}

android {
    compileSdk = Config.Android.compileSdkVersion
    namespace = "io.sentry.test.agp"

    defaultConfig {
        applicationId = "io.sentry.test.agp"
        minSdk = Config.Android.minSdkVersionOkHttp
        targetSdk = Config.Android.targetSdkVersion
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug") // to be able to run release mode
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "benchmark-proguard-rules.pro"
            )
            ndk {
                abiFilters.clear()
                abiFilters.add("arm64-v8a")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        viewBinding = true
    }
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    variantFilter {
        if (Config.Android.shouldSkipDebugVariant(buildType.name)) {
            ignore = true
        }
    }
}
dependencies {
    // just a mix of different dependencies to test how our logic for checking classes at runtime
    // works with r8
    implementation(projects.sentryAndroid)
    implementation(projects.sentryAndroidOkhttp)
    implementation(projects.sentryAndroidFragment)
    implementation(projects.sentryAndroidTimber)

    implementation(Config.Libs.fragment)

    implementation(Config.Libs.retrofit2)
}
