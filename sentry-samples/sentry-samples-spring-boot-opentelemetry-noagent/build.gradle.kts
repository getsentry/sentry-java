import io.sentry.gradle.MergeSpringMetadataAction
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  java
  application
  alias(libs.plugins.shadow)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
}

// Spring scans this app's own bytecode with its repackaged ASM at startup, and the oldest version
// the Spring Boot 2.x matrix covers (2.4.13, Spring 5.3.13) fails on class files as new as the
// toolchain's: "Unsupported class file major version 65". Only the app is scanned, so its test
// code keeps following the toolchain.
tasks.named<JavaCompile>("compileJava") { options.release.set(11) }

tasks.named<KotlinCompile>("compileKotlin") {
  compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
}

application { mainClass.set("io.sentry.samples.spring.boot.SentryDemoApplication") }

group = "io.sentry.sample.spring-boot"

version = "0.0.1-SNAPSHOT"

tasks.withType<KotlinCompile>().configureEach {
  kotlin {
    compilerOptions.freeCompilerArgs = listOf("-Xjsr305=strict")
  }
}

dependencies {
  implementation(platform(libs.springboot2.bom))
  implementation(platform(libs.otel.instrumentation.bom))
  implementation(libs.springboot.starter)
  implementation(libs.springboot.starter.actuator)
  implementation(libs.springboot.starter.aop)
  implementation(libs.springboot.starter.graphql)
  implementation(libs.springboot.starter.jdbc)
  implementation(libs.springboot.starter.quartz)
  implementation(libs.springboot.starter.security)
  implementation(libs.springboot.starter.web)
  implementation(libs.springboot.starter.webflux)
  implementation(libs.springboot.starter.websocket)
  implementation("org.aspectj:aspectjweaver")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation(kotlin("stdlib-jdk8", getKotlinPluginVersion()))
  implementation(projects.sentrySpringBootStarter)
  implementation(projects.sentryLogback)
  implementation(projects.sentryGraphql)
  implementation(projects.sentryQuartz)
  implementation(projects.sentryOpentelemetry.sentryOpentelemetryAgentlessSpring)
  implementation(projects.sentryAsyncProfiler)

  implementation(libs.spring.kafka2)
  implementation(projects.sentryKafka)

  // database query tracing
  implementation(projects.sentryJdbc)
  runtimeOnly(libs.hsqldb)
}

val runtimeClasspath = configurations.named("runtimeClasspath")

// Configure the Shadow JAR (executable JAR with all dependencies)
tasks.shadowJar {
  manifest { attributes["Main-Class"] = "io.sentry.samples.spring.boot.SentryDemoApplication" }
  archiveClassifier.set("")

  doLast(
    MergeSpringMetadataAction(
      runtimeClasspath.get(),
      MergeSpringMetadataAction.DEFAULT_SPRING_METADATA_FILES,
    )
  )
}

tasks.jar {
  enabled = false
  dependsOn(tasks.shadowJar)
}

tasks.startScripts { dependsOn(tasks.shadowJar) }
