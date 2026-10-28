plugins { war }

group = "io.sentry.sample.servlet"

version = "0.0.1-SNAPSHOT"

java.sourceCompatibility = JavaVersion.VERSION_1_8

java.targetCompatibility = JavaVersion.VERSION_1_8

dependencies {
  implementation("io.sentry:sentry-servlet:$sentryVersion")
  implementation("javax.servlet:javax.servlet-api:4.0.1")
}

val sentryVersion = libs.versions.sentry.get()

tasks.withType<Test>().configureEach { useJUnitPlatform() }
