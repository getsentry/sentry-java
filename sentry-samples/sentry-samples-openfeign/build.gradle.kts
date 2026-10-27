plugins {
  java
  application
  alias(libs.plugins.gradle.versions)
  id("io.sentry.spotless")
  id("io.sentry.jdk-target")
}

val sentryVersion = libs.versions.sentry.get()

application { mainClass.set("io.sentry.samples.openfeign.Main") }

configure<JavaPluginExtension> {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
  implementation("io.sentry:sentry:$sentryVersion")
  implementation("io.sentry:sentry-openfeign:$sentryVersion")
  implementation(libs.feign.core)
  implementation(libs.feign.gson)
}
