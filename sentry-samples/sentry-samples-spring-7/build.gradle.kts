import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
  application
  alias(libs.plugins.springboot4) apply false
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
  id("war")
  alias(libs.plugins.gretty)
}

application { mainClass.set("io.sentry.samples.spring7.Main") }

// Ensure WAR is up to date before run task
tasks.named("run") { dependsOn(tasks.named("war")) }

group = "io.sentry.sample.spring-7"

version = "0.0.1-SNAPSHOT"

dependencyManagement {
  imports {
    mavenBom(SpringBootPlugin.BOM_COORDINATES)
    mavenBom(libs.kotlin.bom.get().toString())
  }
}

dependencies {
  implementation("org.springframework:spring-webmvc")
  implementation("org.springframework:spring-aop")
  implementation("org.aspectj:aspectjweaver")
  implementation("org.springframework.security:spring-security-web")
  implementation("org.springframework.security:spring-security-config")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation(kotlin("stdlib-jdk8", getKotlinPluginVersion()))
  implementation(projects.sentrySpring7)
  implementation(projects.sentryLogback)
  implementation(projects.sentryAsyncProfiler)
  implementation(libs.jackson.databind)
  implementation(libs.logback.classic)
  implementation(libs.servlet.jakarta.api)
  implementation(libs.slf4j2.api)

  implementation(libs.tomcat.catalina.jakarta)
  implementation(libs.tomcat.embed.jasper.jakarta)
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
