import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
    id("com.android.library")
    kotlin("android")
    jacoco
    id(Config.QualityPlugins.jacocoAndroid)
    id(Config.QualityPlugins.gradleVersions)
}

val sentryAndroidSdkName: String by project

android {
    compileSdk = Config.Android.compileSdkVersion
    namespace = "io.sentry.android.ndk"

    defaultConfig {
        targetSdk = Config.Android.targetSdkVersion
        minSdk = Config.Android.minSdkVersionNdk // NDK requires a higher API level than core.

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

    variantFilter {
        if (Config.Android.shouldSkipDebugVariant(buildType.name)) {
            ignore = true
        }
    }
}

dependencies {
    api(projects.sentry)
    api(projects.sentryAndroidCore)

    implementation("io.sentry:sentry-native-ndk:0.7.8")

    compileOnly(Config.CompileOnly.jetbrainsAnnotations)

    testImplementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
    testImplementation(Config.TestLibs.kotlinTestJunit)

    testImplementation(Config.TestLibs.mockitoKotlin)
}
