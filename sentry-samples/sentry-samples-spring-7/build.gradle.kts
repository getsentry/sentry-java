import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
  alias(libs.plugins.springboot4) apply false
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
  id("war")
  alias(libs.plugins.gretty)
}

group = "io.sentry.sample.spring-7"

version = "0.0.1-SNAPSHOT"

java.sourceCompatibility = JavaVersion.VERSION_17

java.targetCompatibility = JavaVersion.VERSION_17

repositories { mavenCentral() }

dependencyManagement { imports { mavenBom(SpringBootPlugin.BOM_COORDINATES) } }

dependencies {
  implementation(Config.Libs.springWeb)
  implementation(Config.Libs.springAop)
  implementation(Config.Libs.aspectj)
  implementation(Config.Libs.springSecurityWeb)
  implementation(Config.Libs.springSecurityConfig)
  implementation(Config.Libs.kotlinReflect)
  implementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
  implementation(projects.sentrySpring7)
  implementation(projects.sentryLogback)
  implementation(libs.jackson.databind)
  implementation(libs.logback.classic)
  implementation(libs.servlet.jakarta.api)
  implementation(libs.slf4j2.api)
  testImplementation(libs.springboot.starter.test) {
    exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
  }
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

tasks.withType<KotlinCompile>().configureEach {
  kotlin {
    compilerOptions.freeCompilerArgs = listOf("-Xjsr305=strict")
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
  }
}
