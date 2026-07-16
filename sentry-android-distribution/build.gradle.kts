import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  id("com.android.library")
  alias(libs.plugins.kotlin.android)
}

android {
  compileSdk = libs.versions.compileSdk.get().toInt()
  namespace = "io.sentry.android.distribution"

  defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
  buildFeatures { buildConfig = false }

  // AGP 9 only generates unit tests for the testBuildType. CI disables the debug
  // variant, so unit tests must target release to run at all.
  testBuildType = "release"

  testOptions {
    unitTests.apply {
      isReturnDefaultValues = true
      isIncludeAndroidResources = true
    }
  }
}

kotlin {
  compilerOptions.jvmTarget = JVM_1_8
  compilerOptions.languageVersion = KotlinVersion.KOTLIN_1_9
  explicitApi()
}

androidComponents.beforeVariants {
  it.enable = !Config.Android.shouldSkipDebugVariant(it.buildType)
}

dependencies {
  implementation(projects.sentry)
  implementation(
    libs.jetbrains.annotations
  ) // Use implementation instead of compileOnly to override kotlin stdlib's version
  implementation(kotlin(Config.kotlinStdLib, Config.kotlinStdLibVersionAndroid))
  testImplementation(libs.androidx.test.ext.junit)
  testImplementation(libs.roboelectric)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.androidx.test.core)
}
