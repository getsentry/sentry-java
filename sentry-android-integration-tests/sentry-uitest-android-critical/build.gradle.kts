import io.gitlab.arturbosch.detekt.Detekt

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdk = Config.Android.compileSdkVersion
    namespace = "io.sentry.uitest.android.critical"

    defaultConfig {
        applicationId = "io.sentry.uitest.android.critical"
        minSdk = 21
        targetSdk = Config.Android.targetSdkVersion
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = Config.androidComposeCompilerVersion
    }
}

dependencies {
    implementation(kotlin(Config.kotlinStdLib, org.jetbrains.kotlin.config.KotlinCompilerVersion.VERSION))
    implementation(Config.Libs.androidxCore)
    implementation(Config.Libs.composeActivity)
    implementation(Config.Libs.composeFoundation)
    implementation(Config.Libs.composeMaterial)
    implementation(Config.Libs.constraintLayout)
    implementation(projects.sentryAndroidCore)
}

tasks.withType<Detekt> {
    // Target version of the generated JVM bytecode. It is used for type resolution.
    jvmTarget = JavaVersion.VERSION_1_8.toString()
}

kotlin {
    explicitApi()
}
