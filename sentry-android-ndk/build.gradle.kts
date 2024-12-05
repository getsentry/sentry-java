import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
    id("com.android.library")
    kotlin("android")
    jacoco
    id(Config.QualityPlugins.jacocoAndroid)
    id(Config.NativePlugins.nativeBundleExport)
    id(Config.QualityPlugins.gradleVersions)
}

var sentryNativeSrc: String = "sentry-native"
val sentryAndroidSdkName: String by project

android {
    compileSdk = Config.Android.compileSdkVersion
    namespace = "io.sentry.android.ndk"

    sentryNativeSrc = if (File("${project.projectDir}/sentry-native-local").exists()) {
        "sentry-native-local"
    } else {
        "sentry-native"
    }
    println("sentry-android-ndk: $sentryNativeSrc")

    defaultConfig {
        targetSdk = Config.Android.targetSdkVersion
        minSdk = Config.Android.minSdkVersionNdk // NDK requires a higher API level than core.

        testInstrumentationRunner = Config.TestLibs.androidJUnitRunner

        externalNativeBuild {
            cmake {
                arguments.add(0, "-DANDROID_STL=c++_static")
                arguments.add(0, "-DSENTRY_NATIVE_SRC=$sentryNativeSrc")
                arguments.add(0, "-DSENTRY_SDK_NAME=$sentryAndroidSdkName")
            }
        }

        ndk {
            abiFilters.addAll(Config.Android.abiFilters)
        }

        // for AGP 4.1
        buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
    }

    // we use the default NDK and CMake versions based on the AGP's version
    // https://developer.android.com/studio/projects/install-ndk#apply-specific-version

    externalNativeBuild {
        cmake {
            path("CMakeLists.txt")
        }
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

    nativeBundleExport {
        headerDir = "${project.projectDir}/$sentryNativeSrc/include"
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

    compileOnly(Config.CompileOnly.jetbrainsAnnotations)

    testImplementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
    testImplementation(Config.TestLibs.kotlinTestJunit)
    testImplementation(Config.TestLibs.mockitoKotlin)
    testImplementation(projects.sentryTestSupport)
}
