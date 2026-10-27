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
    register("sentryJdkTarget") {
      id = "io.sentry.jdk-target"
      implementationClass = "io.sentry.gradle.JdkTargetConvention"
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
