package io.sentry.gradle

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader

/**
 * Fails the build if an archive still holds a MethodParameters entry without a name, the entries
 * [StripMethodParameters] removes. It reads the final jar and aar of a module, so it also covers
 * classes that no JavaCompile task of this build produced, e.g. repackaged ones.
 */
@CacheableTask
abstract class VerifyMethodParameters : DefaultTask() {

  /** The jar and aar files to read. An aar is opened down to its nested jars. */
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val archives: ConfigurableFileCollection

  @get:OutputFile abstract val report: RegularFileProperty

  @TaskAction
  fun verify() {
    val offenders = mutableListOf<String>()
    var classes = 0
    archives.files
      .filter { it.isFile }
      .forEach { archive ->
        archive.inputStream().use { stream ->
          classes += scan(ZipInputStream(stream), archive.name, offenders)
        }
      }

    val summary =
      if (offenders.isEmpty()) "$classes classes checked, no unnamed MethodParameters entry"
      else offenders.joinToString("\n")
    val reportFile = report.get().asFile
    reportFile.parentFile.mkdirs()
    reportFile.writeText(summary + "\n")

    if (offenders.isNotEmpty()) {
      throw GradleException(
        "${offenders.size} method(s) keep a MethodParameters entry without a name, which makes " +
          "the D8 of AGP 8.0 and older fail. See StripMethodParameters.\n$summary"
      )
    }
    logger.info("verifyMethodParameters: $summary")
  }

  /** Adds `archive!/entry method` for every offending method and returns the classes read. */
  private fun scan(zip: ZipInputStream, path: String, offenders: MutableList<String>): Int {
    var classes = 0
    while (true) {
      val entry = zip.nextEntry ?: break
      val name = entry.name
      when {
        name.endsWith(".class") -> {
          val reader = ClassReader(zip.readBytes())
          reader.methodsWithUnnamedParameters().forEach { offenders.add("$path!/$name $it") }
          classes++
        }
        name.endsWith(".jar") -> {
          val nested = ZipInputStream(ByteArrayInputStream(zip.readBytes()))
          classes += scan(nested, "$path!/$name", offenders)
        }
      }
    }
    return classes
  }
}
