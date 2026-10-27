import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.springboot4)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
}

val sentryVersion = libs.versions.sentry.get()

group = "io.sentry.sample.spring-boot-4-webflux"

version = "0.0.1-SNAPSHOT"

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation(kotlin("stdlib-jdk8", getKotlinPluginVersion()))
  implementation("io.sentry:sentry-spring-boot-4-starter:$sentryVersion")
  implementation("io.sentry:sentry-logback:$sentryVersion")
  implementation("io.sentry:sentry-jdbc:$sentryVersion")
  implementation("io.sentry:sentry-graphql-22:$sentryVersion")
  implementation("io.sentry:sentry-async-profiler:$sentryVersion")
  implementation(libs.context.propagation)
  implementation(libs.springboot4.starter.actuator)
  implementation(libs.springboot4.starter.graphql)
  implementation(libs.springboot4.starter.webflux)
  implementation(libs.springboot4.starter.webclient)

  // cache tracing
  implementation(libs.springboot4.starter.cache)
  implementation(libs.caffeine)
}

tasks.withType<KotlinCompile>().configureEach {
  kotlin {
    explicitApi()
    // skip metadata version check, as Spring 7 / Spring Boot 4 is
    // compiled against a newer version of Kotlin
    compilerOptions.freeCompilerArgs = listOf("-Xjsr305=strict", "-Xskip-metadata-version-check")
    compilerOptions.languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
    compilerOptions.apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
  }
}
