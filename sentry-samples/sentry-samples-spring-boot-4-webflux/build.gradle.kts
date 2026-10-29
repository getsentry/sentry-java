import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.springboot4)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
}

group = "io.sentry.sample.spring-boot-4-webflux"

version = "0.0.1-SNAPSHOT"

dependencies {
  implementation(Config.Libs.kotlinReflect)
  implementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
  implementation(projects.sentrySpringBoot4Starter)
  implementation(projects.sentryLogback)
  implementation(projects.sentryJdbc)
  implementation(projects.sentryGraphql22)
  implementation(projects.sentryAsyncProfiler)
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
