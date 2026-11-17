package io.sentry.gradle

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import org.jetbrains.kotlin.abi.tools.AbiFilters
import org.jetbrains.kotlin.abi.tools.AbiTools
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

/**
 * The Kotlin Gradle plugin's ABI validation only dumps JVM and legacy `androidTarget` targets, so a
 * module on the KMP Android library plugin would silently stop checking its Android ABI. This dumps
 * that target with the same ABI tools the Kotlin Gradle plugin uses, into the same
 * `api/<target>/<module>.api` file, and hooks into `checkKotlinAbi` and `updateKotlinAbi`.
 */
internal fun Project.validateKmpAndroidAbi(extension: ApiValidationExtension) {
  val abiTools =
    configurations.detachedConfiguration(
      dependencies.create("org.jetbrains.kotlin:abi-tools:${getKotlinPluginVersion()}")
    )

  extensions
    .getByType(KotlinMultiplatformExtension::class.java)
    .targets
    .withType(KotlinMultiplatformAndroidLibraryTarget::class.java)
    .all {
      val targetName = name
      val capitalized = targetName.replaceFirstChar { it.uppercase() }
      val dump =
        tasks.register("dump${capitalized}KotlinAbi", DumpKmpAndroidAbi::class.java) {
          description = "Dumps the public ABI of the $targetName target."
          classpath.from(abiTools)
          classes.from(compilations.getByName("main").output.classesDirs)
          ignoredPackages.set(extension.ignoredPackages)
          dumpFile.set(layout.buildDirectory.file("kotlin/abi-$targetName/${project.name}.api"))
        }
      val referenceDump = layout.projectDirectory.file("api/$targetName/${project.name}.api")

      val check =
        tasks.register("check${capitalized}KotlinAbi", CheckKmpAndroidAbi::class.java) {
          description = "Checks the public ABI of the $targetName target against its dump."
          group = "verification"
          classpath.from(abiTools)
          actual.set(dump.flatMap { it.dumpFile })
          expected.set(referenceDump)
        }
      tasks.named("checkKotlinAbi").configure { dependsOn(check) }

      val update =
        tasks.register("update${capitalized}KotlinAbi", UpdateKmpAndroidAbi::class.java) {
          description = "Writes the public ABI of the $targetName target to its dump."
          actual.set(dump.flatMap { it.dumpFile })
          expected.set(referenceDump)
        }
      tasks.named("updateKotlinAbi").configure { dependsOn(update) }
    }
}

@CacheableTask
abstract class DumpKmpAndroidAbi @Inject constructor(private val workers: WorkerExecutor) :
  DefaultTask() {
  @get:Classpath abstract val classpath: ConfigurableFileCollection

  @get:Classpath abstract val classes: ConfigurableFileCollection

  @get:Input abstract val ignoredPackages: SetProperty<String>

  @get:OutputFile abstract val dumpFile: RegularFileProperty

  @TaskAction
  fun dump() {
    workers
      .classLoaderIsolation { classpath.from(this@DumpKmpAndroidAbi.classpath) }
      .submit(DumpAbiAction::class.java) {
        classes.from(this@DumpKmpAndroidAbi.classes)
        excludedClasses.set(ignoredPackages.map { packages -> packages.map { "$it.**" }.toSet() })
        dumpFile.set(this@DumpKmpAndroidAbi.dumpFile)
      }
  }
}

@DisableCachingByDefault(because = "Comparing two small files is cheaper than caching it")
abstract class CheckKmpAndroidAbi @Inject constructor(private val workers: WorkerExecutor) :
  DefaultTask() {
  @get:Classpath abstract val classpath: ConfigurableFileCollection

  @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) abstract val actual: RegularFileProperty

  // InputFiles rather than InputFile, so that a missing dump fails with the message below.
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val expected: RegularFileProperty

  @TaskAction
  fun check() {
    val expectedFile = expected.get().asFile
    if (!expectedFile.exists()) {
      throw GradleException(
        "The ABI dump ${expectedFile.path} is missing. Run updateKotlinAbi to create it."
      )
    }
    workers
      .classLoaderIsolation { classpath.from(this@CheckKmpAndroidAbi.classpath) }
      .submit(CheckAbiAction::class.java) {
        expected.set(expectedFile)
        actual.set(this@CheckKmpAndroidAbi.actual)
      }
  }
}

@DisableCachingByDefault(because = "Copies one file into the source tree")
abstract class UpdateKmpAndroidAbi : DefaultTask() {
  @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) abstract val actual: RegularFileProperty

  @get:OutputFile abstract val expected: RegularFileProperty

  @TaskAction
  fun update() {
    actual.get().asFile.copyTo(expected.get().asFile, overwrite = true)
  }
}

interface DumpAbiParameters : WorkParameters {
  val classes: ConfigurableFileCollection
  val excludedClasses: SetProperty<String>
  val dumpFile: RegularFileProperty
}

abstract class DumpAbiAction : WorkAction<DumpAbiParameters> {
  override fun execute() {
    val filters =
      AbiFilters(emptySet(), parameters.excludedClasses.get(), emptySet(), emptySet())
    val classFiles = parameters.classes.asFileTree.matching { include("**/*.class") }.files
    parameters.dumpFile.get().asFile.bufferedWriter().use {
      AbiTools.getInstance().printJvmDump(it, classFiles, filters)
    }
  }
}

interface CheckAbiParameters : WorkParameters {
  val expected: RegularFileProperty
  val actual: RegularFileProperty
}

abstract class CheckAbiAction : WorkAction<CheckAbiParameters> {
  override fun execute() {
    val expected: File = parameters.expected.get().asFile
    val actual: File = parameters.actual.get().asFile
    val diff = AbiTools.getInstance().filesDiff(expected, actual) ?: return
    throw GradleException(
      "The public ABI of ${expected.path} changed. Run updateKotlinAbi if that is intended.\n$diff"
    )
  }
}
