package io.sentry.util.sql

import com.google.common.truth.Truth.assertThat
import io.sentry.JsonObjectReader
import io.sentry.vendor.gson.stream.JsonToken
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Runs every case in `sql/analyzer-cases.json` against [SqlQueryAnalyzer]. The case file is the
 * single owner of expected outputs; its format is described in
 * docs/plans/sanitize-sql-queries/01-test-suite.md, step 4.
 *
 * The loader rejects anything that would silently weaken a case: unknown or missing keys, a dialect
 * that falls back to `CONSERVATIVE` by typo, duplicate ids and unknown requirement ids.
 */
@RunWith(Parameterized::class)
class SqlQueryAnalyzerCasesTest(
  @Suppress("unused") private val id: String,
  private val case: Case,
) {

  @Test
  fun analyze() {
    val query = SqlQueryAnalyzer.analyze(case.input, case.dialect, case.maxQueryTextLength)

    assertThat(Fields(query.queryText, query.querySummary, query.storedProcedureName))
      .isEqualTo(case.expected)
  }

  data class Fields(val text: String?, val summary: String?, val storedProcedure: String?)

  class Case(
    val id: String,
    val dialect: SqlDialect,
    val input: String,
    val maxQueryTextLength: Int,
    val expected: Fields,
    /** Substrings that must never appear in any output field; asserted by the robustness tests. */
    val secrets: List<String>,
  )

  companion object {
    private const val RESOURCE = "sql/analyzer-cases.json"

    /** Used when a case sets no limit: larger than any input. */
    private const val UNLIMITED = Int.MAX_VALUE

    private val REQUIREMENTS = (1..11).map { "R$it" }.toSet()
    private val SOURCE = Regex("sentry|otel-java:\\w+#.+|otel-dotnet:[^#]+#.+")
    private val REQUIRED_KEYS =
      setOf("id", "requirements", "dialect", "input", "expected", "source")

    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun parameters(): List<Array<Any>> = loadCases().map { arrayOf(it.id, it) }

    fun loadCases(): List<Case> {
      val stream =
        checkNotNull(
          SqlQueryAnalyzerCasesTest::class.java.classLoader.getResourceAsStream(RESOURCE)
        ) {
          "$RESOURCE not found"
        }
      val cases = mutableListOf<Case>()
      JsonObjectReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
        reader.beginArray()
        while (reader.hasNext()) {
          val index = cases.size
          val case =
            try {
              readCase(reader)
            } catch (e: Exception) {
              throw IllegalStateException("$RESOURCE: case #$index is invalid: ${e.message}", e)
            }
          check(cases.none { it.id == case.id }) { "$RESOURCE: duplicate id '${case.id}'" }
          cases += case
        }
        reader.endArray()
      }
      check(cases.isNotEmpty()) { "$RESOURCE has no cases" }
      return cases
    }

    private fun readCase(reader: JsonObjectReader): Case {
      var id: String? = null
      var requirements: List<String>? = null
      var dialect: SqlDialect? = null
      var input: String? = null
      var maxQueryTextLength = UNLIMITED
      var expected: Fields? = null
      var secrets = emptyList<String>()
      val seen = mutableSetOf<String>()

      reader.beginObject()
      while (reader.hasNext()) {
        val key = reader.nextName()
        check(seen.add(key)) { "duplicate key '$key'" }
        when (key) {
          "id" -> id = readString(reader, key)
          "requirements" -> requirements = readStrings(reader, key)
          "dialect" -> dialect = toDialect(readString(reader, key))
          "input" -> input = readString(reader, key)
          "maxQueryTextLength" -> {
            check(reader.peek() == JsonToken.NUMBER) { "'$key' must be an integer" }
            maxQueryTextLength = reader.nextInt()
            check(maxQueryTextLength > 0) { "'$key' must be positive" }
          }
          "expected" -> expected = readExpected(reader)
          "secrets" -> secrets = readStrings(reader, key)
          "source" -> {
            val source = readString(reader, key)
            check(SOURCE.matches(source)) { "unknown source format '$source'" }
          }
          "note" -> readString(reader, key)
          else -> error("unknown key '$key'")
        }
      }
      reader.endObject()

      val missing = REQUIRED_KEYS - seen
      check(missing.isEmpty()) { "case '$id' misses $missing" }
      val requirementIds = requirements!!
      check(requirementIds.isNotEmpty()) { "case '$id' lists no requirements" }
      requirementIds.forEach {
        check(it in REQUIREMENTS) { "case '$id': unknown requirement '$it'" }
      }
      check(id!!.isNotEmpty()) { "id must not be empty" }
      return Case(id, dialect!!, input!!, maxQueryTextLength, expected!!, secrets)
    }

    /** `"CONSERVATIVE"` or a `db.system` that maps to a known dialect, so typos can't fall back. */
    private fun toDialect(value: String): SqlDialect {
      if (value == "CONSERVATIVE") return SqlDialect.CONSERVATIVE
      val dialect = SqlDialect.fromDbSystem(value)
      check(dialect !== SqlDialect.CONSERVATIVE) {
        "dialect '$value' is not a known db.system; use \"CONSERVATIVE\" for unknown systems"
      }
      return dialect
    }

    /** A missing key means the field is expected to be `null`. */
    private fun readExpected(reader: JsonObjectReader): Fields {
      var text: String? = null
      var summary: String? = null
      var storedProcedure: String? = null
      val seen = mutableSetOf<String>()
      reader.beginObject()
      while (reader.hasNext()) {
        val key = reader.nextName()
        check(seen.add(key)) { "duplicate key 'expected.$key'" }
        when (key) {
          "text" -> text = readString(reader, key)
          "summary" -> summary = readString(reader, key)
          "storedProcedure" -> storedProcedure = readString(reader, key)
          else -> error("unknown key 'expected.$key'")
        }
      }
      reader.endObject()
      return Fields(text, summary, storedProcedure)
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
  }
}
