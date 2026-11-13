// Checks BackStackEntryMapper.SerialNames and SerialNamesAndArguments in R8-minified release
// builds. See README.md.
plugins {
  id("com.android.application")
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.serialization)
  id("io.sentry.spotless")
  id("io.sentry.jdk-target")
}

val sentryVersion = libs.versions.sentry.get()

android {
  namespace = "io.sentry.tests.nav3serialname"
  compileSdk = libs.versions.compileSdk.get().toInt()
  compileSdkMinor = libs.versions.compileSdkMinor.get().toInt()

  defaultConfig {
    applicationId = "io.sentry.tests.nav3serialname"
    minSdk = libs.versions.minSdk.get().toInt()
    targetSdk = libs.versions.targetSdk.get().toInt()
    versionCode = 1
    versionName = "1.0"
  }

  // kotlinx.serialization is optional for sentry-android-navigation3, so check apps with and
  // without it.
  flavorDimensions += "serialization"
  productFlavors {
    create("withSerialization") { dimension = "serialization" }
    create("withoutSerialization") { dimension = "serialization" }
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
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
      signingConfig = signingConfigs.getByName("debug")
    }
  }

  androidComponents.beforeVariants { it.enable = it.buildType == "release" }
}

dependencies {
  implementation("io.sentry:sentry-android-navigation3:$sentryVersion")
  // compileOnly in sentry-android-navigation3; Nav3 apps always have it.
  implementation(libs.androidx.compose.runtime)
  // Defaults to the minimum version sentry-android-navigation3 supports, whose R8 rules are the
  // most limited. Override with -Pnav3CheckKotlinxVersion=<version>.
  val kotlinxVersionOverride = providers.gradleProperty("nav3CheckKotlinxVersion").orNull
  "withSerializationImplementation"(libs.kotlinx.serialization.core.nav3) {
    kotlinxVersionOverride?.let { version { strictly(it) } }
  }
}
