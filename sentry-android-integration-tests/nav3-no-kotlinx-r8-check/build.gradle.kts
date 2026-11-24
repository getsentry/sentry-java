// Checks that an R8-minified app without kotlinx.serialization builds and runs with
// sentry-android-navigation3. See README.md.
plugins {
  id("com.android.application")
  alias(libs.plugins.kotlin.android)
  id("io.sentry.spotless")
  id("io.sentry.jdk-target")
}

val sentryVersion = libs.versions.sentry.get()

android {
  namespace = "io.sentry.tests.nav3.r8"
  compileSdk = libs.versions.compileSdk.get().toInt()
  compileSdkMinor = libs.versions.compileSdkMinor.get().toInt()

  defaultConfig {
    // Shares nav3-backstack-entry-mapper-r8-check's application ID, Activity, and log tag, so
    // scripts/test-nav3-backstack-entry-mapper-r8.sh runs both apps' APKs the same way.
    applicationId = "io.sentry.tests.nav3.r8"
    minSdk = libs.versions.minSdk.get().toInt()
    targetSdk = libs.versions.targetSdk.get().toInt()
    versionCode = 1
    versionName = "1.0"
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

// Nothing here should bring in kotlinx.serialization, but excluding it guarantees the check
// covers the case it exists for.
configurations.configureEach {
  exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-core")
  exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-core-jvm")
}

dependencies {
  implementation("io.sentry:sentry-android-navigation3:$sentryVersion")
  // Lets the check read the integrations the SDK reports, which sentry-android-navigation3 only
  // has as a runtime dependency.
  implementation("io.sentry:sentry:$sentryVersion")
  // compileOnly in sentry-android-navigation3. Apps that use it always use Compose.
  implementation(libs.androidx.compose.runtime)
}
