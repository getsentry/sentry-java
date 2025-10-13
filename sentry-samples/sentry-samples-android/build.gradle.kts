import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.impl.VariantImpl
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.internal.extensions.stdlib.capitalized

plugins {
  id("com.android.application")
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
}

android {
  compileSdk = libs.versions.compileSdk.get().toInt()
  namespace = "io.sentry.samples.android"

  defaultConfig {
    applicationId = "io.sentry.samples.android"
    minSdk = libs.versions.minSdk.get().toInt()
    targetSdk = libs.versions.targetSdk.get().toInt()
    versionCode = 2
    versionName = project.version.toString()

    externalNativeBuild {
      cmake {
        // Android 15: As we're using an older version of AGP / NDK, the STL is not 16kb page
        // aligned yet
        // Our example code doesn't use the STL, so we simply disable it
        // See https://developer.android.com/guide/practices/page-sizes
        arguments.add(0, "-DANDROID_STL=none")
      }
    }

    ndk { abiFilters.addAll(Config.Android.abiFilters) }
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
        "HardcodedText",
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

  composeOptions { kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get() }

  dependenciesInfo {
    // Disables dependency metadata when building APKs.
    includeInApk = false
    // Disables dependency metadata when building Android App Bundles.
    includeInBundle = false
  }

  externalNativeBuild { cmake { path("CMakeLists.txt") } }

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
      addManifestPlaceholders(mapOf("sentryDebug" to true, "sentryEnvironment" to "debug"))
    }
    getByName("release") {
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("debug") // to be able to run release mode
      isShrinkResources = true

      addManifestPlaceholders(mapOf("sentryDebug" to false, "sentryEnvironment" to "release"))
    }
  }

  kotlin { compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8 }

  androidComponents.beforeVariants {
    it.enable = !Config.Android.shouldSkipDebugVariant(it.buildType)
  }

  androidComponents.onVariants { variant ->
    val taskName = "toggle${variant.name.capitalized()}NativeLogging"
    val toggleNativeLoggingTask =
      project.tasks.register<ToggleNativeLoggingTask>(taskName) {
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

  @Suppress("UnstableApiUsage") packagingOptions { jniLibs { useLegacyPackaging = true } }
}

dependencies {
  implementation(
    kotlin(Config.kotlinStdLib, org.jetbrains.kotlin.config.KotlinCompilerVersion.VERSION)
  )

  implementation(projects.sentryAndroid)
  implementation(projects.sentryAndroidFragment)
  implementation(projects.sentryAndroidTimber)
  implementation(projects.sentryCompose)
  implementation(projects.sentryKotlinExtensions)
  implementation(projects.sentryOkhttp)

  //    how to exclude androidx if release health feature is disabled
  //    implementation(projects.sentryAndroid) {
  //        exclude(group = "androidx.lifecycle", module = "lifecycle-process")
  //        exclude(group = "androidx.lifecycle", module = "lifecycle-common-java8")
  //        exclude(group = "androidx.core", module = "core")
  //    }

  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.foundation.layout)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.recyclerview)
  implementation(libs.coil.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.retrofit)
  implementation(libs.retrofit.gson)
  implementation(libs.sentry.native.ndk)
  implementation(libs.timber)
  implementation(libs.camerax.core)
  implementation(libs.camerax.camera2)
  implementation(libs.camerax.lifecycle)
  implementation(libs.camerax.view)

  debugImplementation(projects.sentryAndroidDistribution)
  debugImplementation(libs.leakcanary)
}

abstract class ToggleNativeLoggingTask : Exec() {
  @get:Input abstract val rootDir: Property<String>

  @get:InputFile abstract val mergedManifest: RegularFileProperty

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
    private val regex =
      Regex(
        """<meta-data\s+[^>]*android:name="io\.sentry\.session-replay\.debug"[^>]*android:value="([^"]+)""""
      )
  }
}
