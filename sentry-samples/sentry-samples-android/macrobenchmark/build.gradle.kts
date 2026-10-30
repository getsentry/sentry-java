plugins {
  id("com.android.test")
  id("io.sentry.spotless")
  id("io.sentry.jdk-target")
  alias(libs.plugins.kotlin.android)
}

android {
  namespace = "io.sentry.uitest.android.macrobenchmark"
  compileSdk = libs.versions.compileSdk.get().toInt()
  compileSdkMinor = libs.versions.compileSdkMinor.get().toInt()

  defaultConfig {
    // Macrobenchmark requires API 23+.
    minSdk = 24
    targetSdk = libs.versions.targetSdk.get().toInt()
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    // Pairs with the app's release build via matchingFallbacks. The test APK itself must be
    // debuggable (to instrument) and signed (to install); only the target app needs to be
    // genuinely release-like.
    create("benchmark") {
      isDebuggable = true
      signingConfig = signingConfigs.getByName("debug")
      matchingFallbacks += listOf("release")
    }
  }

  // The sample app is the root project of this build.
  targetProjectPath = ":"
  // Run the test in its own process so it measures the target app cold, not itself.
  experimentalProperties["android.experimental.self-instrumenting"] = true
}

// Benchmarks only make sense against the release build; drop the debug variant entirely.
androidComponents { beforeVariants(selector().withBuildType("debug")) { it.enable = false } }

dependencies {
  implementation(libs.androidx.test.ext.junit)
  implementation(libs.androidx.benchmark.macro.junit4)
}
