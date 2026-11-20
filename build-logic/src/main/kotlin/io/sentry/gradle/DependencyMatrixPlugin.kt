package io.sentry.gradle

import com.android.build.api.variant.HasHostTests
import com.android.build.api.variant.HasHostTestsBuilder
import com.android.build.api.variant.HostTestBuilder
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import groovy.json.JsonSlurper
import javax.inject.Inject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * Runs a module's tests on each version of a dependency listed in a checked-in JSON file.
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

/**
 * A version from a versions file, and the version each dependency group is forced to for it.
 *
 * An entry in the file is either a version string or an object such as `{"version": "1.8.3",
 * "pin": {"androidx.compose.material3": "1.3.2"}}`. The version applies to every group of the
 * matrix, and `pin` forces each of its groups to its own version, for test dependencies whose
 * current version needs a newer version of the matrix's groups.
 */
internal class MatrixVersion(val version: String, val forced: Map<String, String>) {
  val suffix = version.replace('.', '_').replace('-', '_')
}

abstract class DependencyMatrixExtension @Inject constructor(private val project: Project) {
  /**
   * Runs an Android library module's unit tests with every dependency in [groups] at each version
   * in `gradle/<versionsFile>`, in `<name>Test<version>`, such as `composeUiTest1_13_0_rc01`.
   */
  fun androidUnitTests(name: String, groups: List<String>, versionsFile: String) {
    matrix(name, groups, versionsFile) { version ->
      listOf(androidUnitTest("$name${version.suffix}", version.forced))
    }
  }

  /**
   * Runs a JVM module's test suites with every dependency in [groups] at each version in
   * `gradle/<versionsFile>`. [suites] picks the suites; each runs in `<suite><Name><version>`, such
   * as `integrationTestSpringBoot3_2_12`, and `<name>Test<version>` runs all of a version's.
   */
  fun jvmTestSuites(
    name: String,
    groups: List<String>,
    versionsFile: String,
    suites: JvmTestSuitesMatrix.() -> Unit,
  ) {
    matrix(name, groups, versionsFile) { version ->
      JvmTestSuitesMatrix(project, name, version).apply(suites).tests
    }
  }

  /**
   * Registers `<name>Test<version>` for each version in `gradle/<versionsFile>`, running the test
   * tasks that [tests] returns for it, and `<name>Test` for every version. The versions run one at
   * a time: the configuration cache would run them all at once, and their test JVMs together ran
   * out of memory with 16 GB.
   */
  private fun matrix(
    name: String,
    groups: List<String>,
    versionsFile: String,
    tests: (MatrixVersion) -> List<String>,
  ) {
    val all =
      project.tasks.register("${name}Test") {
        description = "Runs the tests with every version in gradle/$versionsFile."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
      }
    var previous = emptyList<String>()
    versions(groups, versionsFile).forEach { version ->
      val current = tests(version)
      val before = previous
      project.tasks.named { it in current }.configureEach { mustRunAfter(before) }
      previous = current
      val versionTest =
        project.tasks.register("${name}Test${version.suffix}") {
          description = "Runs the tests with ${groups.joinToString()} at ${version.version}."
          group = LifecycleBasePlugin.VERIFICATION_GROUP
          dependsOn(current)
        }
      all.configure { dependsOn(versionTest) }
    }
  }

  private fun versions(groups: List<String>, versionsFile: String): List<MatrixVersion> {
    val file = project.layout.settingsDirectory.file("gradle/$versionsFile")
    val entries =
      JsonSlurper().parseText(project.providers.fileContents(file).asText.get()) as List<*>
    check(entries.isNotEmpty()) { "gradle/$versionsFile lists no versions" }
    return entries.map { entry ->
      val version = if (entry is Map<*, *>) entry["version"] as String else entry as String
      val pin = (entry as? Map<*, *>)?.get("pin") as Map<*, *>?
      val pinned = pin.orEmpty().entries.associate { (group, v) -> group as String to v as String }
      MatrixVersion(version, groups.associateWith { version } + pinned)
    }
  }

  /**
   * A build type of [name] based on the test build type, whose unit tests compile and run with the
   * [forced] versions. Android needs a variant per version, because AGP builds the R class and
   * resources the unit tests run with from the variant's dependencies. The tests also compile
   * against the [forced] versions: Kotlin compiler plugins such as the Compose compiler emit calls
   * into the library's runtime that older versions don't have.
   *
   * Revisit this once the modules can use AGP test suites: a test suite per version could replace
   * the extra build types.
   */
  private fun androidUnitTest(name: String, forced: Map<String, String>): String {
    val components = project.extensions.getByType(LibraryAndroidComponentsExtension::class.java)
    components.finalizeDsl { android ->
      val base = android.testBuildType
      android.buildTypes.create(name) {
        initWith(android.buildTypes.getByName(base))
        matchingFallbacks += base
      }
    }
    // AGP only generates unit tests for the test build type.
    components.beforeVariants(components.selector().withBuildType(name)) {
      (it as HasHostTestsBuilder).hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
    components.onVariants(components.selector().withBuildType(name)) {
      val unitTest = (it as HasHostTests).hostTests.getValue(HostTestBuilder.UNIT_TEST_TYPE)
      unitTest.compileConfiguration.force(forced)
      unitTest.runtimeConfiguration.force(forced)
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
}

/**
 * The test suites of a JVM module that run on [version]. The suites and the module compile once,
 * against the catalog's version, and each suite's tests run on a runtime classpath resolved for
 * [version].
 */
class JvmTestSuitesMatrix
internal constructor(
  private val project: Project,
  private val name: String,
  private val version: MatrixVersion,
) {
  internal val tests = mutableListOf<String>()
  private val suffix = name.replaceFirstChar(Char::uppercase) + version.suffix

  /**
   * Runs [suite]'s tests in `<testName><Name><version>`. [testName] tells apart several tasks of
   * one suite, and [configure] adds what the suite's own task has beyond its classes and classpath.
   */
  fun test(suite: String, testName: String = suite, configure: Test.() -> Unit = {}) {
    // Created now rather than with the task, so `dependencies --configuration` finds it.
    val runtimeClasspath = runtimeClasspath(suite)
    val task = "$testName$suffix"
    project.tasks.register(task, Test::class.java) {
      description = "Runs $testName on $name ${version.version}."
      group = LifecycleBasePlugin.VERIFICATION_GROUP
      testClassesDirs = sourceSet(suite).output.classesDirs
      classpath = runtimeClasspath
      useJUnit()
      configure()
    }
    tests += task
  }

  /** [sourceSet]'s output and runtime dependencies, with the matrix's groups at [version]. */
  fun runtimeClasspath(sourceSet: String): FileCollection {
    val sources = sourceSet(sourceSet)
    val configurationName = "$sourceSet${suffix}RuntimeClasspath"
    val configurations = project.configurations
    val dependencies =
      configurations.findByName(configurationName)
        ?: configurations
          .resolvable(configurationName) {
            extendsFrom(
              configurations.getByName(sources.implementationConfigurationName),
              configurations.getByName(sources.runtimeOnlyConfigurationName),
            )
            attributes.addAllLater(
              configurations.getByName(sources.runtimeClasspathConfigurationName).attributes
            )
            force(version.forced)
          }
          .get()
    return sources.output + dependencies
  }

  private fun sourceSet(name: String) =
    project.extensions.getByType(SourceSetContainer::class.java).getByName(name)
}

/**
 * Has every dependency in a forced group resolve to its version, as setting the version in the
 * catalog does.
 */
private fun Configuration.force(versions: Map<String, String>) {
  resolutionStrategy.eachDependency { versions[requested.group]?.let { useVersion(it) } }
}
