plugins {
  war
  id("io.sentry.spotless")
  id("io.sentry.jdk-target")
}

val sentryVersion = libs.versions.sentry.get()

group = "io.sentry.sample.servlet"

version = "0.0.1-SNAPSHOT"

java.sourceCompatibility = JavaVersion.VERSION_1_8

java.targetCompatibility = JavaVersion.VERSION_1_8

dependencies {
  implementation("io.sentry:sentry-servlet:$sentryVersion")
  implementation(libs.servlet4.api)
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }
