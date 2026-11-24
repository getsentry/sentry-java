// Checks BackStackEntryMapper.SerialName and SerialNameAndProperties in R8-minified
// release builds. See README.md.
plugins {
  id("com.android.application")
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.serialization)
  id("io.sentry.spotless")
  id("io.sentry.jdk-target")
}

val sentryVersion = libs.versions.sentry.get()

// Defaults to the min version sentry-android-navigation3 supports, whose R8 rules are the most
// limited. scripts/test-nav3-backstack-entry-mapper-r8.sh overrides it with
// -Pnav3CheckKotlinxVersion=<version> for the latest stable release and any newer release
// candidate.
val kotlinxVersionOverride = providers.gradleProperty("nav3CheckKotlinxVersion").orNull
val kotlinxVersion = kotlinxVersionOverride ?: matrixLibs.versions.kotlinxSerialization.floor.get()

// kotlinx.serialization's own R8 rules keep what its lookup of named companion objects needs from
// 1.10.0 on. With older versions, whether the lookup survives without the app's own rules depends
// on the version and R8 (e.g., 1.9.x's succeeds in R8 compat mode only), so either outcome is
// accepted.
val kotlinxKeepsNamedCompanions =
  kotlinxVersion.split(".").let { (major, minor) ->
    major.toInt() > 1 || (major.toInt() == 1 && minor.toInt() >= 10)
  }

android {
  namespace = "io.sentry.tests.nav3.r8"
  compileSdk = libs.versions.compileSdk.get().toInt()
  compileSdkMinor = libs.versions.compileSdkMinor.get().toInt()

  defaultConfig {
    applicationId = "io.sentry.tests.nav3.r8"
    minSdk = libs.versions.minSdk.get().toInt()
    targetSdk = libs.versions.targetSdk.get().toInt()
    versionCode = 1
    versionName = "1.0"
    buildConfigField(
      "boolean",
      "KOTLINX_KEEPS_NAMED_COMPANIONS",
      kotlinxKeepsNamedCompanions.toString(),
    )
  }

  signingConfigs {
    getByName("debug") {
      storeFile = file("../../debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
      signingConfig = signingConfigs.getByName("debug")
    }
  }

  buildFeatures { buildConfig = true }

  androidComponents.beforeVariants { it.enable = it.buildType == "release" }
}

dependencies {
  implementation("io.sentry:sentry-android-navigation3:$sentryVersion")
  // compileOnly in sentry-android-navigation3; always available for apps that use Nav3.
  implementation(libs.androidx.compose.runtime)

  implementation(matrixLibs.kotlinxSerialization.core) {
    version {
      require(kotlinxVersion)
      kotlinxVersionOverride?.let { strictly(it) }
    }
  }
}
