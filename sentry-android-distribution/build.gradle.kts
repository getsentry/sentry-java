import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

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
  compilerOptions.languageVersion = KotlinVersion.KOTLIN_1_9
  explicitApi()
}

androidComponents.beforeVariants {
  it.enable = !Config.Android.shouldSkipDebugVariant(it.buildType)
}

dependencies {
  implementation(projects.sentry)
  implementation(
    libs.jetbrains.annotations
  ) // Use implementation instead of compileOnly to override kotlin stdlib's version
  implementation(kotlin(Config.kotlinStdLib, Config.kotlinStdLibVersionAndroid))
  testImplementation(libs.androidx.test.ext.junit)
}
