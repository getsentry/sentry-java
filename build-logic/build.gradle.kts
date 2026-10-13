plugins {
  `kotlin-dsl`
}

repositories {
  gradlePluginPortal()
}

dependencies {
  implementation(libs.animalsniffer.gradle.plugin)
  implementation(libs.spotlessLib)
  // Only for the Kotlin task types the jvm-targets plugin configures.
  compileOnly(libs.kotlin.gradle.plugin)
}

gradlePlugin {
  plugins {
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
