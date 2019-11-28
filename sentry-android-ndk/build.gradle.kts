import com.novoda.gradle.release.PublishExtension

plugins {
    id("com.android.library")
    kotlin("android")
    jacoco
    id(Config.Deploy.novodaBintrayId)
}

android {
    compileSdkVersion(Config.Android.compileSdkVersion)
    buildToolsVersion(Config.Android.buildToolsVersion)

    defaultConfig {
        targetSdkVersion(Config.Android.targetSdkVersion)
        minSdkVersion(Config.Android.minSdkVersionNdk) // NDK requires a higher API level than core.

        javaCompileOptions {
            annotationProcessorOptions {
                includeCompileClasspath = true
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        versionName = project.version.toString()
        versionCode = Config.Sentry.buildVersionCode

        externalNativeBuild {
            val sentryNativeSrc = if (File("${project.projectDir}/sentry-native-local").exists()) {
                "sentry-native-local"
            } else {
                "sentry-native"
            }
            cmake {
                arguments.add(0, "-DANDROID_STL=c++_static")
                arguments.add(0, "-DCMAKE_VERBOSE_MAKEFILE:BOOL=ON")
                arguments.add(0, "-DSENTRY_NATIVE_SRC=$sentryNativeSrc")
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

        // replace with https://issuetracker.google.com/issues/72050365 once released.
        libraryVariants.all {
            generateBuildConfigProvider?.configure {
                enabled = false
            }
        }
    }

    externalNativeBuild {
        cmake {
            setPath("CMakeLists.txt")
        }
    }

    buildTypes {
        getByName("debug")
        getByName("release") {
            consumerProguardFiles("proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }

    testOptions {
        animationsDisabled = true
        unitTests.apply {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
            all(KotlinClosure1<Any, Test>({
                (this as Test).also { testTask ->
                    testTask.extensions
                        .getByType(JacocoTaskExtension::class.java)
                        .isIncludeNoLocationClasses = true
                }
            }, this))
        }
    }

    lintOptions {
        isWarningsAsErrors = true
        isCheckDependencies = true

        // We run a full lint analysis as build part in CI, so skip vital checks for assemble tasks.
        isCheckReleaseBuilds = false
    }

    ndkVersion = "20.1.5948944"
}

dependencies {
    api(project(":sentry-core"))
    api(project(":sentry-android-core"))

    compileOnly(Config.CompileOnly.jetbrainsAnnotations)
}

val initNative = tasks.register<Exec>("initNative") {
    logger.log(LogLevel.LIFECYCLE, "Initializing git submodules")
    commandLine("git", "submodule", "update", "--init", "--recursive")
}

tasks.named("preBuild") {
    dependsOn(initNative)
}

//TODO: move thse blocks to parent gradle file, DRY
configure<PublishExtension> {
    userOrg = Config.Sentry.userOrg
    groupId = project.group.toString()
    publishVersion = project.version.toString()
    desc = Config.Sentry.description
    website = Config.Sentry.website
    repoName = Config.Sentry.repoName
    setLicences(Config.Sentry.licence)
    issueTracker = Config.Sentry.issueTracker
    repository = Config.Sentry.repository
    dryRun = Config.Deploy.dryRun
    override = Config.Deploy.override
    sign = Config.Deploy.sign
    artifactId = "sentry-android-ndk"
}
