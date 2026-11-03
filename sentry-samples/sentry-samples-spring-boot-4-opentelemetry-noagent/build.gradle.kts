import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.springboot4)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
}

group = "io.sentry.sample.spring-boot-4"

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
  implementation(libs.springboot4.starter.webclient)
  implementation(libs.springboot4.starter.restclient)
  implementation(Config.Libs.aspectj)
  implementation(Config.Libs.kotlinReflect)
  implementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
  implementation(projects.sentrySpringBoot4Starter)
  implementation(projects.sentryLogback)
  implementation(projects.sentryGraphql22)
  implementation(projects.sentryQuartz)
  implementation(projects.sentryOpentelemetry.sentryOpentelemetryAgentlessSpring)
  implementation(projects.sentryAsyncProfiler)

  // kafka
  implementation(libs.springboot4.starter.kafka)
  implementation(projects.sentryKafka)

  // database query tracing
  implementation(projects.sentryJdbc)
  runtimeOnly(libs.hsqldb)
}

dependencyManagement { imports { mavenBom(libs.otel.instrumentation.bom.get().toString()) } }
