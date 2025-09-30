import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
  application
  alias(libs.plugins.springboot4) apply false
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
  id("war")
  alias(libs.plugins.gretty)
}

application { mainClass.set("io.sentry.samples.spring7.Main") }

// Ensure WAR is up to date before run task
tasks.named("run") { dependsOn(tasks.named("war")) }

group = "io.sentry.sample.spring-7"

version = "0.0.1-SNAPSHOT"

java.sourceCompatibility = JavaVersion.VERSION_17

java.targetCompatibility = JavaVersion.VERSION_17

repositories { mavenCentral() }

dependencyManagement { imports { mavenBom(SpringBootPlugin.BOM_COORDINATES) } }

dependencies {
  implementation(Config.Libs.springWeb)
  implementation(Config.Libs.springAop)
  implementation(Config.Libs.aspectj)
  implementation(Config.Libs.springSecurityWeb)
  implementation(Config.Libs.springSecurityConfig)
  implementation(Config.Libs.kotlinReflect)
  implementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
  implementation(projects.sentrySpring7)
  implementation(projects.sentryLogback)
  implementation(libs.jackson.databind)
  implementation(libs.logback.classic)
  implementation(libs.servlet.jakarta.api)
  implementation(libs.slf4j2.api)

  implementation(libs.tomcat.catalina.jakarta)
  implementation(libs.tomcat.embed.jasper.jakarta)

  testImplementation(projects.sentrySystemTestSupport)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.springboot.starter.test) {
    exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
  }
}

tasks.withType<KotlinCompile>().configureEach {
  kotlin {
    explicitApi()
    // skip metadata version check, as Spring 7 / Spring Boot 4 is
    // compiled against a newer version of Kotlin
    compilerOptions.freeCompilerArgs = listOf("-Xjsr305=strict", "-Xskip-metadata-version-check")
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    compilerOptions.languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
    compilerOptions.apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
  }
}

configure<SourceSetContainer> { test { java.srcDir("src/test/java") } }

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
