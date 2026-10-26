package io.sentry.gradle

import javax.inject.Inject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Runs integration suites on each Spring Boot version of a line, without recompiling anything.
 *
 * The suites and the SDK compile once, against the version catalog's Spring Boot version. For each
 * version, a suite's test task reuses its compiled classes on a runtime classpath resolved for that
 * version, which is how users run our published jars on their Spring Boot version.
 */
class SpringBootMatrixPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.extensions.create("springBootMatrix", SpringBootMatrixExtension::class.java)
  }
}

abstract class SpringBootMatrixExtension @Inject constructor(private val project: Project) {
  private var versions = emptyList<String>()

  /**
   * Registers `allIntegrationTestsBoot<version>` for each of [versions], and
   * `allIntegrationTestsBoot<major>` for all of them, which CI runs from the root project for every
   * module of the line at once.
   */
  fun versions(versions: List<String>) {
    this.versions = versions
    val major = versions.map { it.substringBefore('.') }.distinct().single()
    project.tasks.register("allIntegrationTestsBoot$major") {
      description = "Runs the integration suites on Spring Boot ${versions.joinToString()}."
      group = LifecycleBasePlugin.VERIFICATION_GROUP
      dependsOn(versions.map { aggregateName(it) })
    }
    versions.forEach { version ->
      project.tasks.register(aggregateName(version)) {
        description = "Runs the integration suites on Spring Boot $version."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
      }
    }
  }

  /**
   * Registers `<name>Boot<version>` for each version, running [suite]'s tests. [name] tells apart
   * several tasks of one suite, and [configure] adds what the suite's own task has beyond its
   * classes and classpath.
   */
  fun test(suite: String, name: String = suite, configure: Test.(version: String) -> Unit = {}) {
    check(versions.isNotEmpty()) { "Call versions() before test()" }
    versions.forEach { version ->
      // Created now rather than with the task, so `dependencies --configuration` finds it.
      val runtimeClasspath = runtimeClasspath(suite, version)
      val task =
        project.tasks.register("$name${suffix(version)}", Test::class.java) {
          description = "Runs $name on Spring Boot $version."
          group = LifecycleBasePlugin.VERIFICATION_GROUP
          testClassesDirs = sourceSet(suite).output.classesDirs
          classpath = runtimeClasspath
          useJUnit()
          configure(version)
        }
      project.tasks.named(aggregateName(version)) { dependsOn(task) }
    }
  }

  /** [sourceSet]'s output and runtime dependencies, with Spring Boot at [version]. */
  fun runtimeClasspath(sourceSet: String, version: String): FileCollection {
    val sources = sourceSet(sourceSet)
    val name = "$sourceSet${suffix(version)}RuntimeClasspath"
    val configurations = project.configurations
    val dependencies =
      configurations.findByName(name)
        ?: configurations.create(name) {
          isCanBeConsumed = false
          extendsFrom(
            configurations.getByName(sources.implementationConfigurationName),
            configurations.getByName(sources.runtimeOnlyConfigurationName),
          )
          attributes.addAllLater(
            configurations.getByName(sources.runtimeClasspathConfigurationName).attributes
          )
          // What setting the version in the catalog does: every Spring Boot artifact, the BOM
          // included, is in this group. Libraries pinned to their own version still win conflicts.
          resolutionStrategy.eachDependency {
            if (requested.group == "org.springframework.boot") useVersion(version)
          }
        }
    return sources.output + dependencies
  }

  private fun sourceSet(name: String) =
    project.extensions.getByType(SourceSetContainer::class.java).getByName(name)

  private fun aggregateName(version: String) = "allIntegrationTests${suffix(version)}"

  private fun suffix(version: String) = "Boot" + version.replace('.', '_')
}
