import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.springboot3)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
}

group = "io.sentry.sample.spring-boot-webflux-jakarta"

version = "0.0.1-SNAPSHOT"

dependencyManagement {
  imports {
    mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springboot3.get()}")
  }
}

// Apollo 4.x requires coroutines 1.9.0+, override Spring Boot's managed version
extra["kotlin-coroutines.version"] = "1.9.0"

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation(kotlin("stdlib-jdk8", getKotlinPluginVersion()))
  implementation(projects.sentrySpringBootStarterJakarta)
  implementation(projects.sentryLogback)
  implementation(projects.sentryJdbc)
  implementation(projects.sentryGraphql22)
  implementation(projects.sentryAsyncProfiler)
  implementation(libs.context.propagation)
  implementation(libs.springboot3.starter.actuator)
  implementation(libs.springboot3.starter.graphql)
  implementation(libs.springboot3.starter.webflux)

  // cache tracing
  implementation(libs.springboot3.starter.cache)
  implementation(libs.caffeine)
}

tasks.withType<KotlinCompile>().configureEach {
  kotlin {
    compilerOptions.freeCompilerArgs = listOf("-Xjsr305=strict")
  }
}
