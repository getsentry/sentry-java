// Checks BackStackEntryMapper.SerialName in R8-minified release builds. See README.md.
plugins {
  id("com.android.application")
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  id("io.sentry.spotless")
  id("io.sentry.jdk-target")
}

val sentryVersion = libs.versions.sentry.get()

// Defaults to the oldest supported version, which has the fewest R8 rules. The test script also
// runs with -Pnav3CheckKotlinx=latestStable.
val kotlinxVersion =
  when (val role = providers.gradleProperty("nav3CheckKotlinx").getOrElse("floor")) {
    "floor" -> matrixLibs.versions.kotlinxSerialization.floor.get()
    "latestStable" -> matrixLibs.versions.kotlinxSerialization.latestStable.get()
    else -> error("nav3CheckKotlinx must be floor or latestStable, but was $role.")
  }

// kotlinx.serialization 1.10.0+ supplies R8 rules for named companions, so naming must succeed
// without app keep rules. Older releases may return the name or /unknown, depending on R8.
val namedCompanionAlwaysNamed =
  kotlinxVersion.split('.', '-').let { parts ->
    val major = parts[0].toInt()
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    major > 1 || (major == 1 && minor >= 10)
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
    buildConfigField("boolean", "NAMED_COMPANION_ALWAYS_NAMED", "$namedCompanionAlwaysNamed")
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

  buildFeatures {
    buildConfig = true
    compose = true
  }

  androidComponents.beforeVariants { it.enable = it.buildType == "release" }
}

dependencies {
  implementation("io.sentry:sentry-android-navigation3:$sentryVersion")
  // Lets the check initialize Sentry with a logger that records the mapper's messages.
  implementation("io.sentry:sentry-android-core:$sentryVersion")
  // compileOnly in sentry-android-navigation3; always available for apps that use Nav3.
  implementation(libs.androidx.compose.runtime)
  // Hosts SentryNavEffect, so the check reads names and reasons from what the SDK records. Compose
  // UI 1.7+ provides the LocalLifecycleOwner the effect reads, as it does in every Nav3 app.
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.ui)

  // Strict, so that no other dependency can raise the version the check runs against.
  implementation(matrixLibs.kotlinxSerialization.core) { version { strictly(kotlinxVersion) } }
}
