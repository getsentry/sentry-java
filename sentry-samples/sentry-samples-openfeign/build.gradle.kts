plugins {
  java
  application
  alias(libs.plugins.gradle.versions)
}

application { mainClass.set("io.sentry.samples.openfeign.Main") }

configure<JavaPluginExtension> {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
  implementation(projects.sentry)
  implementation(projects.sentryOpenfeign)
  implementation(libs.feign.core)
  implementation(libs.feign.gson)
}
