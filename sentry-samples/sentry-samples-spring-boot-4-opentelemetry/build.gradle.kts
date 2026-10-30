import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
  alias(libs.plugins.springboot4)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
}

group = "io.sentry.sample.spring-boot-4"

version = "0.0.1-SNAPSHOT"

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

dependencies {
  implementation(libs.springboot4.starter)
  implementation(libs.springboot4.starter.actuator)
  implementation(libs.springboot4.starter.aspectj)
  implementation(libs.springboot4.starter.graphql)
  implementation(libs.springboot4.starter.jdbc)
  implementation(libs.springboot4.starter.quartz)
  implementation(libs.springboot4.starter.security)
  implementation(libs.springboot4.starter.web)
  implementation(libs.springboot4.starter.webflux)
  implementation(libs.springboot4.starter.websocket)
  implementation(libs.springboot4.starter.webclient)
  implementation(libs.springboot4.starter.restclient)
  implementation("org.aspectj:aspectjweaver")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation(kotlin("stdlib-jdk8", getKotlinPluginVersion()))
  implementation(projects.sentrySpringBoot4Starter)
  implementation(projects.sentryLogback)
  implementation(projects.sentryGraphql22)
  implementation(projects.sentryQuartz)
  implementation(projects.sentryAsyncProfiler)
  implementation(libs.otel)

  // kafka
  implementation(libs.springboot4.starter.kafka)
  implementation(projects.sentryKafka)

  // cache tracing
  implementation(libs.springboot4.starter.cache)
  implementation(libs.caffeine)

  // database query tracing
  implementation(projects.sentryJdbc)
  runtimeOnly(libs.hsqldb)
}

tasks.register<BootRun>("bootRunWithAgent").configure {
  group = "application"

  val mainBootRunTask = tasks.getByName<BootRun>("bootRun")
  mainClass = mainBootRunTask.mainClass
  classpath = mainBootRunTask.classpath

  val versionName = project.properties["versionName"] as String
  val agentJarPath =
    "$rootDir/sentry-opentelemetry/sentry-opentelemetry-agent/build/libs/sentry-opentelemetry-agent-$versionName.jar"

  val dsn =
    System.getenv("SENTRY_DSN")
      ?: "https://502f25099c204a2fbf4cb16edc5975d1@o447951.ingest.sentry.io/5428563"
  val tracesSampleRate = System.getenv("SENTRY_TRACES_SAMPLE_RATE") ?: "1"

  environment("SENTRY_DSN", dsn)
  environment("SENTRY_TRACES_SAMPLE_RATE", tracesSampleRate)
  environment("OTEL_TRACES_EXPORTER", "none")
  environment("OTEL_METRICS_EXPORTER", "none")
  environment("OTEL_LOGS_EXPORTER", "none")

  jvmArgs = listOf("-Dotel.javaagent.debug=true", "-javaagent:$agentJarPath")
}
