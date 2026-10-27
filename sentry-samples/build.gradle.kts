// Loads the shared plugins once for every sample, so they all share one classpath. The Spring Boot
// plugin is applied in each sample instead, because the samples use different versions of it.
plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.spring) apply false
  alias(libs.plugins.spring.dependency.management) apply false
  alias(libs.plugins.gradle.versions) apply false
  id("io.sentry.spotless") apply false
  id("io.sentry.jdk-target") apply false
}

subprojects {
  apply(plugin = "io.sentry.spotless")
  apply(plugin = "io.sentry.jdk-target")
}
