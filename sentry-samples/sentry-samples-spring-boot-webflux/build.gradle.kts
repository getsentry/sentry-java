import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.springboot2)
  alias(libs.plugins.spring.dependency.management)
  kotlin("jvm")
  alias(libs.plugins.kotlin.spring)
}

group = "io.sentry.sample.spring-boot"

version = "0.0.1-SNAPSHOT"

java.sourceCompatibility = JavaVersion.VERSION_17

java.targetCompatibility = JavaVersion.VERSION_17

repositories { mavenCentral() }

dependencies {
  implementation(libs.springboot.starter.actuator)
  implementation(libs.springboot.starter.graphql)
  implementation(libs.springboot.starter.webflux)
  implementation(Config.Libs.kotlinReflect)
  implementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
  implementation(projects.sentrySpringBootStarter)
  implementation(projects.sentryLogback)
  implementation(projects.sentryGraphql)

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

configure<SourceSetContainer> { test { java.srcDir("src/test/java") } }

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    freeCompilerArgs = listOf("-Xjsr305=strict")
    jvmTarget = JavaVersion.VERSION_17.toString()
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
