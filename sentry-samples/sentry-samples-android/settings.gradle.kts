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
  val agpVersion = System.getenv("VERSION_AGP")
  // With -PuseSagp, -PsagpVersion picks the Sentry Android Gradle Plugin version, e.g. one
  // published
  // to Maven Local that's newer than libs.versions.toml's.
  val sagpVersion = providers.gradleProperty("sagpVersion").orNull
  resolutionStrategy {
    eachPlugin {
      if (agpVersion != null && requested.id.id.startsWith("com.android")) {
        useVersion(agpVersion)
      }
      if (sagpVersion != null && requested.id.id == "io.sentry.android.gradle") {
        useVersion(sagpVersion)
      }
    }
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
    google()
    mavenCentral()
    mavenLocal()
  }
  versionCatalogs {
    create("libs") {
      from(files("../../gradle/libs.versions.toml"))
      // gradle.properties links to the SDK build's, so versionName is the SDK version.
      version("sentry", providers.gradleProperty("versionName").get())
    }
  }
}

rootProject.name = "sentry-samples-android"

includeBuild("../..")

// For the io.sentry.spotless and io.sentry.jdk-target convention plugins.
includeBuild("../../build-logic")

include("macrobenchmark")
