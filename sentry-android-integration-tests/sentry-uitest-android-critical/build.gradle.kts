import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

plugins {
  id("com.android.application")
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  id("io.sentry.spotless")
  id("io.sentry.jdk-target")
}

val sentryVersion = libs.versions.sentry.get()

android {
  compileSdk = libs.versions.compileSdk.get().toInt()
  compileSdkMinor = libs.versions.compileSdkMinor.get().toInt()
  namespace = "io.sentry.uitest.android.critical"

  signingConfigs {
    getByName("debug") {
      // Debug config remains unchanged
    }
  }

  defaultConfig {
    applicationId = "io.sentry.uitest.android.critical"
    minSdk = libs.versions.minSdk.get().toInt()
    targetSdk = libs.versions.targetSdk.get().toInt()
    versionCode = 1
    versionName = "1.0"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("debug")
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  buildFeatures { compose = true }
  composeOptions { kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get() }
  androidComponents.beforeVariants {
    it.enable = it.buildType != "debug"
  }
}

dependencies {
  implementation(kotlin("stdlib-jdk8", getKotlinPluginVersion()))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.core)
  implementation("io.sentry:sentry-android-core:$sentryVersion")
}

tasks.withType<Detekt>().configureEach {
  // Target version of the generated JVM bytecode. It is used for type resolution.
  jvmTarget = JavaVersion.VERSION_1_8.toString()
}
