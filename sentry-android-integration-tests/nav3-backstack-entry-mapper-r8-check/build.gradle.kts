// Checks BackStackEntryMapper.SerialName in R8-minified release builds. See README.md.
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
// -Pnav3CheckKotlinxVersion=<version> to test newer versions.
val kotlinxVersionOverride = providers.gradleProperty("nav3CheckKotlinxVersion").orNull
val kotlinxFloor = matrixLibs.versions.kotlinxSerialization.floor.get()
val kotlinxVersion = kotlinxVersionOverride ?: kotlinxFloor

// Whether the mapper can name a @Serializable class with a named companion object and no
// named-companion R8 rules of the app's own: "named", "unknown", or "either".
// - kotlinx.serialization's own R8 rules keep what its lookup needs from 1.10.0 on.
// - The floor's rules don't, in R8 full and compat mode alike (verified on a device), so the floor
//   must record /unknown. Asserting it means a change in that behavior shows up here.
// - Between the two, it depends on the version and R8 mode (e.g., 1.9.x's lookup succeeds in
//   compat mode only), so either outcome is accepted.
val namedCompanionOutcome =
  kotlinxVersion.split('.', '-').let { parts ->
    val major = parts[0].toInt()
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    when {
      major > 1 || (major == 1 && minor >= 10) -> "named"
      kotlinxVersion == kotlinxFloor -> "unknown"
      else -> "either"
    }
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
    buildConfigField("String", "NAMED_COMPANION_OUTCOME", "\"$namedCompanionOutcome\"")
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
  // Lets the check initialize Sentry with a logger that records the mapper's messages.
  implementation("io.sentry:sentry-android-core:$sentryVersion")
  // compileOnly in sentry-android-navigation3; always available for apps that use Nav3.
  implementation(libs.androidx.compose.runtime)

  // Strict, so that no other dependency can raise the version the check runs against.
  implementation(matrixLibs.kotlinxSerialization.core) { version { strictly(kotlinxVersion) } }
}
