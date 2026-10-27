plugins {
  java
  application
  alias(libs.plugins.gradle.versions)
  alias(libs.plugins.shadow)
}

application { mainClass.set("io.sentry.samples.console.Main") }

dependencies {
  implementation(projects.sentryOpentelemetry.sentryOpentelemetryOtlp)
  implementation(projects.sentryAsyncProfiler)
  implementation(libs.otel.semconv)
  implementation(libs.otel.semconv.incubating)
  implementation(libs.otel.exporter.logging)
}

// Configure the Shadow JAR (executable JAR with all dependencies)
tasks.shadowJar {
  manifest { attributes["Main-Class"] = "io.sentry.samples.console.Main" }
  archiveClassifier.set("") // Remove the classifier so it replaces the regular JAR
  duplicatesStrategy = DuplicatesStrategy.INCLUDE
  mergeServiceFiles()
}

// Make the regular jar task depend on shadowJar
tasks.jar {
  enabled = false
  dependsOn(tasks.shadowJar)
}

// Fix the startScripts task dependency
tasks.startScripts { dependsOn(tasks.shadowJar) }
