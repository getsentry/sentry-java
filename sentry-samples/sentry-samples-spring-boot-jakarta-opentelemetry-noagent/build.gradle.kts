import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.springboot3)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
}

group = "io.sentry.sample.spring-boot-jakarta"

version = "0.0.1-SNAPSHOT"

dependencyManagement {
  imports {
    mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springboot3.get()}")
    mavenBom(libs.otel.instrumentation.bom.get().toString())
  }
}

// Apollo 4.x requires coroutines 1.9.0+, override Spring Boot's managed version
extra["kotlin-coroutines.version"] = "1.9.0"

tasks.withType<KotlinCompile>().configureEach {
  kotlin {
    compilerOptions.freeCompilerArgs = listOf("-Xjsr305=strict")
  }
}

dependencies {
  implementation(libs.springboot3.starter)
  implementation(libs.springboot3.starter.actuator)
  implementation(libs.springboot3.starter.aop)
  implementation(libs.springboot3.starter.graphql)
  implementation(libs.springboot3.starter.jdbc)
  implementation(libs.springboot3.starter.quartz)
  implementation(libs.springboot3.starter.security)
  implementation(libs.springboot3.starter.web)
  implementation(libs.springboot3.starter.webflux)
  implementation(libs.springboot3.starter.websocket)
  implementation("org.aspectj:aspectjweaver")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation(kotlin("stdlib-jdk8", getKotlinPluginVersion()))
  implementation(projects.sentrySpringBootStarterJakarta)
  implementation(projects.sentryLogback)
  implementation(projects.sentryGraphql22)
  implementation(projects.sentryQuartz)
  implementation(projects.sentryAsyncProfiler)
  implementation(projects.sentryOpentelemetry.sentryOpentelemetryAgentlessSpring)

  // kafka
  implementation(libs.spring.kafka3)
  implementation(projects.sentryKafka)

  // cache tracing
  implementation(libs.springboot3.starter.cache)
  implementation(libs.caffeine)

  // database query tracing
  implementation(projects.sentryJdbc)
  runtimeOnly(libs.hsqldb)
}
