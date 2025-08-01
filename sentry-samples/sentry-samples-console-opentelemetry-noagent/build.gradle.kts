import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  java
  application
  kotlin("jvm")
  alias(libs.plugins.gradle.versions)
  id("com.github.johnrengelman.shadow") version "8.1.1"
}

application { mainClass.set("io.sentry.samples.console.Main") }

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
  implementation(projects.sentryOpentelemetry.sentryOpentelemetryAgentless)

  testImplementation(kotlin(Config.kotlinStdLib))
  testImplementation(projects.sentry)
  testImplementation(projects.sentrySystemTestSupport)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.slf4j.api)
  testImplementation(libs.slf4j.jdk14)
}

// Configure the Shadow JAR (executable JAR with all dependencies)
tasks.shadowJar {
  manifest { attributes["Main-Class"] = "io.sentry.samples.console.Main" }
  archiveClassifier.set("") // Remove the classifier so it replaces the regular JAR
  mergeServiceFiles()
}

// Make the regular jar task depend on shadowJar
tasks.jar {
  enabled = false
  dependsOn(tasks.shadowJar)
}

// Fix the startScripts task dependency
tasks.startScripts { dependsOn(tasks.shadowJar) }

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
