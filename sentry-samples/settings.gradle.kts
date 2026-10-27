// A standalone build that includes the SDK build, so the samples depend on the SDK through the same
// io.sentry:* coordinates an app would, and Gradle substitutes the SDK's projects for them.
// The SDK build doesn't include this one back, so the samples' plugins and versions stay out of
// the SDK build. Run it with `./gradlew -p sentry-samples <task>`.
// sentry-samples-android is a separate build of its own.

pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins {
  id("com.gradle.develocity") version "4.5.0"
  id("com.gradle.common-custom-user-data-gradle-plugin") version "2.8.0"
}

develocity {
  buildScan {
    termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
    termsOfUseAgree.set("yes")
  }
}

// Gradle reads gradle.properties only for the build it belongs to, so read the SDK version from the
// SDK build's file.
val sentryVersion: String =
  providers
    .fileContents(layout.settingsDirectory.file("../gradle.properties"))
    .asText
    .map { java.util.Properties().apply { load(it.reader()) }.getProperty("versionName") }
    .get()

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    mavenLocal()
  }
  versionCatalogs {
    create("libs") {
      from(files("../gradle/libs.versions.toml"))
      version("sentry", sentryVersion)
    }
  }
}

rootProject.name = "sentry-samples"

includeBuild("..")

// For the io.sentry.spotless and io.sentry.jdk-target convention plugins.
includeBuild("../build-logic")

include(
  "sentry-samples-console",
  "sentry-samples-console-opentelemetry-noagent",
  "sentry-samples-console-otlp",
  "sentry-samples-jul",
  "sentry-samples-ktor-client",
  "sentry-samples-log4j2",
  "sentry-samples-logback",
  "sentry-samples-netflix-dgs",
  "sentry-samples-openfeign",
  "sentry-samples-servlet",
  "sentry-samples-spring",
  "sentry-samples-spring-7",
  "sentry-samples-spring-boot",
  "sentry-samples-spring-boot-4",
  "sentry-samples-spring-boot-4-log4j2",
  "sentry-samples-spring-boot-4-opentelemetry",
  "sentry-samples-spring-boot-4-opentelemetry-noagent",
  "sentry-samples-spring-boot-4-otlp",
  "sentry-samples-spring-boot-4-webflux",
  "sentry-samples-spring-boot-jakarta",
  "sentry-samples-spring-boot-jakarta-log4j2",
  "sentry-samples-spring-boot-jakarta-opentelemetry",
  "sentry-samples-spring-boot-jakarta-opentelemetry-noagent",
  "sentry-samples-spring-boot-opentelemetry",
  "sentry-samples-spring-boot-opentelemetry-noagent",
  "sentry-samples-spring-boot-webflux",
  "sentry-samples-spring-boot-webflux-jakarta",
  "sentry-samples-spring-jakarta",
)
