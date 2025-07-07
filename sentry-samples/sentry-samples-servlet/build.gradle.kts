plugins { war }

group = "io.sentry.sample.servlet"

version = "0.0.1-SNAPSHOT"

java.sourceCompatibility = JavaVersion.VERSION_1_8

java.targetCompatibility = JavaVersion.VERSION_1_8

repositories { mavenCentral() }

dependencies {
  implementation(projects.sentryServlet)
  implementation("javax.servlet:javax.servlet-api:4.0.1")
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }
