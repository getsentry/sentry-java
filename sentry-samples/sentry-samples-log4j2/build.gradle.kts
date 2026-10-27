plugins {
  java
  application
  alias(libs.plugins.gradle.versions)
  alias(libs.plugins.shadow)
}

application { mainClass.set("io.sentry.samples.log4j2.Main") }

dependencies {
  implementation(projects.sentryLog4j2)
  implementation(libs.log4j.api)
  implementation(libs.log4j.core)
}

// Configure the Shadow JAR (executable JAR with all dependencies)
tasks.shadowJar {
  manifest { attributes["Main-Class"] = "io.sentry.samples.log4j2.Main" }
  archiveClassifier.set("") // Remove the classifier so it replaces the regular JAR
  duplicatesStrategy = DuplicatesStrategy.INCLUDE
  mergeServiceFiles()
  // Use Log4j2 cache transformer to properly handle plugin files
  transform(
    com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer()
  )
}

// Make the regular jar task depend on shadowJar
tasks.jar {
  enabled = false
  dependsOn(tasks.shadowJar)
}

// Fix the startScripts task dependency
tasks.startScripts { dependsOn(tasks.shadowJar) }
