// A standalone build that includes the SDK build, so it depends on the SDK through the same
// io.sentry:* coordinates an app would, and Gradle substitutes the SDK's projects for them.
// The SDK build doesn't include this one back, so its plugins and versions stay out of the SDK
// build. Run it with `./gradlew -p sentry-android-integration-tests <task>`.

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

  // Every build in a composite must use the same AGP, so each build's settings applies the AGP
  // matrix job's override.
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
    google()
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

rootProject.name = "sentry-android-integration-tests"

includeBuild("..")

// For the io.sentry.spotless and io.sentry.jdk-target convention plugins.
includeBuild("../build-logic")

include("sentry-uitest-android", "sentry-uitest-android-critical", "test-app-size")
