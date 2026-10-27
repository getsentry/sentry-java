import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  application
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
  id("war")
  alias(libs.plugins.gretty)
}

application { mainClass.set("io.sentry.samples.spring.jakarta.Main") }

// Ensure WAR is up to date before run task
tasks.named("run") { dependsOn(tasks.named("war")) }

group = "io.sentry.sample.spring-jakarta"

version = "0.0.1-SNAPSHOT"

dependencyManagement {
  imports {
    mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springboot3.get()}")
    mavenBom(libs.kotlin.bom.get().toString())
    mavenBom(libs.jackson.bom.get().toString())
  }
}

dependencies {
  implementation("org.springframework:spring-webmvc")
  implementation("org.springframework:spring-aop")
  implementation("org.aspectj:aspectjweaver")
  implementation("org.springframework.security:spring-security-web")
  implementation("org.springframework.security:spring-security-config")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation(kotlin("stdlib-jdk8"))
  implementation(projects.sentrySpringJakarta)
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
    compilerOptions.freeCompilerArgs = listOf("-Xjsr305=strict")
  }
}
