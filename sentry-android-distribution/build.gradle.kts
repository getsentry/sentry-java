import org.jetbrains.kotlin.config.KotlinCompilerVersion
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
  implementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
}
