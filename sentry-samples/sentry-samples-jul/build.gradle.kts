plugins {
  java
  application
  alias(libs.plugins.gradle.versions)
  alias(libs.plugins.shadow)
}

application { mainClass.set("io.sentry.samples.jul.Main") }

dependencies {
  implementation(projects.sentryJul)
  implementation(libs.slf4j.jdk14)
}

// Configure the Shadow JAR (executable JAR with all dependencies)
tasks.shadowJar {
  manifest { attributes["Main-Class"] = "io.sentry.samples.jul.Main" }
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
