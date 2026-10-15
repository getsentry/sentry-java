plugins {
  `kotlin-dsl`
}

repositories {
  gradlePluginPortal()
  // AGP is only published to Google's repository.
  google()
}

dependencies {
  implementation(libs.animalsniffer.gradle.plugin)
  implementation(libs.spotlessLib)
  // Only for the Kotlin task types the android-compilation plugin configures.
  compileOnly(libs.kotlin.gradle.plugin)
  // Only for the AGP extension types the android-compilation plugin configures.
  compileOnly(libs.android.gradle.plugin)
}

gradlePlugin {
  plugins {
    register("sentryCompilation") {
      id = "io.sentry.compilation"
      implementationClass = "io.sentry.gradle.SentryCompilationPlugin"
    }
    register("sentryAnimalSniffer") {
      id = "io.sentry.animalsniffer"
      implementationClass = "io.sentry.gradle.SentryAnimalSnifferPlugin"
    }
    register("sentryAnimalSnifferAndroid") {
      id = "io.sentry.animalsniffer.android"
      implementationClass = "io.sentry.gradle.SentryAnimalSnifferAndroidPlugin"
    }
  }
}
