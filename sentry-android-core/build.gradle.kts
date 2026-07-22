import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8

plugins {
  id("com.android.library")
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.errorprone)
  alias(libs.plugins.gradle.versions)
}

android {
  compileSdk = libs.versions.compileSdk.get().toInt()
  namespace = "io.sentry.android.core"

  defaultConfig {
    minSdk = libs.versions.minSdk.get().toInt()

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    buildConfigField(
      "String",
      "SENTRY_ANDROID_SDK_NAME",
      "\"${Config.Sentry.SENTRY_ANDROID_SDK_NAME}\"",
    )

    // for AGP 4.1
    buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
  }

  buildTypes {
    getByName("debug") { consumerProguardFiles("proguard-rules.pro") }
    getByName("release") { consumerProguardFiles("proguard-rules.pro") }
  }

  // AGP 9 only generates unit tests for the testBuildType. CI disables the debug
  // variant, so unit tests must target release to run at all.
  testBuildType = "release"

  kotlin { compilerOptions.jvmTarget = JVM_1_8 }

  testOptions {
    animationsDisabled = true
    unitTests.apply {
      isReturnDefaultValues = true
      isIncludeAndroidResources = true
      // Robolectric loads the android-all jar into each test JVM, which needs more heap
      // than the default.
      all {
        it.minHeapSize = "256m"
        it.maxHeapSize = "2g"
      }
    }
  }

  lint {
    warningsAsErrors = true
    checkDependencies = true

    // We run a full lint analysis as build part in CI, so skip vital checks for assemble tasks.
    checkReleaseBuilds = false
  }

  buildFeatures { buildConfig = true }

  // needed because of Kotlin 1.4.x
  configurations.all { resolutionStrategy.force(libs.jetbrains.annotations.get()) }

  androidComponents.beforeVariants {
    it.enable = !Config.Android.shouldSkipDebugVariant(it.buildType)
  }
}

tasks.withType<JavaCompile>().configureEach {
  options.errorprone {
    check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
    option("NullAway:AnnotatedPackages", "io.sentry")
  }
}

// Snapshot PNGs are written by ScreenshotEventProcessorTest at runtime but must be declared as
// outputs so Gradle's build cache restores them on cache hits (otherwise the CLI upload step
// finds an empty directory).
tasks
  .matching { it.name == "testDebugUnitTest" || it.name == "testReleaseUnitTest" }
  .configureEach { outputs.dir(layout.buildDirectory.dir("test-snapshots")) }

dependencies {
  api(projects.sentry)
  compileOnly(libs.jetbrains.annotations)
  compileOnly(libs.nopen.annotations)
  compileOnly(projects.sentryAndroidFragment)
  compileOnly(projects.sentryAndroidTimber)
  compileOnly(projects.sentryAndroidReplay)
  compileOnly(projects.sentryCompose)
  compileOnly(projects.sentryAndroidDistribution)

  // lifecycle processor, session tracking
  implementation(libs.androidx.lifecycle.common.java8)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.core)
  implementation(libs.epitaph)

  errorprone(libs.errorprone.core)
  errorprone(libs.nopen.checker)
  errorprone(libs.nullaway)

  // tests
  testImplementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
  testImplementation(libs.roboelectric)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.androidx.core.ktx)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.test.ext.junit)
  testImplementation(libs.androidx.test.runner)
  testImplementation(libs.awaitility.kotlin)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.mockito.inline)
  testImplementation(projects.sentryTestSupport)
  testImplementation(projects.sentrySpotlight)
  testImplementation(projects.sentryAndroidFragment)
  testImplementation(projects.sentryAndroidTimber)
  testImplementation(projects.sentryAndroidReplay)
  testImplementation(projects.sentryCompose)
  testImplementation(projects.sentryAndroidNdk)

  testImplementation(libs.androidx.activity.compose)
  testImplementation(libs.androidx.compose.ui)
  testImplementation(libs.androidx.compose.foundation)
  testImplementation(libs.androidx.compose.foundation.layout)
  testImplementation(libs.androidx.compose.material3)
  testRuntimeOnly(libs.androidx.fragment.ktx)
  testRuntimeOnly(libs.timber)
}
