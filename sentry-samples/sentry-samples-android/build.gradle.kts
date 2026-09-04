import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.impl.VariantImpl
import io.sentry.android.gradle.extensions.InstrumentationFeature
import io.sentry.android.gradle.extensions.SentryPluginExtension
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.internal.extensions.stdlib.capitalized

plugins {
  alias(libs.plugins.android.application)
  id("io.sentry.spotless")
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.ksp)
  alias(libs.plugins.sentry) apply false
  alias(libs.plugins.sqldelight)
}

// The SDK version lives in the SDK build's gradle.properties, which this build cannot read as a
// Gradle property. It doubles as the sample's versionName, so an APK says which SDK it was built
// against.
val sentryVersion: String =
  providers
    .fileContents(layout.projectDirectory.dir("../..").file("gradle.properties"))
    .asText
    .map { properties ->
      val match = Regex("""^versionName=(.+)$""", RegexOption.MULTILINE).find(properties)
      checkNotNull(match) { "versionName is missing from the SDK build's gradle.properties" }
        .groupValues[1]
        .trim()
    }
    .get()

version = sentryVersion

if (providers.gradleProperty("useSagp").isPresent) {
  apply(plugin = "io.sentry.android.gradle")
}

plugins.withId("io.sentry.android.gradle") {
  // Extension configs match non-SAGP builds. Update locally to test your feature.
  extensions.configure<SentryPluginExtension>("sentry") {
    autoInstallation.enabled.set(false)
    includeProguardMapping.set(false)
    includeDependenciesReport.set(false)
    telemetry.set(false)
    tracingInstrumentation {
      features.set(
        setOf(
          // FILE_IO is disabled for non-SAGP builds.
          InstrumentationFeature.COMPOSE,
          InstrumentationFeature.DATABASE,
          InstrumentationFeature.OKHTTP,
        )
      )
      logcat.enabled.set(false)
      appStart.enabled.set(false)
    }
  }
}

android {
  compileSdk = libs.versions.compileSdk.get().toInt()
  namespace = "io.sentry.samples.android"

  defaultConfig {
    applicationId = "io.sentry.samples.android"
    // androidx.sqlite 2.6+ require minSdk 23; the Sentry SDK still supports 21.
    minSdk = 23
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

    ndk { abiFilters.addAll(listOf("x86", "armeabi-v7a", "x86_64", "arm64-v8a")) }
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
      storeFile = rootProject.file("../../debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    getByName("debug") {
      // Suffix the id so debug and release builds can be installed side by side.
      applicationIdSuffix = ".debug"
      addManifestPlaceholders(mapOf("sentryDebug" to true, "sentryEnvironment" to "debug"))
      // The SDK modules only publish a release variant, so fall back to it for the
      // debug build of the sample.
      matchingFallbacks += "release"
    }
    getByName("release") {
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("debug") // to be able to run release mode
      isShrinkResources = true

      addManifestPlaceholders(mapOf("sentryDebug" to false, "sentryEnvironment" to "release"))
    }
  }

  // Java 11 b/c androidx.room3 requires it.
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  kotlin { compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11 }

  androidComponents.onVariants { variant ->
    variant.buildConfigFields?.put(
      "USE_SAGP",
      providers.provider {
        BuildConfigField(
          type = "boolean",
          value = providers.gradleProperty("useSagp").isPresent.toString(),
          comment = "Whether the Sentry Android Gradle Plugin was applied",
        )
      },
    )

    val taskName = "toggle${variant.name.capitalized()}NativeLogging"
    val toggleNativeLoggingTask =
      project.tasks.register<ToggleNativeLoggingTask>(taskName) {
        mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
        repoDir.set(project.rootDir.resolve("../..").canonicalPath)
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

// The SDK build drives formatting repository-wide and references one task per included build, so
// this build's root spotlessApply has to cover its subprojects too.
tasks.named("spotlessApply") { dependsOn(subprojects.map { "${it.path}:spotlessApply" }) }

sqldelight {
  databases {
    create("SampleSQLDelightDatabase") {
      packageName.set("io.sentry.samples.android.sqlite")
      // Keep .sq files next to the hand-written Kotlin (src/main/java/.../sqlite) instead of the
      // default src/main/sqldelight source root.
      srcDirs("src/main/java")
    }
  }
}

dependencies {
  implementation(kotlin("stdlib-jdk8"))

  implementation("io.sentry:sentry-android:$sentryVersion")
  implementation("io.sentry:sentry-android-fragment:$sentryVersion")
  implementation("io.sentry:sentry-android-navigation:$sentryVersion")
  implementation("io.sentry:sentry-android-sqlite:$sentryVersion")
  implementation("io.sentry:sentry-android-timber:$sentryVersion")
  implementation("io.sentry:sentry-compose:$sentryVersion")
  implementation("io.sentry:sentry-kotlin-extensions:$sentryVersion")
  implementation("io.sentry:sentry-okhttp:$sentryVersion")
  implementation("io.sentry:sentry-spotlight:$sentryVersion")

  //    how to exclude androidx if release health feature is disabled
  //    implementation("io.sentry:sentry-android:$sentryVersion") {
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
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.navigation.fragment)
  implementation(libs.androidx.recyclerview)
  implementation(libs.androidx.browser)
  implementation(libs.androidx.room3.runtime)
  implementation(libs.bundles.androidx.room2)
  implementation(libs.bundles.androidx.sqlite.drivers)
  implementation(libs.camerax.camera2)
  implementation(libs.camerax.core)
  implementation(libs.camerax.lifecycle)
  implementation(libs.camerax.view)
  implementation(libs.coil.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.lottie.compose)
  implementation(libs.retrofit)
  implementation(libs.retrofit.gson)
  implementation(libs.sentry.native.ndk)
  implementation(libs.sqldelight.android.driver)
  implementation(libs.timber)

  ksp(libs.androidx.room.compiler)
  ksp(libs.androidx.room3.compiler)

  debugImplementation("io.sentry:sentry-android-distribution:$sentryVersion")
  debugImplementation(libs.leakcanary)
}

abstract class ToggleNativeLoggingTask : Exec() {
  @get:Input abstract val repoDir: Property<String>

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
        args.add("${repoDir.get()}/scripts/toggle-codec-logs.sh")
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
