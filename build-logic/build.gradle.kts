plugins {
  `kotlin-dsl`
}

dependencies {
  implementation(libs.animalsniffer.gradle.plugin)
  implementation(libs.spotlessLib)
  compileOnly(libs.kotlin.gradle.plugin)
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
