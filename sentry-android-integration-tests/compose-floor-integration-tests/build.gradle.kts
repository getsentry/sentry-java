import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  id("com.android.library")
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.detekt)
}

android {
  compileSdk = libs.versions.compileSdk.get().toInt()
  namespace = "io.sentry.compose.floortest"

  defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  // AGP 9 only generates unit tests for the testBuildType. The debug variant is
  // disabled, so unit tests must target release to run at all.
  testBuildType = "release"

  kotlin {
    compilerOptions.jvmTarget = JvmTarget.JVM_1_8
    compilerOptions.languageVersion = KotlinVersion.KOTLIN_1_9
    compilerOptions.apiVersion = KotlinVersion.KOTLIN_1_9
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
    checkReleaseBuilds = false
  }

  androidComponents.beforeVariants {
    it.enable = !Config.Android.shouldSkipDebugVariant(it.buildType)
  }
}

// This module exists purely to run sentry-compose against the oldest Compose we claim to support.
// Compose artifacts are inlined into consumer bytecode, so building sentry-compose against a newer
// Compose can emit references to internals that do not exist on the floor, which only surfaces as a
// NoSuchMethodError at runtime on a consumer's older Compose. Pinning here makes that a test
// failure.
// The pins must stay at the floor: raising them silently disables the check this module provides.
configurations.configureEach {
  resolutionStrategy {
    val floor = libs.versions.androidxCompose.get()
    eachDependency {
      when (requested.group) {
        "androidx.compose.material3" ->
          useVersion(libs.versions.androidxComposeMaterial3Floor.get())
        "androidx.compose.runtime",
        "androidx.compose.foundation",
        "androidx.compose.ui",
        "androidx.compose.animation" -> useVersion(floor)
      }
    }
  }
}

dependencies {
  implementation(projects.sentryCompose)

  testImplementation(libs.androidx.compose.material3.floor)
  testImplementation(libs.androidx.compose.ui.test.junit4.floor)
  testImplementation(libs.androidx.test.ext.junit)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.roboelectric)
}

tasks.withType<Detekt>().configureEach { jvmTarget = JavaVersion.VERSION_1_8.toString() }
