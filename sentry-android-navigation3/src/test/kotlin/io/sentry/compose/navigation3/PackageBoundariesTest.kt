package io.sentry.compose.navigation3

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * Enforces internal dependency rules for `sentry-android-navigation3`.
 *
 * See `internal/backstack/README.md` and `internal/lifecycle/README.md`.
 */
class PackageBoundariesTest {

  private val sources: List<SourceFile> =
    File("src/main/kotlin")
      .walkTopDown()
      .filter { it.isFile && it.extension == "kt" }
      .map(SourceFile::parse)
      .toList()

  @Test
  fun `only the kotlinx package references kotlinx serialization`() {
    val otherSources = sources.filterNot { it.packageName.isIn(KOTLINX) }

    val violations = otherSources.dependenciesMatching { it.isIn("kotlinx.serialization") }

    assertThat(otherSources).isNotEmpty()
    assertThat(violations).isEmpty()
  }

  @Test
  fun `every source file in the kotlinx package has a Kotlinx file-name prefix`() {
    val kotlinxSources = sources.filter { it.packageName.isIn(KOTLINX) }

    val violations = kotlinxSources.filterNot { it.name.startsWith("Kotlinx") }.map { it.path }

    assertThat(kotlinxSources).isNotEmpty()
    assertThat(violations).isEmpty()
  }

  @Test
  fun `only the kotlinx package and the BuiltInBackStackEntryMappers source file reference the kotlinx package`() {
    val otherSources = sources.filterNot {
      it.packageName.isIn(KOTLINX) || it.name == "BuiltInBackStackEntryMappers.kt"
    }

    val violations = otherSources.dependenciesMatching { it.isIn(KOTLINX) }

    assertThat(otherSources).isNotEmpty()
    assertThat(violations).isEmpty()
  }

  @Test
  fun `the kotlinx package doesn't reference Android, AndroidX, or the lifecycle package`() {
    val kotlinxSources = sources.filter { it.packageName.isIn(KOTLINX) }

    val violations = kotlinxSources.dependenciesMatching {
      it.isIn("android") || it.isIn("androidx") || it.isIn(LIFECYCLE)
    }

    assertThat(kotlinxSources).isNotEmpty()
    assertThat(violations).isEmpty()
  }

  @Test
  fun `the lifecycle package doesn't reference other navigation3 packages, Compose, or Nav3`() {
    val lifecycleSources = sources.filter { it.packageName.isIn(LIFECYCLE) }

    val violations = lifecycleSources.dependenciesMatching {
      (it.isIn(ROOT) && !it.isIn(LIFECYCLE)) ||
        it.isIn("androidx.navigation3") ||
        (it.isIn("androidx") && "compose" in it.split('.'))
    }

    assertThat(lifecycleSources).isNotEmpty()
    assertThat(violations).isEmpty()
  }

  // The rules above pass vacuously if no imports or references are parsed, so we check the parser
  // itself.

  @Test
  fun `parses the package and imports of a source file`() {
    val file = File.createTempFile("Parsed", ".kt").apply { deleteOnExit() }
    file.writeText(
      """
      |@file:OptIn(ExperimentalSerializationApi::class)
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
  fun `parses fully qualified references from code, but not from comments, strings, or declarations`() {
    val file = File.createTempFile("Parsed", ".kt").apply { deleteOnExit() }
    file.writeText(
      """
      |package io.sentry.compose.navigation3.feature
      |
      |import kotlinx.serialization.Serializable
      |
      |/** See [kotlinx.serialization.descriptors.SerialDescriptor.serialName]. */
      |class Parsed(val serializer: kotlinx.serialization.KSerializer<*>) {
      |  // Not androidx.compose.runtime.Composable
      |  /* Not android.app.Activity */
      |  val text = "io.sentry.compose.navigation3.internal.lifecycle.NavScopes"
      |  val raw = ${"\"\"\""}kotlinx.serialization.json.Json${"\"\"\""}
      |  val quote = '"'
      |  val lifecycle = io.sentry.compose.navigation3.internal.lifecycle.NavScopes::class
      |}
      """
        .trimMargin(),
    )

    val parsed = SourceFile.parse(file)

    assertThat(parsed.references)
      .containsExactly(
        "kotlinx.serialization.KSerializer",
        "io.sentry.compose.navigation3.internal.lifecycle.NavScopes",
      )
      .inOrder()
  }

  @Test
  fun `parses imports from the main sources`() {
    val imports = sources.flatMap { it.imports }

    assertThat(imports).contains("kotlinx.serialization.KSerializer")
  }

  /**
   * A main source file's name, package, imported names (without aliases or wildcards), and fully
   * qualified [references] in code.
   */
  private class SourceFile(
    val path: String,
    val name: String,
    val packageName: String,
    val imports: List<String>,
    /**
     * Fully qualified names under `android`, `androidx`, `kotlinx`, or `io` used in code, outside
     * comments, strings, and the package and import declarations.
     */
    val references: List<String>,
  ) {
    /** The names this file depends on, whether imported or referenced in code. */
    val dependencies: List<String>
      get() = imports + references

    companion object {
      private val PACKAGE = Regex("""^package\s+([\w.]+)""")
      private val IMPORT = Regex("""^import\s+([\w.]+?)(?:\.\*)?(?:\s+as\s+\w+)?\s*;?\s*$""")
      private val NON_CODE =
        Regex(
          """//[^\n]*|/\*[\s\S]*?\*/|"{3}[\s\S]*?"{3}|"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'""",
        )
      private val REFERENCE = Regex("""(?<![\w.])(?:androidx?|kotlinx|io)(?:\.\w+)+""")

      fun parse(file: File): SourceFile {
        val text = file.readText()
        val lines = text.lines().map(String::trim)
        val code =
          NON_CODE.replace(text, " ").lines().filterNot {
            PACKAGE.containsMatchIn(it.trim()) || IMPORT.containsMatchIn(it.trim())
          }
        return SourceFile(
          path = file.path,
          name = file.name,
          packageName =
            lines.firstNotNullOfOrNull { PACKAGE.find(it)?.groupValues?.get(1) }.orEmpty(),
          imports = lines.mapNotNull { IMPORT.find(it)?.groupValues?.get(1) },
          references = code.flatMap { line -> REFERENCE.findAll(line).map { it.value } },
        )
      }
    }
  }

  private companion object {
    const val ROOT = "io.sentry.compose.navigation3"
    const val KOTLINX = "$ROOT.internal.backstack.kotlinx"
    const val LIFECYCLE = "$ROOT.internal.lifecycle"

    /** Whether this name is [packageName] or lies inside it. */
    fun String.isIn(packageName: String): Boolean =
      this == packageName || startsWith("$packageName.")

    /** Each import or reference matching [isForbidden], as `path: name`. */
    fun List<SourceFile>.dependenciesMatching(isForbidden: (String) -> Boolean): List<String> =
      flatMap { file ->
        file.dependencies.filter(isForbidden).map { "${file.path}: $it" }
      }
  }
}
