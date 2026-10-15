plugins {
  java
  application
  alias(libs.plugins.gradle.versions)
  alias(libs.plugins.shadow)
}

application { mainClass.set("io.sentry.samples.logback.Main") }

java.sourceCompatibility = JavaVersion.VERSION_17

java.targetCompatibility = JavaVersion.VERSION_17

configure<JavaPluginExtension> {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
  implementation(projects.sentryLogback)
  implementation(libs.logback.classic)
}

// Configure the Shadow JAR (executable JAR with all dependencies)
tasks.shadowJar {
  manifest { attributes["Main-Class"] = "io.sentry.samples.logback.Main" }
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
