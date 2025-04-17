import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.impl.VariantImpl
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.internal.extensions.stdlib.capitalized

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdk = Config.Android.compileSdkVersion
    namespace = "io.sentry.samples.android"

    defaultConfig {
        applicationId = "io.sentry.samples.android"
        minSdk = Config.Android.minSdkVersion
        targetSdk = Config.Android.targetSdkVersion
        versionCode = 2
        versionName = project.version.toString()

        externalNativeBuild {
            cmake {
                // Android 15: As we're using an older version of AGP / NDK, the STL is not 16kb page aligned yet
                // Our example code doesn't use the STL, so we simply disable it
                // See https://developer.android.com/guide/practices/page-sizes
                arguments.add(0, "-DANDROID_STL=none")
            }
        }

        ndk {
            abiFilters.addAll(Config.Android.abiFilters)
        }
    }

    lint {
        disable.addAll(
            listOf(
                "Typos",
                "PluralsCandidate",
                "MonochromeLauncherIcon",
                "TextFields",
                "ContentDescription",
                "LabelFor",
                "HardcodedText"
            )
        )
    }

    buildFeatures {
        // Determines whether to support View Binding.
        // Note that the viewBinding.enabled property is now deprecated.
        viewBinding = true
        compose = true
        buildConfig = true
        prefab = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = Config.androidComposeCompilerVersion
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    externalNativeBuild {
        cmake {
            path("CMakeLists.txt")
        }
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
        getByName("debug") {
            addManifestPlaceholders(
                mapOf(
                    "sentryDebug" to true,
                    "sentryEnvironment" to "debug"
                )
            )
        }
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug") // to be able to run release mode
            isShrinkResources = true

            addManifestPlaceholders(
                mapOf(
                    "sentryDebug" to false,
                    "sentryEnvironment" to "release"
                )
            )
        }
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }

    androidComponents.beforeVariants {
        it.enable = !Config.Android.shouldSkipDebugVariant(it.buildType)
    }

    androidComponents.onVariants { variant ->
        val taskName = "toggle${variant.name.capitalized()}NativeLogging"
        val toggleNativeLoggingTask = project.tasks.register<ToggleNativeLoggingTask>(taskName) {
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            rootDir.set(project.rootDir.absolutePath)
        }
        project.afterEvaluate {
            (variant as? VariantImpl<*>)?.taskContainer?.assembleTask?.configure {
                finalizedBy(toggleNativeLoggingTask)
            }
            (variant as? VariantImpl<*>)?.taskContainer?.installTask?.configure {
                finalizedBy(toggleNativeLoggingTask)
            }
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
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(kotlin(Config.kotlinStdLib, org.jetbrains.kotlin.config.KotlinCompilerVersion.VERSION))

    implementation(projects.sentryAndroid)
    implementation(projects.sentryAndroidFragment)
    implementation(projects.sentryAndroidTimber)
    implementation(projects.sentryCompose)
    implementation(projects.sentryOkhttp)
    implementation(Config.Libs.fragment)
    implementation(Config.Libs.timber)

//    how to exclude androidx if release health feature is disabled
//    implementation(projects.sentryAndroid) {
//        exclude(group = "androidx.lifecycle", module = "lifecycle-process")
//        exclude(group = "androidx.lifecycle", module = "lifecycle-common-java8")
//        exclude(group = "androidx.core", module = "core")
//    }

    implementation(Config.Libs.appCompat)
    implementation(Config.Libs.androidxRecylerView)
    implementation(Config.Libs.retrofit2)
    implementation(Config.Libs.retrofit2Gson)

    implementation(Config.Libs.composeActivity)
    implementation(Config.Libs.composeFoundation)
    implementation(Config.Libs.composeFoundationLayout)
    implementation(Config.Libs.composeNavigation)
    implementation(Config.Libs.composeMaterial)
    implementation(Config.Libs.composeCoil)
    implementation(Config.Libs.sentryNativeNdk)

    implementation(projects.sentryKotlinExtensions)
    implementation(Config.Libs.coroutinesAndroid)

    debugImplementation(Config.Libs.leakCanary)
}

abstract class ToggleNativeLoggingTask : Exec() {

    @get:Input
    abstract val rootDir: Property<String>

    @get:InputFile
    abstract val mergedManifest: RegularFileProperty

    override fun exec() {
        isIgnoreExitValue = true
        val manifestFile = mergedManifest.get().asFile
        val manifestContent = manifestFile.readText()
        val match = regex.find(manifestContent)

        if (match != null) {
            val value = match.groupValues[1].toBooleanStrictOrNull()
            if (value != null) {
                val args = mutableListOf<String>()
                if (Os.isFamily(Os.FAMILY_WINDOWS)) {
                    args.add(0, "cmd")
                    args.add(1, "/c")
                }
                args.add("${rootDir.get()}/scripts/toggle-codec-logs.sh")
                args.add(if (value) "enable" else "disable")
                commandLine(args)
                super.exec()
            }
        }
    }

    companion object {
        private val regex = Regex(
            """<meta-data\s+[^>]*android:name="io\.sentry\.session-replay\.debug"[^>]*android:value="([^"]+)""""
        )
    }
}
