package io.sentry.util.sql

import java.util.Locale
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Measures [SqlQueryAnalyzer.analyze] over the case corpus and prints the median ns per call (plan
 * 01, step 7). The analyzer has no cache (D24), so every call is an uncached analysis and one mode
 * suffices (plan 01, step 11d). It runs twice: over all cases (`corpus=all`) and over the realistic
 * `workload.*` cases alone (`corpus=workload`, D19, plan 01 step 11c). It is a measurement, not a
 * contract: it only checks that the workload subset is not empty, and is skipped unless the
 * `sentry.sql.benchmark` system property is set:
 * ```
 * ./gradlew :sentry:test --tests 'io.sentry.util.sql.SqlQueryAnalyzerBenchmark' -Dsentry.sql.benchmark --rerun
 * ```
 *
 * The root build does not show test stdout on the console; the report lines are in the test's JUnit
 * XML under `sentry/build/test-results/test/`.
 *
 * Each mode runs untimed passes over the corpus on the calling thread for at least [WARMUP_MILLIS]
 * and [MIN_WARMUP_PASSES], so that analyzers of different speeds reach comparable JIT state, and
 * then [MEASURED_PASSES] timed passes. A pass's ns/op is its wall time divided by the number of
 * calls; the report gives the median, minimum and maximum over the timed passes. Timing whole
 * passes keeps the cost of `System.nanoTime` out of the per-call figure. Every result feeds a
 * printed checksum so that the JIT cannot drop the calls.
 */
class SqlQueryAnalyzerBenchmark {

  @Test
  fun `median ns per uncached analyze call, all cases and workload`() {
    assumeTrue("set -D$PROPERTY to run the benchmark", System.getProperty(PROPERTY) != null)
    val all = SqlQueryAnalyzerCasesTest.loadCases()
    val workload = all.filter { it.id.startsWith(WORKLOAD_PREFIX) }
    check(workload.isNotEmpty()) { "no case id starts with '$WORKLOAD_PREFIX'" }

    for ((corpus, cases) in listOf("all" to all, "workload" to workload)) {
      report(corpus, cases.size, measure(cases))
    }
  }

  private class Result(val warmupPasses: Int, val nsPerOp: DoubleArray, val checksum: Long)

  private fun measure(cases: List<SqlQueryAnalyzerCasesTest.Case>): Result {
    val nsPerOp = DoubleArray(MEASURED_PASSES)
    var checksum = 0L
    val warmupEnd = System.nanoTime() + WARMUP_MILLIS * 1_000_000
    var warmupPasses = 0
    while (warmupPasses < MIN_WARMUP_PASSES || System.nanoTime() < warmupEnd) {
      checksum = checksum * 31 + runPass(cases)
      warmupPasses++
    }
    for (pass in 0 until MEASURED_PASSES) {
      val start = System.nanoTime()
      checksum = checksum * 31 + runPass(cases)
      nsPerOp[pass] = (System.nanoTime() - start).toDouble() / cases.size
    }
    return Result(warmupPasses, nsPerOp, checksum)
  }

  private fun runPass(cases: List<SqlQueryAnalyzerCasesTest.Case>): Long {
    var checksum = 0L
    for (case in cases) {
      val query = SqlQueryAnalyzer.analyze(case.input, case.dialect, case.maxQueryTextLength)
      checksum =
        checksum * 31 + (query.queryText?.length ?: -1) + (query.querySummary?.length ?: -1)
    }
    return checksum
  }

  private fun report(corpus: String, calls: Int, result: Result) {
    val sorted = result.nsPerOp.sortedArray()
    val mid = sorted.size / 2
    val median = if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    println(
      String.format(
        Locale.ROOT,
        "sql-benchmark mode=uncached corpus=%s cases=%d warmup_passes=%d passes=%d median_ns_op=%.1f min=%.1f max=%.1f checksum=%d",
        corpus,
        calls,
        result.warmupPasses,
        sorted.size,
        median,
        sorted.first(),
        sorted.last(),
        result.checksum,
      )
    )
  }

  companion object {
    private const val PROPERTY = "sentry.sql.benchmark"
    private const val WORKLOAD_PREFIX = "workload."
    private const val WARMUP_MILLIS = 2_000L
    private const val MIN_WARMUP_PASSES = 5
    private const val MEASURED_PASSES = 20
  }
}
