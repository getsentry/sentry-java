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
  implementation(Config.Libs.springWeb)
  implementation(Config.Libs.springAop)
  implementation(Config.Libs.aspectj)
  implementation(Config.Libs.springSecurityWeb)
  implementation(Config.Libs.springSecurityConfig)
  implementation(Config.Libs.kotlinReflect)
  implementation(kotlin(Config.kotlinStdLib))
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
