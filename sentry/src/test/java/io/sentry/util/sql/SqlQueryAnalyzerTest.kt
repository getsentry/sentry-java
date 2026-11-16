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
}
