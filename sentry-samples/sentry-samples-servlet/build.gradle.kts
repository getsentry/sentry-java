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
  implementation("javax.servlet:javax.servlet-api:4.0.1")
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }
