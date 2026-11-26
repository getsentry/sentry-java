package io.sentry.util.sql

import com.google.common.truth.Truth.assertWithMessage
import kotlin.test.Test

/**
 * Owns the accepted leaks of the unchanged grammar (D69): a corpus case leaks when one of its
 * declared secrets appears whole in the text, summary or stored procedure of its full input. Every
 * leaking case must be listed in `sql/known-leaks.txt`, so a sync that adds a leak fails here. A
 * listed case that no longer leaks only prints a note: an upstream fix must not fail the build.
 * Prefixes are not analyzed: truncated input does not reach the analyzer (patch-inventory section
 * 5).
 */
class SqlQueryAnalyzerKnownLeakTest {

  @Test
  fun `only listed cases leak a declared secret`() {
    val known = knownLeaks()
    val leaking =
      SqlCorpus.cases
        .filter { it.secrets.isNotEmpty() }
        .filter { case ->
          val query =
            SqlQueryAnalyzer.analyze(case.input, case.dbSystemName, case.maxQueryTextLength)
          query.fields().values.any { value ->
            value != null && case.secrets.any { value.contains(it) }
          }
        }
        .map { it.id }
        .toSet()

    val fixed = known - leaking
    if (fixed.isNotEmpty()) {
      println(
        "${fixed.size} listed case(s) no longer leak; remove them from $RESOURCE:\n" +
          fixed.sorted().joinToString("\n"),
      )
    }
    assertWithMessage("cases that leak but are not listed in %s", RESOURCE)
      .that((leaking - known).sorted())
      .isEmpty()
  }

  private fun knownLeaks(): Set<String> {
    val stream =
      checkNotNull(javaClass.classLoader.getResourceAsStream(RESOURCE)) { "$RESOURCE not found" }
    return stream
      .bufferedReader()
      .readLines()
      .map { it.trim() }
      .filter { it.isNotEmpty() && !it.startsWith("#") }
      .toSet()
  }

  private companion object {
    const val RESOURCE = "sql/known-leaks.txt"
  }
}
