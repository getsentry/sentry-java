package io.sentry.gradle

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileTreeElement
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.testing.Test
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Splits an Android library's unit tests into two tasks, so only the Robolectric tests pay for a
 * large heap and run in several forks:
 * - `test<Variant>UnitTest`, the AGP task, runs the Robolectric tests. Robolectric loads an
 *   android-all jar into each test JVM, which needs more heap than the default.
 * - `test<Variant>JvmUnitTest` runs the rest with the Gradle defaults.
 *
 * A Robolectric test is a class annotated with `@RunWith` and one of [ROBOLECTRIC_RUNNERS]. The
 * annotation is read from the class itself, not from its superclasses.
 *
 * With `--tests`, the AGP task runs every matching test, so the IDE can still run a plain JVM test.
 */
class RobolectricTestSplitPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.withPlugin("com.android.library") {
      // testBuildType is only final once the module's android block has run.
      project.afterEvaluate { split(project) }
    }
  }

  private fun split(project: Project) {
    val android = project.extensions.getByType(LibraryExtension::class.java)
    val variant = android.testBuildType!!.replaceFirstChar { it.uppercase() }
    val robolectricTestsName = "test${variant}UnitTest"

    project.tasks
      .withType(Test::class.java)
      .matching { it.name == robolectricTestsName }
      .configureEach {
        maxHeapSize = "2g"
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
        doFirst(ExcludeJvmTestsUnlessFiltered)
      }

    val jvmTests =
      project.tasks.register("test${variant}JvmUnitTest", Test::class.java) {
        description = "Runs the $variant unit tests that do not use Robolectric."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        val robolectricTests = project.tasks.named(robolectricTestsName, Test::class.java).get()
        testClassesDirs = robolectricTests.testClassesDirs
        classpath = robolectricTests.classpath
        useJUnit()
        exclude(RobolectricTestClass(robolectric = true))
      }

    project.tasks.matching { it.name == "test" }.configureEach { dependsOn(jvmTests) }
  }
}

private object ExcludeJvmTestsUnlessFiltered : Action<Task> {
  override fun execute(task: Task) {
    val test = task as Test
    // Gradle has no public getter for the `--tests` patterns.
    val commandLinePatterns = (test.filter as? DefaultTestFilter)?.commandLineIncludePatterns
    if (commandLinePatterns.isNullOrEmpty()) {
      test.exclude(RobolectricTestClass(robolectric = false))
    }
  }
}

private val ROBOLECTRIC_RUNNERS =
  setOf(
    "androidx/test/ext/junit/runners/AndroidJUnit4",
    "org/robolectric/RobolectricTestRunner",
    "org/robolectric/ParameterizedRobolectricTestRunner",
  )

/** Matches the class files of Robolectric tests, or with [robolectric] false, all others. */
private class RobolectricTestClass(private val robolectric: Boolean) : Spec<FileTreeElement> {
  override fun isSatisfiedBy(element: FileTreeElement): Boolean {
    // Never match a directory, which would exclude everything inside it.
    if (element.isDirectory || !element.name.endsWith(".class")) {
      return false
    }
    return runsWithRobolectric(element.file.readBytes()) == robolectric
  }

  private fun runsWithRobolectric(classFile: ByteArray): Boolean {
    var found = false
    val visitor =
      object : ClassVisitor(Opcodes.ASM9) {
        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
          if (descriptor != "Lorg/junit/runner/RunWith;") {
            return null
          }
          return object : AnnotationVisitor(Opcodes.ASM9) {
            override fun visit(name: String?, value: Any?) {
              if (value is Type && value.internalName in ROBOLECTRIC_RUNNERS) {
                found = true
              }
            }
          }
        }
      }
    ClassReader(classFile)
      .accept(visitor, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
    return found
  }
}
