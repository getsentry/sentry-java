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
  }

  buildTypes {
    getByName("debug") { consumerProguardFiles("proguard-rules.pro") }
    getByName("release") { consumerProguardFiles("proguard-rules.pro") }
  }

  kotlin {
    compilerOptions.jvmTarget = JVM_1_8
    compilerOptions.languageVersion = KotlinVersion.KOTLIN_1_9
    compilerOptions.apiVersion = KotlinVersion.KOTLIN_1_9
  }

  lint {
    warningsAsErrors = true
    checkDependencies = true

    // We run a full lint analysis as build part in CI, so skip vital checks for assemble tasks.
    checkReleaseBuilds = false
  }

  androidComponents.beforeVariants {
    it.enable = !Config.Android.shouldSkipDebugVariant(it.buildType)
  }
}

kotlin { explicitApi() }

tasks.withType<Detekt>().configureEach {
  // Target version of the generated JVM bytecode. It is used for type resolution.
  jvmTarget = JavaVersion.VERSION_1_8.toString()
}
