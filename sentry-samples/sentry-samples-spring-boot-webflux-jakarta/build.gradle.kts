import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.springboot3)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
}

group = "io.sentry.sample.spring-boot-webflux-jakarta"

version = "0.0.1-SNAPSHOT"

java.sourceCompatibility = JavaVersion.VERSION_17

java.targetCompatibility = JavaVersion.VERSION_17

repositories { mavenCentral() }

dependencies {
  implementation(Config.Libs.kotlinReflect)
  implementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
  implementation(projects.sentrySpringBootStarterJakarta)
  implementation(projects.sentryLogback)
  implementation(projects.sentryJdbc)
  implementation(projects.sentryGraphql22)
  implementation(projects.sentryAsyncProfiler)
  implementation(libs.context.propagation)
  implementation(libs.springboot3.starter.actuator)
  implementation(libs.springboot3.starter.graphql)
  implementation(libs.springboot3.starter.webflux)
  implementation(libs.springboot3.starter.mongodb)
  implementation(libs.springboot3.starter.mongodb.reactive)

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

tasks.withType<KotlinCompile>().configureEach {
  kotlin {
    compilerOptions.freeCompilerArgs = listOf("-Xjsr305=strict")
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
  }
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
