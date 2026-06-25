import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  java
  application
  alias(libs.plugins.shadow)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
  id("io.sentry.systemtest")
}

application { mainClass.set("io.sentry.samples.spring.boot.SentryDemoApplication") }

group = "io.sentry.sample.spring-boot"

version = "0.0.1-SNAPSHOT"

java.sourceCompatibility = JavaVersion.VERSION_11

java.targetCompatibility = JavaVersion.VERSION_11

repositories { mavenCentral() }

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

configure<JavaPluginExtension> {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions.jvmTarget = JvmTarget.JVM_11
  kotlin {
    compilerOptions.freeCompilerArgs = listOf("-Xjsr305=strict")
    compilerOptions.jvmTarget = JvmTarget.JVM_11
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
  implementation(libs.springboot.starter.websocket)
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
  implementation(libs.otel)

  if (includeKafka) {
    implementation(libs.spring.kafka2)
    implementation(projects.sentryKafka)
  }

  // database query tracing
  implementation(projects.sentryJdbc)
  runtimeOnly(libs.hsqldb)

  testImplementation(kotlin(Config.kotlinStdLib))
  testImplementation(projects.sentrySystemTestSupport)
  testImplementation(libs.apollo3.kotlin)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.slf4j2.api)
  testImplementation(libs.springboot.starter.test) {
    exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
  }
  testImplementation("ch.qos.logback:logback-classic:1.5.16")
  testImplementation("ch.qos.logback:logback-core:1.5.16")
  testImplementation("org.apache.httpcomponents:httpclient")
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

tasks.register<JavaExec>("bootRunWithAgent").configure {
  group = "application"

  mainClass.set("io.sentry.samples.spring.boot.SentryDemoApplication")
  classpath = sourceSets["main"].runtimeClasspath

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

// The runner launches this sample with -javaagent, so track the agent jar as a systemTest input.
sentrySystemTest { usesOpenTelemetryAgent = true }

tasks.register<Test>("systemTest").configure {
  group = "verification"
  description = "Runs the System tests"

  val test = project.extensions.getByType<SourceSetContainer>()["test"]
  testClassesDirs = test.output.classesDirs
  classpath = test.runtimeClasspath

  maxParallelForks = 1

  // Cap JVM args per test
  minHeapSize = "128m"
  maxHeapSize = "1g"

  filter {
    includeTestsMatching("io.sentry.systemtest*")
    if (!includeGraphql) {
      excludeTestsMatching("io.sentry.systemtest.Graphql*")
    }
    if (!includeKafka) {
      excludeTestsMatching("io.sentry.systemtest.Kafka*")
    }
  }
}

tasks.named("test").configure {
  require(this is Test)

  filter { excludeTestsMatching("io.sentry.systemtest.*") }
}
