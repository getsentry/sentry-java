import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  id("com.android.library")
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.gradle.versions)
  alias(libs.plugins.detekt)
}

android {
  compileSdk = libs.versions.compileSdk.get().toInt()
  namespace = "io.sentry.compose.navigation3"

  defaultConfig {
    minSdk = 23 // Nav3 requires minSdk 23

    // for AGP 4.1
    buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
  }

  buildTypes {
    getByName("debug") { consumerProguardFiles("proguard-rules.pro") }
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

    // We run a full lint analysis as build part in CI, so skip vital checks for assemble tasks.
    checkReleaseBuilds = false
  }

  buildFeatures { buildConfig = true }

  androidComponents.beforeVariants {
    it.enable = !Config.Android.shouldSkipDebugVariant(it.buildType)
  }
}

kotlin { explicitApi() }

dependencies {
  implementation(projects.sentry)

  compileOnly(libs.androidx.compose.runtime)

  testImplementation(libs.androidx.compose.runtime)
  testImplementation(libs.google.truth)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.mockito.inline)
  testImplementation(libs.mockito.kotlin)
}

tasks.withType<Detekt>().configureEach {
  // Target version of the generated JVM bytecode. It is used for type resolution.
  jvmTarget = JavaVersion.VERSION_1_8.toString()
}
