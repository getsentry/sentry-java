import io.gitlab.arturbosch.detekt.Detekt

plugins {
    id("com.android.library")
    kotlin("android")
    jacoco
    alias(libs.plugins.jacoco.android)
    alias(libs.plugins.gradle.versions)
    alias(libs.plugins.detekt)
}

android {
    compileSdk = Config.Android.compileSdkVersion
    namespace = "io.sentry.android.navigation"

    defaultConfig {
        minSdk = Config.Android.minSdkVersion

        // for AGP 4.1
        buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
    }

    buildTypes {
        getByName("debug") {
            consumerProguardFiles("proguard-rules.pro")
        }
        getByName("release") {
            consumerProguardFiles("proguard-rules.pro")
        }
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
        kotlinOptions.languageVersion = Config.kotlinCompatibleLanguageVersion
    }

    testOptions {
        animationsDisabled = true
        unitTests.apply {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }

    lint {
        warningsAsErrors = true
        checkDependencies = true

        // We run a full lint analysis as build part in CI, so skip vital checks for assemble tasks.
        checkReleaseBuilds = false
    }

    buildFeatures {
        buildConfig = true
    }

    androidComponents.beforeVariants {
        it.enable = !Config.Android.shouldSkipDebugVariant(it.buildType)
    }
}

kotlin {
    explicitApi()
}

dependencies {
    api(projects.sentry)

    compileOnly(Config.Libs.navigationRuntime)

    // tests
    testImplementation(Config.Libs.navigationRuntime)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockito.inline)

    testImplementation(libs.roboelectric)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.core.ktx)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.runner)
}

tasks.withType<Detekt>().configureEach {
    // Target version of the generated JVM bytecode. It is used for type resolution.
    jvmTarget = JavaVersion.VERSION_1_8.toString()
}
