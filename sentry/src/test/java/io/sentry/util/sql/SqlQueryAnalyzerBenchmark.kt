package io.sentry.util.sql

import java.util.Locale
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Measures [SqlQueryAnalyzer.analyze] over the case corpus and prints the median ns per call, once
 * with a warm cache and once with the cache bypassed (plan 01, step 7). It is a measurement, not a
 * contract: it asserts nothing and is skipped unless the `sentry.sql.benchmark` system property is
 * set:
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
 * passes keeps the cost of `System.nanoTime` out of the per-call figure.
 *
 * The cache is bypassed without a production seam: every input gets a unique ` /* <n> */` suffix,
 * which changes the cache key `(dialect, maxQueryTextLength, sql)`. Suffixed inputs are built
 * before a pass is timed. Every result feeds a printed checksum so that the JIT cannot drop the
 * calls.
 */
class SqlQueryAnalyzerBenchmark {

  @Test
  fun `median ns per analyze call, warm cache and cache bypassed`() {
    assumeTrue("set -D$PROPERTY to run the benchmark", System.getProperty(PROPERTY) != null)
    val cases = SqlQueryAnalyzerCasesTest.loadCases()

    val inputs = cases.map { it.input }
    report("warm", cases.size, measure(cases) { inputs })

    var suffix = 0L
    report("bypass", cases.size, measure(cases) { cases.map { "${it.input} /* ${suffix++} */" } })
  }

  private class Result(val warmupPasses: Int, val nsPerOp: DoubleArray, val checksum: Long)

  private fun measure(
    cases: List<SqlQueryAnalyzerCasesTest.Case>,
    inputsForPass: () -> List<String>,
  ): Result {
    val nsPerOp = DoubleArray(MEASURED_PASSES)
    var checksum = 0L
    val warmupEnd = System.nanoTime() + WARMUP_MILLIS * 1_000_000
    var warmupPasses = 0
    while (warmupPasses < MIN_WARMUP_PASSES || System.nanoTime() < warmupEnd) {
      checksum = checksum * 31 + runPass(cases, inputsForPass())
      warmupPasses++
    }
    for (pass in 0 until MEASURED_PASSES) {
      val inputs = inputsForPass()
      val start = System.nanoTime()
      checksum = checksum * 31 + runPass(cases, inputs)
      nsPerOp[pass] = (System.nanoTime() - start).toDouble() / cases.size
    }
    return Result(warmupPasses, nsPerOp, checksum)
  }

  private fun runPass(cases: List<SqlQueryAnalyzerCasesTest.Case>, inputs: List<String>): Long {
    var checksum = 0L
    for (i in cases.indices) {
      val case = cases[i]
      val query = SqlQueryAnalyzer.analyze(inputs[i], case.dialect, case.maxQueryTextLength)
      checksum =
        checksum * 31 + (query.queryText?.length ?: -1) + (query.querySummary?.length ?: -1)
    }
    return checksum
  }

  private fun report(mode: String, calls: Int, result: Result) {
    val sorted = result.nsPerOp.sortedArray()
    val mid = sorted.size / 2
    val median = if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    println(
      String.format(
        Locale.ROOT,
        "sql-benchmark mode=%s cases=%d warmup_passes=%d passes=%d median_ns_op=%.1f min=%.1f max=%.1f checksum=%d",
        mode,
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
    private const val WARMUP_MILLIS = 2_000L
    private const val MIN_WARMUP_PASSES = 5
    private const val MEASURED_PASSES = 20
  }
}
