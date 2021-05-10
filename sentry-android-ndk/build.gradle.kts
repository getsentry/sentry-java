import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
    id("com.android.library")
    kotlin("android")
    jacoco
    id(Config.NativePlugins.nativeBundleExport)
    id(Config.QualityPlugins.gradleVersions)
}

var sentryNativeSrc: String = "sentry-native"

android {
    compileSdkVersion(Config.Android.compileSdkVersion)

    sentryNativeSrc = if (File("${project.projectDir}/sentry-native-local").exists()) {
        "sentry-native-local"
    } else {
        "sentry-native"
    }
    println("sentry-android-ndk: $sentryNativeSrc")

    defaultConfig {
        targetSdkVersion(Config.Android.targetSdkVersion)
        minSdkVersion(Config.Android.minSdkVersionNdk) // NDK requires a higher API level than core.

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        versionName = project.version.toString()
        versionCode = project.properties[Config.Sentry.buildVersionCodeProp].toString().toInt()

        externalNativeBuild {
            cmake {
                arguments.add(0, "-DANDROID_STL=c++_static")
                arguments.add(0, "-DSENTRY_NATIVE_SRC=$sentryNativeSrc")
            }
        }

        ndk {
            abiFilters.addAll(Config.Android.abiFilters)
        }

        // for AGP 4.1
        buildConfigField("String", "VERSION_NAME", "\"$versionName\"")
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

    lintOptions {
        isWarningsAsErrors = true
        isCheckDependencies = true

        // We run a full lint analysis as build part in CI, so skip vital checks for assemble tasks.
        isCheckReleaseBuilds = false
    }

    nativeBundleExport {
        headerDir = "${project.projectDir}/$sentryNativeSrc/include"
    }

    // needed because of Kotlin 1.4.x
    configurations.all {
        resolutionStrategy.force(Config.CompileOnly.jetbrainsAnnotations)
    }
}

tasks.withType<Test> {
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = false
    }
}

dependencies {
    api(project(":sentry"))
    api(project(":sentry-android-core"))

    compileOnly(Config.CompileOnly.jetbrainsAnnotations)

    testImplementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
    testImplementation(Config.TestLibs.kotlinTestJunit)

    testImplementation(Config.TestLibs.mockitoKotlin)
}

val initNative = tasks.register<Exec>("initNative") {
    logger.log(LogLevel.LIFECYCLE, "Initializing git submodules")
    commandLine("git", "submodule", "update", "--init", "--recursive")
    outputs.dir("${project.projectDir}/$sentryNativeSrc")
}

tasks.named("preBuild") {
    dependsOn(initNative)
}
