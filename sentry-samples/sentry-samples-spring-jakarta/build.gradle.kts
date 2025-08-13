import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
  application
  alias(libs.plugins.springboot3) apply false
  alias(libs.plugins.spring.dependency.management)
  kotlin("jvm")
  alias(libs.plugins.kotlin.spring)
  id("war")
  alias(libs.plugins.gretty)
}

application { mainClass.set("io.sentry.samples.spring.jakarta.Main") }

group = "io.sentry.sample.spring-jakarta"

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
  implementation(projects.sentrySpringJakarta)
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
  kotlinOptions {
    freeCompilerArgs = listOf("-Xjsr305=strict")
    jvmTarget = JavaVersion.VERSION_17.toString()
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
