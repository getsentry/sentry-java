plugins {
  kotlin("jvm")
  application
  alias(libs.plugins.gradle.versions)
}

application { mainClass.set("io.sentry.samples.ktor.Main") }

configure<JavaPluginExtension> {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
}

dependencies {
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.java)
  implementation(libs.kotlinx.coroutines)
  implementation(projects.sentry)
  implementation(projects.sentryKtor)
}

tasks.test { useJUnitPlatform() }
