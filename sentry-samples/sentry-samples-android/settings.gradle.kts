// This sample is a standalone Gradle build that includes the SDK build, so it consumes the SDK
// through the same `io.sentry:*` coordinates a real app would. Gradle substitutes those with the
// projects of the included build, so there is nothing to publish first.
// Run it with `./gradlew -p sentry-samples/sentry-samples-android <task>` from the repository root.

pluginManagement {
  repositories {
    // Prefer local SAGP artifact if one exists; otherwise fall back to libs.versions.toml.
    if (providers.gradleProperty("useSagp").isPresent) {
      mavenLocal {
        content {
          includeGroup("io.sentry")
          includeGroup("io.sentry.android.gradle")
        }
      }
    }
    mavenCentral()
    gradlePluginPortal()
    google()
  }

  // The AGP compatibility matrix job pins a specific AGP; the catalog version is the default.
  System.getenv("VERSION_AGP")?.let { agpVersion ->
    resolutionStrategy {
      eachPlugin {
        if (requested.id.id.startsWith("com.android")) {
          useVersion(agpVersion)
        }
      }
    }
  }
}

plugins {
  id("com.gradle.develocity") version "4.4.2"
  id("com.gradle.common-custom-user-data-gradle-plugin") version "2.6.0"
}

develocity {
  buildScan {
    termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
    termsOfUseAgree.set("yes")
  }
}

dependencyResolutionManagement {
  repositories {
    google()
    mavenCentral()
    mavenLocal()
  }
  versionCatalogs { create("libs") { from(files("../../gradle/libs.versions.toml")) } }
}

rootProject.name = "sentry-samples-android"

// The SDK, which supplies every io.sentry:* dependency by substitution.
includeBuild("../..")

// Convention plugins, for io.sentry.spotless.
includeBuild("../../build-logic")

include("macrobenchmark")
