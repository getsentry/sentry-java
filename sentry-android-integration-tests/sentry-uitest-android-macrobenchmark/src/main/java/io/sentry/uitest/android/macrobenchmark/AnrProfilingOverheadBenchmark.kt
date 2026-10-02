package io.sentry.uitest.android.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.traceprocessor.TraceProcessor
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Measures the overhead of AnrProfilingIntegration in the sample app's AnrProfilingOverheadActivity
 * for different polling intervals, with and without an animation on screen.
 *
 * Each iteration starts the activity, presses the button that re-initializes the SDK with a minimal
 * config, and waits until the 10 s measurement is done. [AnrProfilingOverheadMetric] then reads the
 * result from the Perfetto trace.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(Parameterized::class)
class AnrProfilingOverheadBenchmark(
  private val pollingIntervalMs: Long?,
  private val animate: Boolean,
) {

  @get:Rule val benchmarkRule = MacrobenchmarkRule()

  @Test
  fun overhead() =
    benchmarkRule.measureRepeated(
      packageName = TARGET_PACKAGE,
      metrics = listOf(AnrProfilingOverheadMetric()),
      compilationMode = CompilationMode.Full(),
      startupMode = StartupMode.COLD,
      iterations = 1,
      setupBlock = { pressHome() },
    ) {
      startActivityAndWait { intent ->
        intent.setClassName(TARGET_PACKAGE, TARGET_ACTIVITY)
        if (pollingIntervalMs == null) {
          intent.putExtra("mode", "off")
        } else {
          intent.putExtra("pollingIntervalMs", pollingIntervalMs)
          intent.putExtra("samplingIntervalMs", pollingIntervalMs)
        }
        intent.putExtra("animate", animate)
      }
      device.wait(Until.findObject(By.text(BUTTON_TEXT)), UI_TIMEOUT_MS).click()
      checkNotNull(device.wait(Until.findObject(By.text(STATUS_DONE)), MEASUREMENT_TIMEOUT_MS)) {
        "Measurement did not finish"
      }
    }

  companion object {
    private const val TARGET_PACKAGE = "io.sentry.samples.android"
    private const val TARGET_ACTIVITY = "io.sentry.samples.android.anr.AnrProfilingOverheadActivity"

    // Kept in sync with AnrProfilingOverheadActivity.
    private const val BUTTON_TEXT = "Run 10 s measurement"
    private const val STATUS_DONE = "Measurement done"

    private const val UI_TIMEOUT_MS = 5_000L
    private const val MEASUREMENT_TIMEOUT_MS = 20_000L

    private const val ROUNDS = 10

    /**
     * Alternates the configurations (with, without, with, ...) instead of running all iterations of
     * one configuration back to back, so slow drift such as thermal throttling affects both
     * equally. Each test is a single iteration; JUnit runs them in list order.
     *
     * null = integration off (baseline); 66 ms = the default polling interval.
     */
    @JvmStatic
    @Parameterized.Parameters(name = "{index}_polling={0}ms,animate={1}")
    fun parameters(): List<Array<Any?>> =
      (1..ROUNDS).flatMap { listOf(arrayOf<Any?>(66L, true), arrayOf<Any?>(null, true)) }
  }
}

/**
 * Reads the overhead numbers from the trace:
 * - `checkCount` / `checkSumMs` / `checkMaxMs`: number, total and longest wall time of
 *   `AnrProfiling.check` sections
 * - `capture*` / `write*`: count, total and longest wall time, and CPU time of the stack capture
 *   (`getStackTrace()`) and the stack write to the profile file
 * - `pollingThreadCpuMs`: CPU time of the polling thread, from the `AnrProfiling.cpuNs` counter
 * - `mainThreadRuns` / `mainThreadCpuMs`: scheduling slices of the main thread in the window
 * - `processCpuMs`: CPU time of all app threads in the window
 *
 * The window is the `AnrProfiling.measurement` async section.
 */
@OptIn(ExperimentalMetricApi::class)
private class AnrProfilingOverheadMetric : TraceMetric() {

  override fun getMeasurements(
    captureInfo: Metric.CaptureInfo,
    traceSession: TraceProcessor.Session,
  ): List<Metric.Measurement> {
    val pkg = captureInfo.targetPackageName
    val window =
      traceSession
        .query("SELECT ts, ts + dur AS end_ts FROM slice WHERE name = 'AnrProfiling.measurement'")
        .firstOrNull() ?: return emptyList()
    val start = window.long("ts")
    val end = window.long("end_ts")

    val checks =
      traceSession
        .query(
          """
          SELECT COUNT(*) AS count, IFNULL(SUM(dur), 0) AS sum, IFNULL(MAX(dur), 0) AS max
          FROM slice
          WHERE name = 'AnrProfiling.check' AND ts >= $start AND ts < $end
          """
        )
        .first()

    val capture = sectionStats(traceSession, "AnrProfiling.captureStack", start, end)
    val write = sectionStats(traceSession, "AnrProfiling.writeStack", start, end)

    val pollingCpuNs =
      traceSession
        .query(
          """
          SELECT c.value AS value
          FROM counter c JOIN process_counter_track t ON c.track_id = t.id
          WHERE t.name = 'AnrProfiling.cpuNs'
          ORDER BY c.ts DESC LIMIT 1
          """
        )
        .firstOrNull()
        ?.double("value") ?: 0.0

    val mainThread =
      traceSession
        .query(
          """
          SELECT COUNT(*) AS runs, IFNULL(SUM(s.dur), 0) AS cpu
          FROM sched s JOIN thread USING (utid) JOIN process USING (upid)
          WHERE process.name = '$pkg' AND thread.tid = process.pid
            AND s.ts >= $start AND s.ts < $end
          """
        )
        .first()

    val processCpuNs =
      traceSession
        .query(
          """
          SELECT IFNULL(SUM(s.dur), 0) AS cpu
          FROM sched s JOIN thread USING (utid) JOIN process USING (upid)
          WHERE process.name = '$pkg' AND s.ts >= $start AND s.ts < $end
          """
        )
        .first()
        .long("cpu")

    return listOf(
      Metric.Measurement("checkCount", checks.long("count").toDouble()),
      Metric.Measurement("checkSumMs", checks.long("sum") / NS_PER_MS),
      Metric.Measurement("checkMaxMs", checks.long("max") / NS_PER_MS),
      Metric.Measurement("captureCount", capture.long("count").toDouble()),
      Metric.Measurement("captureSumMs", capture.long("sum") / NS_PER_MS),
      Metric.Measurement("captureMaxMs", capture.long("max") / NS_PER_MS),
      Metric.Measurement("captureCpuMs", capture.long("cpu") / NS_PER_MS),
      Metric.Measurement("writeSumMs", write.long("sum") / NS_PER_MS),
      Metric.Measurement("writeMaxMs", write.long("max") / NS_PER_MS),
      Metric.Measurement("writeCpuMs", write.long("cpu") / NS_PER_MS),
      Metric.Measurement("pollingThreadCpuMs", pollingCpuNs / NS_PER_MS),
      Metric.Measurement("mainThreadRuns", mainThread.long("runs").toDouble()),
      Metric.Measurement("mainThreadCpuMs", mainThread.long("cpu") / NS_PER_MS),
      Metric.Measurement("processCpuMs", processCpuNs / NS_PER_MS),
    )
  }

  /**
   * Count, total and longest wall time of the [name] slices in the window, plus the CPU time the
   * slice's thread was actually scheduled while inside them.
   */
  private fun sectionStats(
    traceSession: TraceProcessor.Session,
    name: String,
    start: Long,
    end: Long,
  ) =
    traceSession
      .query(
        """
        SELECT
          COUNT(*) AS count,
          IFNULL(SUM(s.dur), 0) AS sum,
          IFNULL(MAX(s.dur), 0) AS max,
          IFNULL(SUM((
            SELECT SUM(MIN(sc.ts + sc.dur, s.ts + s.dur) - MAX(sc.ts, s.ts))
            FROM sched sc
            WHERE sc.utid = tt.utid AND sc.ts < s.ts + s.dur AND sc.ts + sc.dur > s.ts
          )), 0) AS cpu
        FROM slice s JOIN thread_track tt ON s.track_id = tt.id
        WHERE s.name = '$name' AND s.ts >= $start AND s.ts < $end
        """
      )
      .first()

  private companion object {
    const val NS_PER_MS = 1_000_000.0
  }
}
