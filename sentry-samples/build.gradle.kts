plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.spring) apply false
  alias(libs.plugins.spring.dependency.management) apply false
  alias(libs.plugins.gradle.versions) apply false
  id("io.sentry.spotless") apply false
  id("io.sentry.jdk-target") apply false
}
