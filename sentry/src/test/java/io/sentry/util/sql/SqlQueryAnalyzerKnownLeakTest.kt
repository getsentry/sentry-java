package io.sentry.util.sql

import com.google.common.truth.Truth.assertWithMessage
import io.sentry.JsonObjectReader
import io.sentry.vendor.gson.stream.JsonToken
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.test.Test

/**
 * Owns the accepted leaks of the unchanged grammar (D69): a corpus case (`sql/analyzer-cases.json`)
 * leaks when one of its declared secrets appears whole in the text, summary or stored procedure of
 * its full input. Every leaking case must be listed in `sql/known-leaks.txt`, so a sync that adds a
 * leak fails here. A listed case that no longer leaks only prints a note: an upstream fix must not
 * fail the build. Prefixes are not analyzed: truncated input does not reach the analyzer
 * (patch-inventory section 5).
 *
 * Cases are analyzed without a length limit: a cut can hide a leak that production limits (32 KiB
 * for JDBC, 8 KiB for Android SQLite) would not. The cut is owned by [SqlQueryAnalyzerTest].
 *
 * Every case must also give a text: `null` text for analyzed input means an internal failure (R5),
 * which would hide a leak. Other robustness of the grammar is upstream's to test (plan 08, Tests).
 */
class SqlQueryAnalyzerKnownLeakTest {

  @Test
  fun `only listed cases leak a declared secret`() {
    val known = readKnownLeaks()
    val failed = mutableListOf<String>()
    val leaking = mutableSetOf<String>()
    for (case in readCases()) {
      val query = SqlQueryAnalyzer.analyze(case.input, case.dbSystemName, Int.MAX_VALUE)
      if (query.queryText == null) failed += case.id
      val values = listOfNotNull(query.queryText, query.querySummary, query.storedProcedureName)
      if (values.any { value -> case.secrets.any { value.contains(it) } }) leaking += case.id
    }

    val fixed = known - leaking
    if (fixed.isNotEmpty()) {
      println(
        "${fixed.size} listed case(s) no longer leak; remove them from $KNOWN_LEAKS:\n" +
          fixed.sorted().joinToString("\n"),
      )
    }
    assertWithMessage("cases without text, so the analyzer failed internally (R5)")
      .that(failed)
      .isEmpty()
    assertWithMessage("cases that leak but are not listed in %s", KNOWN_LEAKS)
      .that((leaking - known).sorted())
      .isEmpty()
  }

  private class Case(
    val id: String,
    val dbSystemName: String?,
    val input: String,
    val secrets: List<String>,
  )

  private fun readKnownLeaks(): Set<String> =
    resource(KNOWN_LEAKS)
      .bufferedReader()
      .readLines()
      .map { it.trim() }
      .filter { it.isNotEmpty() && !it.startsWith("#") }
      .toSet()

  /**
   * Rejects anything that would silently weaken a case: unknown or missing keys, unknown dialects.
   */
  private fun readCases(): List<Case> {
    val cases = mutableListOf<Case>()
    JsonObjectReader(InputStreamReader(resource(CASES), StandardCharsets.UTF_8)).use { reader ->
      reader.beginObject()
      while (reader.hasNext()) {
        when (val key = reader.nextName()) {
          // The license header of the adapted upstream cases: JSON has no comments.
          "attribution" -> check(readStrings(reader, key).isNotEmpty())
          "cases" -> {
            reader.beginArray()
            while (reader.hasNext()) {
              val case = readCase(reader)
              check(cases.none { it.id == case.id }) { "$CASES: duplicate id '${case.id}'" }
              cases += case
            }
            reader.endArray()
          }
          else -> error("$CASES: unknown top-level key '$key'")
        }
      }
      reader.endObject()
    }
    check(cases.isNotEmpty()) { "$CASES has no cases" }
    return cases
  }

  private fun readCase(reader: JsonObjectReader): Case {
    val values = mutableMapOf<String, Any>()
    reader.beginObject()
    while (reader.hasNext()) {
      val key = reader.nextName()
      check(key !in values) { "$CASES: duplicate key '$key' in case ${values["id"]}" }
      values[key] =
        when (key) {
          "id",
          "dialect",
          "input",
          "source" -> readString(reader, key)
          "secrets" -> readStrings(reader, key)
          else -> error("$CASES: unknown key '$key' in case ${values["id"]}")
        }
    }
    reader.endObject()
    val id = values["id"] as String?
    check(values.keys == KEYS) { "$CASES: case '$id' misses ${KEYS - values.keys}" }
    check(SOURCE.matches(values["source"] as String)) { "$CASES: case '$id': bad source" }
    @Suppress("UNCHECKED_CAST") val secrets = values["secrets"] as List<String>
    check(secrets.isNotEmpty()) { "$CASES: case '$id' declares no secrets" }
    return Case(
      id!!,
      toDbSystemName(values["dialect"] as String),
      values["input"] as String,
      secrets,
    )
  }

  /**
   * The file names dialects by the `db.system` the integrations report; the analyzer takes the
   * semconv `db.system.name`, as [SqlSpanData] passes it. `CONSERVATIVE` is an unknown database. A
   * value with no mapping is a typo, not an unknown database.
   */
  private fun toDbSystemName(dialect: String): String? {
    if (dialect == "CONSERVATIVE") return null
    return checkNotNull(SqlSpanData.toDbSystemName(dialect)) { "unknown dialect '$dialect'" }
  }

  private fun readString(reader: JsonObjectReader, key: String): String {
    check(reader.peek() == JsonToken.STRING) { "'$key' must be a string" }
    return reader.nextString()
  }

  private fun readStrings(reader: JsonObjectReader, key: String): List<String> {
    val values = mutableListOf<String>()
    reader.beginArray()
    while (reader.hasNext()) values += readString(reader, key)
    reader.endArray()
    return values
  }

  private fun resource(name: String) =
    checkNotNull(javaClass.classLoader.getResourceAsStream(name)) { "$name not found" }

  private companion object {
    const val CASES = "sql/analyzer-cases.json"
    const val KNOWN_LEAKS = "sql/known-leaks.txt"
    val KEYS = setOf("id", "dialect", "input", "secrets", "source")
    val SOURCE = Regex("sentry|otel-java:\\w+#.+|otel-dotnet:[^#]+#.+")
  }
}
