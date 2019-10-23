
plugins {
    id("com.android.library")
}

android {
    compileSdkVersion(Config.Android.compileSdkVersion)
    buildToolsVersion(Config.Android.buildToolsVersion)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    defaultConfig {
        targetSdkVersion(Config.Android.targetSdkVersion)
        javaCompileOptions {
            annotationProcessorOptions {
                includeCompileClasspath = true
            }
        }

        minSdkVersion(Config.Android.minSdkVersionNdk)
        externalNativeBuild {
            val sentryNativeSrc = if (File("${project.projectDir}/sentry-native-local").exists()) {
                "sentry-native-local"
            } else {
                "sentry-native"
            }
            cmake {
                arguments.add(0, "-DANDROID_STL=c++_static")
                arguments.add(0, "-DCMAKE_VERBOSE_MAKEFILE:BOOL=ON")
                arguments.add(0, "-DSENTRY_NATIVE_SRC=" + sentryNativeSrc)
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

        missingDimensionStrategy(Config.Flavors.dimension, Config.Flavors.production)
    }

    externalNativeBuild {
        cmake {
            setPath("CMakeLists.txt")
        }
    }
}

dependencies {
    api(project(":sentry-core"))
    api(project(":sentry-android-core"))
}

val initNative = tasks.register<Exec>("initNative") {
    logger.log(LogLevel.LIFECYCLE, "Initializing git submodules")
    commandLine("git", "submodule", "update", "--init", "--recursive")
}

tasks.named("preBuild") {
    dependsOn(initNative)
}
