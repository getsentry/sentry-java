plugins {
  `kotlin-dsl`
}

dependencies {
  implementation(libs.animalsniffer.gradle.plugin)
  implementation(libs.spotlessLib)
  implementation(libs.detektLib)
  implementation(libs.vanniktechMavenPublishLib)
  implementation(libs.asm)
  compileOnly(libs.kotlin.gradle.plugin)
  compileOnly(libs.android.gradle.plugin)
}

gradlePlugin {
  plugins {
    register("sentryJdkTarget") {
      id = "io.sentry.jdk-target"
      implementationClass = "io.sentry.gradle.JdkTargetConvention"
    }
    register("sentryDetekt") {
      id = "io.sentry.detekt"
      implementationClass = "io.sentry.gradle.SentryDetektPlugin"
    }
    register("sentryPublish") {
      id = "io.sentry.publish"
      implementationClass = "io.sentry.gradle.SentryPublishPlugin"
    }
    register("sentryMultiplatformPublish") {
      id = "io.sentry.publish.multiplatform"
      implementationClass = "io.sentry.gradle.SentryMultiplatformPublishPlugin"
    }
    register("sentryApiValidation") {
      id = "io.sentry.api-validation"
      implementationClass = "io.sentry.gradle.SentryApiValidationPlugin"
    }
    register("sentryAnimalSniffer") {
      id = "io.sentry.animalsniffer"
      implementationClass = "io.sentry.gradle.SentryAnimalSnifferPlugin"
    }
    register("sentryAnimalSnifferAndroid") {
      id = "io.sentry.animalsniffer.android"
      implementationClass = "io.sentry.gradle.SentryAnimalSnifferAndroidPlugin"
    }
    register("sentrySpringBootMatrix") {
      id = "io.sentry.spring-boot-matrix"
      implementationClass = "io.sentry.gradle.SpringBootMatrixPlugin"
    }
  }
}
