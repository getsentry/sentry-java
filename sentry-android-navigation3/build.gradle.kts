import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  id("com.android.library")
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.gradle.versions)
  id("io.sentry.detekt")
  id("io.sentry.spotless")
  id("io.sentry.jdk-target")
  id("io.sentry.publish")
  id("io.sentry.api-validation")
}

android {
  compileSdk = libs.versions.compileSdk.get().toInt()
  namespace = "io.sentry.compose.navigation3"

  defaultConfig {
    minSdk = libs.versions.minSdk.get().toInt()

    buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
  }

  buildTypes {
    getByName("release") { consumerProguardFiles("proguard-rules.pro") }
  }

  // AGP 9 only generates unit tests for the testBuildType. The debug variant is
  // disabled, so unit tests must target release to run at all.
  testBuildType = "release"

  kotlin {
    compilerOptions.jvmTarget = JVM_1_8
    compilerOptions.languageVersion = KotlinVersion.KOTLIN_1_9
    compilerOptions.apiVersion = KotlinVersion.KOTLIN_1_9
  }

  testOptions {
    unitTests.isReturnDefaultValues = true
  }

  lint {
    warningsAsErrors = true
    checkDependencies = true

    // Lifecycle 2.8.3's lint checks are incompatible with AGP 9.5. Lifecycle 2.10.0 fixes them,
    // but raises minSdk to 23 while this SDK still supports 21.
    disable += "ObsoleteLintCustomCheck"

    // We run a full lint analysis as build part in CI, so skip vital checks for assemble tasks.
    checkReleaseBuilds = false
  }

  buildFeatures {
    buildConfig = true
    compose = true
  }

  androidComponents.beforeVariants {
    it.enable = !Config.Android.shouldSkipDebugVariant(it.buildType)
  }
}

kotlin { explicitApi() }

dependencies {
  implementation(projects.sentry)
  implementation(libs.androidx.lifecycle.runtime.compose)

  compileOnly(libs.androidx.compose.runtime)
  compileOnly(libs.androidx.compose.ui)

  testImplementation(projects.sentryAndroidCore)
  testImplementation(projects.sentryTestSupport)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.fragment.ktx)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.test.ext.junit)
  testImplementation(libs.androidx.viewpager2)
  testImplementation(libs.google.truth)
  testImplementation(libs.mockito.core)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.roboelectric)
}

tasks.withType<Detekt>().configureEach {
  // Target version of the generated JVM bytecode. It is used for type resolution.
  jvmTarget = JavaVersion.VERSION_1_8.toString()
}
