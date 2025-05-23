import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
    id("com.android.library")
    kotlin("android")
    jacoco
    alias(libs.plugins.jacoco.android)
    alias(libs.plugins.gradle.versions)
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()
    namespace = "io.sentry.android.ndk"

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(Config.Android.abiFilters)
        }

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

    // needed because of Kotlin 1.4.x
    configurations.all {
        resolutionStrategy.force(Config.CompileOnly.jetbrainsAnnotations)
    }

    buildFeatures {
        buildConfig = true
    }

    androidComponents.beforeVariants {
        it.enable = !Config.Android.shouldSkipDebugVariant(it.buildType)
    }

    // the default AGP version is too high, we keep this low for compatibility reasons
    // see https://developer.android.com/build/releases/past-releases/ to find the AGP-NDK mapping
    ndkVersion = "23.1.7779620"

    @Suppress("UnstableApiUsage")
    packagingOptions {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    api(projects.sentry)
    api(projects.sentryAndroidCore)

    implementation(Config.Libs.sentryNativeNdk)

    compileOnly(Config.CompileOnly.jetbrainsAnnotations)

    testImplementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(projects.sentryTestSupport)
}
