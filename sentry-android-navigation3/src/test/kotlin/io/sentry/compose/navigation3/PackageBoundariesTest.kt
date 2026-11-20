package io.sentry.compose.navigation3

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/** Enforces internal dependency rules for `sentry-android-navigation3`. */
class PackageBoundariesTest {

  private val sources: List<SourceFile> =
    File(SOURCE_ROOT)
      .walkTopDown()
      .filter { it.isFile && it.extension == "kt" }
      .map(SourceFile::parse)
      .toList()

  @Test
  fun `only the kotlinx package may import kotlinx serialization`() {
    val otherSources = sources.filterNot { it.packageName.isIn(KOTLINX) }

    val violations = otherSources.importsMatching { it.isIn("kotlinx.serialization") }

    assertThat(otherSources).isNotEmpty()
    assertThat(violations).isEmpty()
  }

  @Test
  fun `source files in the kotlinx package have names that start with Kotlinx`() {
    val kotlinxSources = sources.filter { it.packageName.isIn(KOTLINX) }

    val violations = kotlinxSources.filterNot { it.name.startsWith("Kotlinx") }.map { it.path }

    assertThat(kotlinxSources).isNotEmpty()
    assertThat(violations).isEmpty()
  }

  @Test
  fun `only the kotlinx package and the mapper factory may import the kotlinx package`() {
    val otherSources = sources.filterNot {
      it.packageName.isIn(KOTLINX) || it.path == MAPPER_FACTORY_PATH
    }

    val violations = otherSources.importsMatching { it.isIn(KOTLINX) }

    assertThat(otherSources).isNotEmpty()
    assertThat(violations).isEmpty()
  }

  @Test
  fun `the kotlinx package has no imports from Android, AndroidX, or the lifecycle package`() {
    val kotlinxSources = sources.filter { it.packageName.isIn(KOTLINX) }

    val violations = kotlinxSources.importsMatching {
      it.isIn("android") || it.isIn("androidx") || it.isIn(LIFECYCLE)
    }

    assertThat(kotlinxSources).isNotEmpty()
    assertThat(violations).isEmpty()
  }

  @Test
  fun `the lifecycle package has no imports from other Sentry navigation3 packages, Compose, or AndroidX Nav3`() {
    val lifecycleSources = sources.filter { it.packageName.isIn(LIFECYCLE) }

    val violations = lifecycleSources.importsMatching {
      (it.isIn(ROOT) && !it.isIn(LIFECYCLE)) ||
        it.isIn("androidx.navigation3") ||
        (it.isIn("androidx") && "compose" in it.split('.'))
    }

    assertThat(lifecycleSources).isNotEmpty()
    assertThat(violations).isEmpty()
  }

  // Check that the import reader finds the declarations the rules inspect.

  @Test
  fun `reads the package and the targets of plain, aliased, and wildcard imports`() {
    val file = File.createTempFile("Parsed", ".kt").apply { deleteOnExit() }
    file.writeText(
      """
      |@file:Suppress("unused")
      |
      |package com.example.feature
      |
      |import com.example.Plain
      |import com.example.wildcard.*
      |import com.example.Original as Alias
      |
      |class Parsed
      """
        .trimMargin(),
    )

    val parsed = SourceFile.parse(file)

    assertThat(parsed.packageName).isEqualTo("com.example.feature")
    assertThat(parsed.imports)
      .containsExactly("com.example.Plain", "com.example.wildcard", "com.example.Original")
      .inOrder()
  }

  @Test
  fun `reads imports from the module's Kotlin sources`() {
    val imports = sources.flatMap { it.imports }

    assertThat(imports).contains("androidx.lifecycle.LifecycleOwner")
  }

  /** A source file's location, package, and import targets. */
  private class SourceFile(
    val path: String,
    val name: String,
    val packageName: String,
    val imports: List<String>,
  ) {
    companion object {
      private val PACKAGE = Regex("""^package\s+([\w.]+)""")
      private val IMPORT = Regex("""^import\s+([\w.]+?)(?:\.\*)?(?:\s+as\s+\w+)?\s*;?\s*$""")

      fun parse(file: File): SourceFile {
        val lines = file.readLines().map(String::trim)
        return SourceFile(
          path = file.invariantSeparatorsPath,
          name = file.name,
          packageName =
            lines.firstNotNullOfOrNull { PACKAGE.find(it)?.groupValues?.get(1) }.orEmpty(),
          imports = lines.mapNotNull { IMPORT.find(it)?.groupValues?.get(1) },
        )
      }
    }
  }

  private companion object {
    const val SOURCE_ROOT = "src/main/kotlin"
    const val ROOT = "io.sentry.compose.navigation3"
    const val KOTLINX = "$ROOT.internal.backstack.kotlinx"
    const val LIFECYCLE = "$ROOT.internal.lifecycle"
    const val MAPPER_FACTORY_PATH =
      "$SOURCE_ROOT/io/sentry/compose/navigation3/internal/backstack/BuiltInBackStackEntryMappers.kt"

    /** Whether this name is [packageName] or lies inside it. */
    fun String.isIn(packageName: String): Boolean =
      this == packageName || startsWith("$packageName.")

    /** Each import matching [isForbidden], as `path: name`. */
    fun List<SourceFile>.importsMatching(isForbidden: (String) -> Boolean): List<String> =
      flatMap { file ->
        file.imports.filter(isForbidden).map { "${file.path}: $it" }
      }
  }
}
