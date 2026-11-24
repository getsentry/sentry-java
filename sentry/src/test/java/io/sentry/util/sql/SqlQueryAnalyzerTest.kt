package io.sentry.util.sql

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

/**
 * Contract checks that the shared case file (`sql/analyzer-cases.json`) cannot express. Expected
 * outputs for real input belong in that file, run by [SqlQueryAnalyzerCasesTest].
 */
class SqlQueryAnalyzerTest {

  @Test
  fun `null input returns all-null fields and is complete`() {
    val query = SqlQueryAnalyzer.analyze(null, SqlDialect.CONSERVATIVE, Int.MAX_VALUE)

    assertThat(query.queryText).isNull()
    assertThat(query.querySummary).isNull()
    assertThat(query.storedProcedureName).isNull()
    // Nothing to scan is not a failure: ERROR is reserved for internal errors (D27).
    assertThat(query.status).isEqualTo(SqlQuery.Status.COMPLETE)
  }

  @Test
  fun `input up to 1 MiB is analyzed`() {
    val sql = "SELECT 1" + " ".repeat(MAX_INPUT_LENGTH - "SELECT 1".length)

    val query = SqlQueryAnalyzer.analyze(sql, SqlDialect.CONSERVATIVE, Int.MAX_VALUE)

    assertThat(query.queryText).isEqualTo("SELECT ?")
    assertThat(query.status).isEqualTo(SqlQuery.Status.COMPLETE)
  }

  @Test
  fun `input over 1 MiB is skipped without any query data`() {
    // D66: a single huge literal is read to its end however small the output limit, so input over
    // the limit is not analyzed at all.
    val sql = "SELECT '" + "x".repeat(MAX_INPUT_LENGTH - "SELECT '".length) + "'"

    val query = SqlQueryAnalyzer.analyze(sql, SqlDialect.CONSERVATIVE, Int.MAX_VALUE)

    assertThat(query.queryText).isNull()
    assertThat(query.querySummary).isNull()
    assertThat(query.storedProcedureName).isNull()
    assertThat(query.status).isEqualTo(SqlQuery.Status.SKIPPED)
  }

  @Test
  fun `a bare CR after a line comment stops wherever it falls in the lexer buffer`() {
    // D64: the stop must not depend on whether the CR or the character after it is still in the
    // lexer's buffer, so the CR is moved across the default buffer size (2048) and a smaller one.
    for (opener in
      listOf("--" to SqlDialect.fromDbSystem("sqlite"), "#" to SqlDialect.fromDbSystem("mysql"))) {
      for (padding in 0..2100) {
        val sql = "SELECT a FROM t ${opener.first}" + "x".repeat(padding) + "\rZq9xK7xv"
        val query = SqlQueryAnalyzer.analyze(sql, opener.second, Int.MAX_VALUE)

        assertThat(query.queryText).isEqualTo("SELECT a FROM t ?")
        assertThat(query.status).isEqualTo(SqlQuery.Status.STOPPED)
      }
    }
  }

  private companion object {
    /** D66: 1 MiB, in characters. */
    const val MAX_INPUT_LENGTH = 1_048_576
  }
}
