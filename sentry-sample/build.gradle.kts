import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
    id("com.android.application")
    kotlin("android")
//    id("io.sentry.android.gradle") how to add sentry gradle plugin
}

android {
    compileSdkVersion(Config.Android.compileSdkVersion)
    buildToolsVersion(Config.Android.buildToolsVersion)

    defaultConfig {
        applicationId = "io.sentry.sample"
        minSdkVersion(Config.Android.minSdkVersion)
        targetSdkVersion(Config.Android.targetSdkVersion)
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments = mapOf(
            "clearPackageData" to "true"
        )

        externalNativeBuild {
            cmake {
                arguments.add(0, "-DANDROID_STL=c++_static")
            }
        }

        ndk {
            val platform = System.getenv("ABI")
            if (platform == null || platform.toLowerCase() == "all") {
                abiFilters("x86", "armeabi-v7a", "x86_64", "arm64-v8a")
            } else {
                abiFilters(platform)
            }
        }
    }

    externalNativeBuild {
        cmake {
            setPath("CMakeLists.txt")
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
        getByName("debug")
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug") // to be able to run release mode
            isShrinkResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(project(":sentry-android"))

    implementation(Config.Libs.appCompat)

    // debugging purpose
    implementation(Config.Libs.timber)
    debugImplementation(Config.Libs.leakCanary)


    testImplementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
    testImplementation(Config.TestLibs.junit)
    androidTestImplementation(Config.TestLibs.espressoCore)
    androidTestImplementation(Config.TestLibs.androidxCore)
    androidTestImplementation(Config.TestLibs.androidxRunner)
    androidTestImplementation(Config.TestLibs.androidxJunit)
    androidTestUtil(Config.TestLibs.androidxOrchestrator)
}
