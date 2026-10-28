package io.sentry.gradle

import groovy.json.JsonSlurper
import javax.inject.Inject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
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
   * Registers `allIntegrationTestsBoot<version>` for each version of Spring Boot [line] in
   * `gradle/spring-boot-versions.json`, and `allIntegrationTestsBoot<line>` for all of them. The CI
   * workflows read the same file and run each version in its own job. A WAR module uses its Boot
   * module's line, because the Boot BOM decides its Spring version.
   *
   * The line's first version must be the oldest. The module's main code compiles against it in
   * `compileJavaBoot<version>`, part of that version's aggregate, because the main compilation only
   * proves the code compiles against the catalog's version.
   */
  fun versions(line: Int) {
    val file = project.layout.settingsDirectory.file("gradle/spring-boot-versions.json")
    val lines =
      JsonSlurper().parseText(project.providers.fileContents(file).asText.get()) as Map<*, *>
    val versions = (lines["boot$line"] as List<*>).map { it as String }
    this.versions = versions
    project.tasks.register("allIntegrationTestsBoot$line") {
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

    val oldest = versions.first()
    val main = sourceSet("main")
    val compileClasspath = classpath("main", oldest, compile = true)
    val compileJava = project.tasks.named("compileJava", JavaCompile::class.java)
    val compileOldest =
      project.tasks.register("compileJava${suffix(oldest)}", JavaCompile::class.java) {
        description = "Compiles the main Java sources against Spring Boot $oldest."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        source = main.java
        classpath = compileClasspath
        destinationDirectory.set(
          project.layout.buildDirectory.dir("classes/java/main${suffix(oldest)}")
        )
        options.release.set(compileJava.flatMap { it.options.release })
        options.annotationProcessorPath = main.annotationProcessorPath
      }
    project.tasks.named(aggregateName(oldest)) { dependsOn(compileOldest) }
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
  fun runtimeClasspath(sourceSet: String, version: String): FileCollection =
    sourceSet(sourceSet).output + classpath(sourceSet, version, compile = false)

  /** [sourceSet]'s compile or runtime classpath dependencies, with Spring Boot at [version]. */
  private fun classpath(sourceSet: String, version: String, compile: Boolean): FileCollection {
    val sources = sourceSet(sourceSet)
    val base =
      if (compile) sources.compileClasspathConfigurationName
      else sources.runtimeClasspathConfigurationName
    val only =
      if (compile) sources.compileOnlyConfigurationName else sources.runtimeOnlyConfigurationName
    val name = "$sourceSet${suffix(version)}${if (compile) "Compile" else "Runtime"}Classpath"
    val configurations = project.configurations
    return configurations.findByName(name)
      ?: configurations
        .resolvable(name) {
          // The base classpath's parents by name, since copying its extendsFrom would depend on
          // what has been added to it by the time this runs.
          extendsFrom(
            configurations.getByName(sources.implementationConfigurationName),
            configurations.getByName(only),
          )
          attributes.addAllLater(configurations.getByName(base).attributes)
          // What setting the version in the catalog does: every Spring Boot artifact, the BOM
          // included, is in this group. Libraries pinned to their own version still win conflicts.
          resolutionStrategy.eachDependency {
            if (requested.group == "org.springframework.boot") useVersion(version)
          }
        }
        .get()
  }

  private fun sourceSet(name: String) =
    project.extensions.getByType(SourceSetContainer::class.java).getByName(name)

  private fun aggregateName(version: String) = "allIntegrationTests${suffix(version)}"

  private fun suffix(version: String) = "Boot" + version.replace('.', '_')
}
