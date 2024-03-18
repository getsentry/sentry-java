plugins {
    id("com.android.application")
}

android {
    compileSdk = Config.Android.compileSdkVersion
    namespace = "io.sentry.java.tests.perf.appsentry"

    defaultConfig {
        applicationId = "io.sentry.java.tests.perf.appsentry"
        minSdk = Config.Android.minSdkVersionNdk
        targetSdk = Config.Android.targetSdkVersion
        versionCode = 1
        versionName = "1.0"
    }

    lint {
        warningsAsErrors = true
        checkDependencies = true

        // We run a full lint analysis as build part in CI, so skip vital checks for assemble tasks.
        checkReleaseBuilds = false
        disable.addAll(
            listOf(
                "FragmentTagUsage",
                "GradleDependency",
                "MonochromeLauncherIcon",
                "ContentDescription",
                "RtlHardcoded"
            )
        )
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug") // to be able to run release mode
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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

    androidComponents.beforeVariants {
        it.enable = !Config.Android.shouldSkipDebugVariant(it.buildType)
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.fragment:fragment:1.6.2")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.navigation:navigation-fragment:2.7.7")
    implementation("androidx.navigation:navigation-ui:2.7.7")
    implementation(projects.sentryAndroid)
}
