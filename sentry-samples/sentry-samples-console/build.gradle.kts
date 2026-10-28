plugins {
  java
  application
  alias(libs.plugins.gradle.versions)
  alias(libs.plugins.shadow)
}

val sentryVersion = libs.versions.sentry.get()

application { mainClass.set("io.sentry.samples.console.Main") }

dependencies {
  implementation("io.sentry:sentry:$sentryVersion")
  implementation("io.sentry:sentry-async-profiler:$sentryVersion")
  implementation("io.sentry:sentry-jcache:$sentryVersion")
  implementation("io.sentry:sentry-kafka:$sentryVersion")
  implementation(libs.jcache)
  implementation(libs.caffeine.jcache)
  implementation(libs.kafka.clients)
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
