import org.jetbrains.kotlin.config.KotlinCompilerVersion
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

fun springBoot2SupportsOptionalIntegrations(): Boolean {
  val version = libs.versions.springboot2.get().removeSuffix(".RELEASE")
  val parts = version.split(".").map { it.toIntOrNull() ?: 0 }
  val major = parts.getOrElse(0) { 0 }
  val minor = parts.getOrElse(1) { 0 }
  return major > 2 || (major == 2 && minor >= 7)
}

val includeGraphql =
  !project.hasProperty("excludeGraphql") && springBoot2SupportsOptionalIntegrations()
val includeKafka = !project.hasProperty("excludeKafka") && springBoot2SupportsOptionalIntegrations()

tasks.withType<KotlinCompile>().configureEach {
  kotlin {
    compilerOptions.freeCompilerArgs = listOf("-Xjsr305=strict")
  }
}

dependencies {
  implementation(platform(libs.springboot2.bom))
  implementation(libs.springboot.starter)
  implementation(libs.springboot.starter.actuator)
  implementation(libs.springboot.starter.aop)
  if (includeGraphql) {
    implementation(libs.springboot.starter.graphql)
  }
  implementation(libs.springboot.starter.jdbc)
  implementation(libs.springboot.starter.quartz)
  implementation(libs.springboot.starter.security)
  implementation(libs.springboot.starter.web)
  implementation(libs.springboot.starter.webflux)
  implementation(libs.springboot.starter.cache)
  implementation(libs.springboot.starter.websocket)
  implementation(libs.caffeine)

  if (includeKafka) {
    implementation(libs.spring.kafka2)
    implementation(projects.sentryKafka)
  }
  implementation(Config.Libs.aspectj)
  implementation(Config.Libs.kotlinReflect)
  implementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
  implementation(projects.sentrySpringBootStarter)
  implementation(projects.sentryLogback)
  if (includeGraphql) {
    implementation(projects.sentryGraphql)
  }
  implementation(projects.sentryQuartz)
  implementation(projects.sentryAsyncProfiler)

  // okhttp client instrumentation
  implementation(projects.sentryOkhttp)
  implementation(libs.okhttp)

  // database query tracing
  implementation(projects.sentryJdbc)
  runtimeOnly(libs.hsqldb)
}

val runtimeClasspath = configurations.named("runtimeClasspath")

// Configure the Shadow JAR (executable JAR with all dependencies)
tasks.shadowJar {
  manifest { attributes["Main-Class"] = "io.sentry.samples.spring.boot.SentryDemoApplication" }
  archiveClassifier.set("")

  // Shadow 9.x enforces DuplicatesStrategy before transformers run, so `append`
  // only sees one copy of each file. We merge Spring metadata from the runtime
  // classpath and patch the built JAR in doLast.
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

configure<SourceSetContainer> {
  main {
    if (!includeGraphql) {
      java.exclude("**/graphql/**")
      resources.exclude("graphql/**")
    }
    if (!includeKafka) {
      java.exclude("**/queues/kafka/**")
      resources.exclude("application-kafka.properties")
    }
  }
}
