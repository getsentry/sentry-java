import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  application
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
  id("war")
  alias(libs.plugins.gretty)
}

// Spring scans this app's own bytecode with its repackaged ASM at startup, and the oldest version
// the Spring Boot 2.x matrix covers (2.4.13, Spring 5.3.13) fails on class files as new as the
// toolchain's: "Unsupported class file major version 65". Only the app is scanned, so its test
// code keeps following the toolchain.
tasks.named<JavaCompile>("compileJava") { options.release.set(8) }

tasks.named<KotlinCompile>("compileKotlin") {
  compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
}

application { mainClass.set("io.sentry.samples.spring.Main") }

// Ensure WAR is up to date before run task
tasks.named("run") { dependsOn(tasks.named("war")) }

group = "io.sentry.sample.spring"

version = "0.0.1-SNAPSHOT"

dependencyManagement {
  imports {
    mavenBom(libs.springboot2.bom.get().toString())
    mavenBom(libs.kotlin.bom.get().toString())
    mavenBom(libs.jackson.bom.get().toString())
    mavenBom(libs.okhttp.bom.get().toString())
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
  implementation(projects.sentrySpring)
  implementation(projects.sentryLogback)
  implementation(projects.sentryAsyncProfiler)
  implementation(libs.jackson.databind)
  implementation(libs.logback.classic)
  implementation(libs.servlet.api)

  implementation(libs.tomcat.catalina)
  implementation(libs.tomcat.embed.jasper)
}

tasks.withType<KotlinCompile>().configureEach {
  kotlin {
    compilerOptions.freeCompilerArgs = listOf("-Xjsr305=strict")
  }
}
