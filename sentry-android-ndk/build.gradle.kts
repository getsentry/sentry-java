import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
    id("com.android.library")
    kotlin("android")
    jacoco
    id(Config.QualityPlugins.jacocoAndroid)
    id(Config.QualityPlugins.gradleVersions)
}

android {
    compileSdk = Config.Android.compileSdkVersion
    namespace = "io.sentry.android.ndk"

    defaultConfig {
        minSdk = Config.Android.minSdkVersion

        testInstrumentationRunner = Config.TestLibs.androidJUnitRunner

        ndk {
            abiFilters.addAll(Config.Android.abiFilters)
        }

        // for AGP 4.1
        buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
    }

    buildTypes {
        getByName("debug")
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
    testImplementation(Config.TestLibs.kotlinTestJunit)
    testImplementation(Config.TestLibs.mockitoKotlin)
    testImplementation(projects.sentryTestSupport)
}
