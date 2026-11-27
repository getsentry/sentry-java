package io.sentry.util.sql

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.sentry.vendor.otel.sql.OtelSqlSanitizer
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.mockito.Mockito
import org.mockito.kotlin.any

/**
 * Owns what [SqlQueryAnalyzer] adds around the vendored grammar: `null` and oversized input, the
 * length limit, the dialect choice and the error path (plan 08, Wrapper). The grammar's own output
 * is not asserted: a sync must not mean re-baselining. Where a test needs a fact about that output
 * (that a keyword or an identifier is kept), it checks the fact first as a precondition.
 */
class SqlQueryAnalyzerTest {

  @Test
  fun `null input gives no text, summary or stored procedure`() {
    val query = SqlQueryAnalyzer.analyze(null, "postgresql", LIMIT)

    assertThat(query.fields()).isEqualTo(NO_FIELDS)
  }

  @Test
  fun `input over 1 MiB is not analyzed`() {
    val sql = "SELECT a FROM t WHERE b = '" + "x".repeat(MIB) + "'"

    val query = SqlQueryAnalyzer.analyze(sql, "postgresql", LIMIT)

    assertThat(query.fields()).isEqualTo(NO_FIELDS)
  }

  @Test
  fun `input of exactly 1 MiB is analyzed`() {
    val prefix = "SELECT a FROM t WHERE b = '"
    val sql = prefix + "x".repeat(MIB - prefix.length - 1) + "'"
    assertThat(sql.length).isEqualTo(MIB)

    val query = SqlQueryAnalyzer.analyze(sql, "postgresql", LIMIT)

    assertThat(query.queryText).isNotNull()
  }

  @Test
  fun `text over the limit is cut at the last whitespace within the limit`() {
    val sql = "SELECT alpha, beta, gamma FROM delta WHERE id = ?"
    val full = fullText(sql)
    val from = full.indexOf(" FROM")
    assertWithMessage("precondition: the grammar keeps ' FROM' in %s", full).that(from).isAtLeast(0)

    // The limit falls inside FROM, so the space before it is the last whitespace within it.
    val query = SqlQueryAnalyzer.analyze(sql, null, from + 3)

    assertThat(query.queryText).isEqualTo(full.substring(0, from))
  }

  @Test
  fun `text without whitespace within the limit is cut at the limit without splitting a surrogate pair`() {
    // U+1D49C is a letter outside the BMP: two chars, one identifier character.
    val letter = "\uD835\uDC9C"
    val sql = letter.repeat(10)
    assertWithMessage("precondition: the grammar keeps the identifier")
      .that(fullText(sql))
      .isEqualTo(sql)

    // Limit 5 falls between the halves of the third letter, which is dropped whole.
    val query = SqlQueryAnalyzer.analyze(sql, null, 5)

    assertThat(query.queryText).isEqualTo(letter.repeat(2))
  }

  @Test
  fun `double quotes are identifiers only for databases that use them so`() {
    val sql = "SELECT \"Zq9xK7\" FROM t"

    // R8: upstream's forDbSystemName, e.g. PostgreSQL yes, MySQL and unknown databases no.
    assertThat(SqlQueryAnalyzer.analyze(sql, "postgresql", LIMIT).queryText).contains("Zq9xK7")
    assertThat(SqlQueryAnalyzer.analyze(sql, "mysql", LIMIT).queryText).doesNotContain("Zq9xK7")
    assertThat(SqlQueryAnalyzer.analyze(sql, null, LIMIT).queryText).doesNotContain("Zq9xK7")
  }

  @Test
  fun `runtime exception from the grammar gives no text, summary or stored procedure`() {
    Mockito.mockStatic(OtelSqlSanitizer::class.java).use { sanitizer ->
      sanitizer
        .`when`<Any> { OtelSqlSanitizer.sanitize(any(), any()) }
        .thenThrow(IllegalStateException("lexer bug"))

      val query = SqlQueryAnalyzer.analyze("SELECT 'Zq9xK7'", null, LIMIT)

      assertThat(query.fields()).isEqualTo(NO_FIELDS)
    }
  }

  @Test
  fun `error from the grammar propagates`() {
    Mockito.mockStatic(OtelSqlSanitizer::class.java).use { sanitizer ->
      sanitizer
        .`when`<Any> { OtelSqlSanitizer.sanitize(any(), any()) }
        .thenThrow(StackOverflowError())

      assertFailsWith<StackOverflowError> { SqlQueryAnalyzer.analyze("SELECT 1", null, LIMIT) }
    }
  }

  private fun SqlQuery.fields(): Map<String, String?> =
    mapOf(
      "text" to queryText,
      "summary" to querySummary,
      "storedProcedure" to storedProcedureName,
    )

  private fun fullText(sql: String): String =
    checkNotNull(SqlQueryAnalyzer.analyze(sql, null, Int.MAX_VALUE).queryText)

  private companion object {
    const val LIMIT = 32 * 1024
    const val MIB = 1024 * 1024
    val NO_FIELDS = mapOf("text" to null, "summary" to null, "storedProcedure" to null)
  }
}
