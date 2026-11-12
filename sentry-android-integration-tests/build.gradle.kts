buildscript {
  repositories { google() }
  dependencies {
    // Replaces the R8 bundled with AGP when the AGP matrix job overrides AGP. See r8 in
    // libs.versions.toml.
    if (System.getenv("VERSION_AGP") != null) classpath(libs.android.r8)
  }
}

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.errorprone) apply false
  alias(libs.plugins.detekt) apply false
  alias(libs.plugins.gradle.versions) apply false
  alias(libs.plugins.sentry) apply false
}
