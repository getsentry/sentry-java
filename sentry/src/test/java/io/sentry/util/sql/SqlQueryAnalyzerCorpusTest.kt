package io.sentry.util.sql

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Owns stability on the corpus inputs (`sql/analyzer-cases.json`), each under its own dialect and
 * limit: [SqlQueryAnalyzer.analyze] completes within [TIME_BOUND_MILLIS] on an
 * [AnalyzerWorker.STACK_SIZE_BYTES] stack, fails nowhere internally, respects the limit and splits
 * no surrogate pair. Outputs are not asserted (plan 08, Tests); leaks are owned by
 * [SqlQueryAnalyzerKnownLeakTest].
 */
@RunWith(Parameterized::class)
class SqlQueryAnalyzerCorpusTest(private val case: SqlCase) {

  @Test
  fun `completes within the bound without failing or exceeding the limit`() {
    val describe = { "case ${case.id} (${escape(case.input, 200)})" }
    val query =
      AnalyzerWorker().use {
        it.analyze(
          case.input,
          case.dbSystemName,
          case.maxQueryTextLength,
          TIME_BOUND_MILLIS,
          describe,
        )
      }

    assertHasText(query, describe)
    assertWithinLimit(query, case.maxQueryTextLength, describe)
    assertNoLoneSurrogate(case.input, query, describe)
  }

  companion object {
    /** Bound recorded in plan 01, step 6. */
    const val TIME_BOUND_MILLIS = 2_000L

    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun parameters(): List<Array<Any>> = SqlCorpus.cases.map { arrayOf(it) }
  }
}
