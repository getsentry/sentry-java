import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
  id("com.android.library")
  alias(libs.plugins.kotlin.android)
}

android {
  compileSdk = libs.versions.compileSdk.get().toInt()
  namespace = "io.sentry.android.distribution"

  defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
  buildFeatures { buildConfig = false }
}

kotlin {
  jvmToolchain(17)
  explicitApi()
}

androidComponents.beforeVariants {
  it.enable = !Config.Android.shouldSkipDebugVariant(it.buildType)
}

dependencies {
  implementation(projects.sentry)
  implementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
}
