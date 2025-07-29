import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
  alias(libs.plugins.springboot3)
  alias(libs.plugins.spring.dependency.management)
  kotlin("jvm")
  alias(libs.plugins.kotlin.spring)
}

group = "io.sentry.sample.spring-boot-jakarta"

version = "0.0.1-SNAPSHOT"

java.sourceCompatibility = JavaVersion.VERSION_17

java.targetCompatibility = JavaVersion.VERSION_17

repositories { mavenCentral() }

configure<JavaPluginExtension> {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions.jvmTarget = JavaVersion.VERSION_17.toString()
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    freeCompilerArgs = listOf("-Xjsr305=strict")
    jvmTarget = JavaVersion.VERSION_17.toString()
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
  implementation(Config.Libs.aspectj)
  implementation(Config.Libs.kotlinReflect)
  implementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
  implementation(projects.sentrySpringBootStarterJakarta)
  implementation(projects.sentryLogback)
  implementation(projects.sentryGraphql22)
  implementation(projects.sentryQuartz)
  implementation(libs.otel)
  implementation(projects.sentryAsyncProfiler)

  // database query tracing
  implementation(projects.sentryJdbc)
  runtimeOnly(libs.hsqldb)

  testImplementation(kotlin(Config.kotlinStdLib))
  testImplementation(projects.sentrySystemTestSupport)
  testImplementation(libs.apollo3.kotlin)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.slf4j2.api)
  testImplementation(libs.springboot3.starter.test) {
    exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
  }
  testImplementation("ch.qos.logback:logback-classic:1.5.16")
  testImplementation("ch.qos.logback:logback-core:1.5.16")
}

configure<SourceSetContainer> { test { java.srcDir("src/test/java") } }

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
  environment("SENTRY_DEBUG", "true")
  environment("SENTRY_PROFILE_SESSION_SAMPLE_RATE", "1.0")
  environment("SENTRY_PROFILING_TRACES_DIR_PATH", "tmp/sentry/profiling-traces")
  environment("SENTRY_PROFILE_LIFECYCLE", "TRACE")

  environment("SENTRY_TRACES_SAMPLE_RATE", tracesSampleRate)
  environment("OTEL_TRACES_EXPORTER", "none")
  environment("OTEL_METRICS_EXPORTER", "none")
  environment("OTEL_LOGS_EXPORTER", "none")
  environment("SENTRY_IN_APP_INCLUDES", "io.sentry.samples")
  environment("SENTRY_ENABLE_PRETTY_SERIALIZATION_OUTPUT", "false")

  jvmArgs = listOf("-Dotel.javaagent.debug=true", "-javaagent:$agentJarPath")
}

tasks.register<Test>("systemTest").configure {
  group = "verification"
  description = "Runs the System tests"

  outputs.upToDateWhen { false }

  maxParallelForks = 1

  // Cap JVM args per test
  minHeapSize = "128m"
  maxHeapSize = "1g"

  filter { includeTestsMatching("io.sentry.systemtest*") }
}

tasks.named("test").configure {
  require(this is Test)

  filter { excludeTestsMatching("io.sentry.systemtest.*") }
}
