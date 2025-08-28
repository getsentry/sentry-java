import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES

plugins {
  application
  alias(libs.plugins.springboot2) apply false
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
  id("war")
  alias(libs.plugins.gretty)
}

application { mainClass.set("io.sentry.samples.spring.Main") }

// Ensure WAR is up to date before run task
tasks.named("run") { dependsOn(tasks.named("war")) }

group = "io.sentry.sample.spring"

version = "0.0.1-SNAPSHOT"

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

repositories { mavenCentral() }

dependencyManagement {
  imports {
    mavenBom(BOM_COORDINATES)
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
  implementation(projects.sentrySpring)
  implementation(projects.sentryLogback)
  implementation(libs.jackson.databind)
  implementation(libs.logback.classic)
  implementation(libs.servlet.api)

  implementation(libs.tomcat.catalina)
  implementation(libs.tomcat.embed.jasper)

  testImplementation(projects.sentrySystemTestSupport)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.springboot.starter.test) {
    exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
  }
}

tasks.withType<KotlinCompile>().configureEach {
  kotlin {
    compilerOptions.freeCompilerArgs = listOf("-Xjsr305=strict")
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
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
