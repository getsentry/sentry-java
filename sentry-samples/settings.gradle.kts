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
  id("com.gradle.develocity") version "4.6.1"
  id("com.gradle.common-custom-user-data-gradle-plugin") version "2.8.0"
}

develocity {
  buildScan {
    termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
    termsOfUseAgree.set("yes")
  }
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    mavenLocal()
  }
  versionCatalogs {
    create("libs") {
      from(files("../gradle/libs.versions.toml"))
      // gradle.properties links to the SDK build's, so versionName is the SDK version.
      version("sentry", providers.gradleProperty("versionName").get())
    }
  }
}

rootProject.name = "sentry-samples"

includeBuild("..")

// For the io.sentry.spotless and io.sentry.jdk-target convention plugins.
includeBuild("../build-logic")

include("sentry-samples-console")
include("sentry-samples-console-opentelemetry-noagent")
include("sentry-samples-console-otlp")
include("sentry-samples-jul")
include("sentry-samples-ktor-client")
include("sentry-samples-log4j2")
include("sentry-samples-logback")
include("sentry-samples-netflix-dgs")
include("sentry-samples-openfeign")
include("sentry-samples-servlet")
include("sentry-samples-spring")
include("sentry-samples-spring-7")
include("sentry-samples-spring-boot")
include("sentry-samples-spring-boot-4")
include("sentry-samples-spring-boot-4-log4j2")
include("sentry-samples-spring-boot-4-opentelemetry")
include("sentry-samples-spring-boot-4-opentelemetry-noagent")
include("sentry-samples-spring-boot-4-otlp")
include("sentry-samples-spring-boot-4-webflux")
include("sentry-samples-spring-boot-jakarta")
include("sentry-samples-spring-boot-jakarta-log4j2")
include("sentry-samples-spring-boot-jakarta-opentelemetry")
include("sentry-samples-spring-boot-jakarta-opentelemetry-noagent")
include("sentry-samples-spring-boot-opentelemetry")
include("sentry-samples-spring-boot-opentelemetry-noagent")
include("sentry-samples-spring-boot-webflux")
include("sentry-samples-spring-boot-webflux-jakarta")
include("sentry-samples-spring-jakarta")
