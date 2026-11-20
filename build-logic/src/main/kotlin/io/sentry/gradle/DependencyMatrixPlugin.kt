package io.sentry.gradle

import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.HasHostTestsBuilder
import com.android.build.api.variant.HostTestBuilder
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import groovy.json.JsonSlurper
import javax.inject.Inject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Runs a module's unit tests on each version of a dependency listed in a checked-in JSON file, for
 * Android library and JVM modules.
 *
 * The module's main code compiles against the version catalog's version, and the tests for each
 * version in the file run with that version, which is how users run our published artifacts on a
 * newer or older version of the dependency.
 */
class DependencyMatrixPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.extensions.create("dependencyMatrix", DependencyMatrixExtension::class.java)
  }
}

abstract class DependencyMatrixExtension @Inject constructor(private val project: Project) {
  /**
   * Registers `<name>Test<version>` for each version in `gradle/<versionsFile>`, a JSON list such
   * as `["1.12.1", "1.13.0-rc01"]`. Each task runs the module's unit tests with every dependency in
   * [groups] at that version. An entry can also be an object such as `{"version": "1.8.3", "pin":
   * {"androidx.compose.material3": "1.3.2"}}`, which also forces each group in `pin` to its
   * version, for test dependencies whose current version needs a newer [groups] version. A CI
   * workflow reads the same file and runs each version in its own job. In an Android module, call
   * it after `android {}`.
   */
  fun matrix(name: String, groups: List<String>, versionsFile: String) {
    val file = project.layout.settingsDirectory.file("gradle/$versionsFile")
    val entries =
      JsonSlurper().parseText(project.providers.fileContents(file).asText.get()) as List<*>
    check(entries.isNotEmpty()) { "gradle/$versionsFile lists no versions" }
    val android = project.plugins.hasPlugin("com.android.library")
    entries.forEach { entry ->
      val version = if (entry is Map<*, *>) entry["version"] as String else entry as String
      val pin = (entry as? Map<*, *>)?.get("pin") as Map<*, *>?
      val forced =
        groups.associateWith { version } +
          pin.orEmpty().entries.associate { (group, pinned) -> group as String to pinned as String }
      val variant = "$name${suffix(version)}"
      val test = if (android) androidUnitTest(variant) else jvmTest(variant)
      project.configurations
        .matching { it.name in testClasspathNames(variant, android) }
        .configureEach { force(forced) }
      project.tasks.register("${name}Test${suffix(version)}") {
        description = "Runs the unit tests with ${groups.joinToString()} at $version."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        dependsOn(test)
      }
    }
  }

  /**
   * A build type of [name] based on the test build type, whose unit tests compile and run on their
   * own classpaths. Android needs a variant per version, because AGP builds the R class and
   * resources the unit tests run with from the variant's dependencies.
   *
   * Revisit this once the modules can use AGP test suites: a test suite per version could replace
   * the extra build types.
   */
  private fun androidUnitTest(name: String): String {
    val android = project.extensions.getByType(LibraryExtension::class.java)
    val base = android.testBuildType
    android.buildTypes.create(name) {
      initWith(android.buildTypes.getByName(base))
      matchingFallbacks += base
    }
    val components = project.extensions.getByType(LibraryAndroidComponentsExtension::class.java)
    // AGP only generates unit tests for the test build type.
    components.beforeVariants(components.selector().withBuildType(name)) {
      (it as HasHostTestsBuilder).hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
    val unitTest = "test${name.replaceFirstChar(Char::uppercase)}UnitTest"
    // Keeps them out of `test` and `check`, which run the test build type's unit tests only, like
    // without a matrix. AGP creates `test` and makes it depend on every variant's unit tests after
    // the build script runs, so the dependency can only be removed in afterEvaluate.
    project.afterEvaluate {
      project.tasks.named("test") {
        setDependsOn(dependsOn.filterNot { it is TaskProvider<*> && it.name == unitTest })
      }
    }
    return unitTest
  }

  /**
   * A test task of [name] that reuses the compiled tests on its own runtime classpath, like
   * [SpringBootMatrixPlugin] does.
   */
  private fun jvmTest(name: String): TaskProvider<Test> {
    val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
    val test = sourceSets.getByName("test")
    val configurations = project.configurations
    val runtimeClasspath =
      configurations.resolvable(runtimeClasspathName(name, android = false)) {
        extendsFrom(
          configurations.getByName(test.implementationConfigurationName),
          configurations.getByName(test.runtimeOnlyConfigurationName),
        )
        attributes.addAllLater(
          configurations.getByName(test.runtimeClasspathConfigurationName).attributes
        )
      }
    val taskName = "test${name.replaceFirstChar(Char::uppercase)}"
    return project.tasks.register(taskName, Test::class.java) {
      testClassesDirs = test.output.classesDirs
      classpath = test.output + sourceSets.getByName("main").output + runtimeClasspath.get()
      useJUnit()
    }
  }

  private fun runtimeClasspathName(name: String, android: Boolean) =
    if (android) "${name}UnitTestRuntimeClasspath" else "${name}TestRuntimeClasspath"

  /**
   * On Android the tests also compile against the matrix versions: Kotlin compiler plugins such as
   * the Compose compiler emit calls into the library's runtime that older versions don't have.
   */
  private fun testClasspathNames(name: String, android: Boolean) =
    if (android) setOf(runtimeClasspathName(name, android), "${name}UnitTestCompileClasspath")
    else setOf(runtimeClasspathName(name, android))

  private fun Configuration.force(versions: Map<String, String>) {
    resolutionStrategy.eachDependency { versions[requested.group]?.let { useVersion(it) } }
  }

  private fun suffix(version: String) = version.replace('.', '_').replace('-', '_')
}
