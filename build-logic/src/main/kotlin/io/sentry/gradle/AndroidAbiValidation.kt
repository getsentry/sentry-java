package io.sentry.gradle

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.VariantSelector
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
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
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

/**
 * The Kotlin Gradle plugin's ABI validation only covers the `org.jetbrains.kotlin.android` plugin
 * and the legacy KMP `androidTarget`. Neither AGP's built-in Kotlin nor the KMP Android library
 * plugin gets it, so their modules would silently stop checking their ABI. This dumps the classes
 * of one variant with the same ABI tools the Kotlin Gradle plugin uses, into the dump file it used
 * to write, and hooks the check and update into `checkKotlinAbi` and `updateKotlinAbi`.
 */
internal fun Project.validateAndroidAbi(
  extension: ApiValidationExtension,
  referenceDump: String,
  selectVariant: (VariantSelector) -> VariantSelector,
) {
  val abiTools =
    configurations.detachedConfiguration(
      dependencies.create("org.jetbrains.kotlin:abi-tools:${getKotlinPluginVersion()}")
    )
  val dump =
    tasks.register("dumpAndroidAbi", DumpAndroidAbi::class.java) {
      description = "Dumps the public ABI of the Android classes."
      classpath.from(abiTools)
      ignoredPackages.set(extension.ignoredPackages)
      dumpFile.set(layout.buildDirectory.file("kotlin/abi-android/${project.name}.api"))
    }
  extensions.getByType(AndroidComponentsExtension::class.java).onVariants(
    selectVariant(extensions.getByType(AndroidComponentsExtension::class.java).selector())
  ) { variant ->
    variant.artifacts
      .forScope(ScopedArtifacts.Scope.PROJECT)
      .use(dump)
      .toGet(ScopedArtifact.CLASSES, DumpAndroidAbi::classJars, DumpAndroidAbi::classDirs)
  }

  val check =
    tasks.register("checkAndroidAbi", CheckAndroidAbi::class.java) {
      description = "Checks the public ABI of the Android classes against $referenceDump."
      group = "verification"
      classpath.from(abiTools)
      actual.set(dump.flatMap { it.dumpFile })
      expected.set(layout.projectDirectory.file(referenceDump))
    }
  val update =
    tasks.register("updateAndroidAbi", UpdateAndroidAbi::class.java) {
      description = "Writes the public ABI of the Android classes to $referenceDump."
      actual.set(dump.flatMap { it.dumpFile })
      expected.set(layout.projectDirectory.file(referenceDump))
    }
  tasks.maybeRegister("checkKotlinAbi").configure { dependsOn(check) }
  tasks.maybeRegister("updateKotlinAbi").configure { dependsOn(update) }
  tasks.named("check").configure { dependsOn(check) }
}

private fun org.gradle.api.tasks.TaskContainer.maybeRegister(name: String) =
  if (name in names) named(name) else register(name)

@CacheableTask
abstract class DumpAndroidAbi @Inject constructor(private val workers: WorkerExecutor) :
  DefaultTask() {
  @get:Classpath abstract val classpath: ConfigurableFileCollection

  @get:Classpath abstract val classJars: ListProperty<RegularFile>

  @get:Classpath abstract val classDirs: ListProperty<Directory>

  @get:Input abstract val ignoredPackages: SetProperty<String>

  @get:OutputFile abstract val dumpFile: RegularFileProperty

  @TaskAction
  fun dump() {
    workers
      .classLoaderIsolation { classpath.from(this@DumpAndroidAbi.classpath) }
      .submit(DumpAbiAction::class.java) {
        classJars.from(this@DumpAndroidAbi.classJars)
        classDirs.from(this@DumpAndroidAbi.classDirs)
        excludedClasses.set(ignoredPackages.map { packages -> packages.map { "$it.**" }.toSet() })
        unpackDir.set(temporaryDir)
        dumpFile.set(this@DumpAndroidAbi.dumpFile)
      }
  }
}

@DisableCachingByDefault(because = "Comparing two small files is cheaper than caching it")
abstract class CheckAndroidAbi @Inject constructor(private val workers: WorkerExecutor) :
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
      .classLoaderIsolation { classpath.from(this@CheckAndroidAbi.classpath) }
      .submit(CheckAbiAction::class.java) {
        expected.set(expectedFile)
        actual.set(this@CheckAndroidAbi.actual)
      }
  }
}

@DisableCachingByDefault(because = "Copies one file into the source tree")
abstract class UpdateAndroidAbi : DefaultTask() {
  @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) abstract val actual: RegularFileProperty

  @get:OutputFile abstract val expected: RegularFileProperty

  @TaskAction
  fun update() {
    actual.get().asFile.copyTo(expected.get().asFile, overwrite = true)
  }
}

interface DumpAbiParameters : WorkParameters {
  val classJars: ConfigurableFileCollection
  val classDirs: ConfigurableFileCollection
  val excludedClasses: SetProperty<String>
  val unpackDir: DirectoryProperty
  val dumpFile: RegularFileProperty
}

/** The ABI tools read class files only, so jars are unpacked first. */
abstract class DumpAbiAction : WorkAction<DumpAbiParameters> {
  override fun execute() {
    val unpackDir = parameters.unpackDir.get().asFile
    unpackDir.deleteRecursively()
    parameters.classJars.forEachIndexed { index, jar -> unpack(jar, File(unpackDir, "$index")) }

    val roots = parameters.classDirs.files + unpackDir
    val classFiles =
      roots.flatMap { root -> root.walk().filter { it.isFile && it.extension == "class" } }
    val filters = AbiFilters(emptySet(), parameters.excludedClasses.get(), emptySet(), emptySet())
    parameters.dumpFile.get().asFile.bufferedWriter().use {
      AbiTools.getInstance().printJvmDump(it, classFiles, filters)
    }
  }

  private fun unpack(jar: File, into: File) {
    ZipFile(jar).use { zip ->
      zip
        .entries()
        .asSequence()
        .filter { !it.isDirectory && it.name.endsWith(".class") }
        .forEach { entry ->
          val target = File(into, entry.name)
          target.parentFile.mkdirs()
          zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
        }
    }
  }
}

interface CheckAbiParameters : WorkParameters {
  val expected: RegularFileProperty
  val actual: RegularFileProperty
}

abstract class CheckAbiAction : WorkAction<CheckAbiParameters> {
  override fun execute() {
    val expected = parameters.expected.get().asFile
    val actual = parameters.actual.get().asFile
    val diff = AbiTools.getInstance().filesDiff(expected, actual) ?: return
    throw GradleException(
      "The public ABI of ${expected.path} changed. Run updateKotlinAbi if that is intended.\n$diff"
    )
  }
}
