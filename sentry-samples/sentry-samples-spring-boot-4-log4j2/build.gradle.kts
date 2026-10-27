import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.springboot4)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
}

group = "io.sentry.sample.spring-boot-4-log4j2"

version = "0.0.1-SNAPSHOT"

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

dependencies {
  implementation(libs.springboot4.starter)
  implementation(libs.springboot4.starter.actuator)
  implementation(libs.springboot4.starter.aspectj)
  implementation(libs.springboot4.starter.graphql)
  implementation(libs.springboot4.starter.jdbc)
  implementation(libs.springboot4.starter.quartz)
  implementation(libs.springboot4.starter.security)
  implementation(libs.springboot4.starter.web)
  implementation(libs.springboot4.starter.webflux)
  implementation(libs.springboot4.starter.websocket)
  implementation(libs.springboot4.starter.restclient)
  implementation(libs.springboot4.starter.webclient)
  implementation("org.aspectj:aspectjweaver")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation(kotlin("stdlib-jdk8", getKotlinPluginVersion()))
  implementation(projects.sentrySpringBoot4Starter)
  implementation(projects.sentryGraphql22)
  implementation(projects.sentryQuartz)
  implementation(projects.sentryAsyncProfiler)

  implementation(projects.sentryLog4j2)
  implementation("org.springframework.boot:spring-boot-starter-log4j2")
  modules {
    module("org.springframework.boot:spring-boot-starter-logging") {
      replacedBy(
        "org.springframework.boot:spring-boot-starter-log4j2",
        "Use Log4j2 instead of Logback",
      )
    }
  }

  // cache tracing
  implementation(libs.springboot4.starter.cache)
  implementation(libs.caffeine)

  // kafka
  implementation(libs.springboot4.starter.kafka)
  implementation(projects.sentryKafka)

  // database query tracing
  implementation(projects.sentryJdbc)
  runtimeOnly(libs.hsqldb)
}
