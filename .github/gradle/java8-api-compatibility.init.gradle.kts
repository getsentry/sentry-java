import org.gradle.api.Project
import org.gradle.api.JavaVersion
import org.gradle.api.plugins.JavaPluginExtension
import ru.vyarus.gradle.plugin.animalsniffer.AnimalSnifferPlugin

initscript {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
  dependencies {
    classpath("ru.vyarus:gradle-animalsniffer-plugin:2.0.1")
  }
}

val excludedPathPrefixes =
  setOf(
    ":sentry-samples",
    ":sentry-system-test-support",
    ":sentry-test-support",
  )

fun Project.isJava8SdkModule(): Boolean {
  val java = extensions.findByType(JavaPluginExtension::class.java) ?: return false
  return plugins.hasPlugin("java-library") &&
    java.targetCompatibility == JavaVersion.VERSION_1_8 &&
    excludedPathPrefixes.none { path == it || path.startsWith("$it:") }
}

allprojects {
  if (rootProject.name != "sentry-root") return@allprojects

  afterEvaluate {
    if (!isJava8SdkModule()) return@afterEvaluate

    if (!plugins.hasPlugin("ru.vyarus.animalsniffer")) {
      apply<AnimalSnifferPlugin>()
    }

    configurations.named("signature").configure {
      dependencies.clear()
    }
    dependencies.add("signature", "org.codehaus.mojo.signature:java18:1.0@signature")
  }
}

rootProject {
  tasks.register("java8ApiCompatibility") {
    group = "verification"
    description = "Checks Java 8 SDK modules against the Java 8 runtime API signature."
  }
}

gradle.projectsEvaluated {
  val java8SdkModules = rootProject.allprojects.filter { it.isJava8SdkModule() }
  rootProject.tasks.named("java8ApiCompatibility").configure {
    dependsOn(java8SdkModules.map { it.tasks.named("animalsnifferMain") })
    doFirst {
      logger.lifecycle(
        "Checking Java 8 API compatibility for: ${java8SdkModules.joinToString { it.path }}"
      )
    }
  }
}
