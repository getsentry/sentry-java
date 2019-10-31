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
        minSdkVersion(Config.Android.minSdkVersionNdk) // NDK requires a higher API level than core.
        targetSdkVersion(Config.Android.targetSdkVersion)
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments = mapOf(
            "clearPackageData" to "true"
        )
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
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    // due https://github.com/gradle/gradle/issues/11083
//    kotlinOptions {
//        jvmTarget = JavaVersion.VERSION_1_8.toString()
//    }
    withGroovyBuilder {
        "kotlinOptions" {
            setProperty("jvmTarget", JavaVersion.VERSION_1_8.toString())
        }
    }

    // if travis ci hangs again on this task, remove comments
//    tasks.all {
//        if (this.name == "signingConfigWriterDebugAndroidTest") {
//            this.enabled = false
//            println("${this.name} is SKIPPED")
//        }
//    }

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
