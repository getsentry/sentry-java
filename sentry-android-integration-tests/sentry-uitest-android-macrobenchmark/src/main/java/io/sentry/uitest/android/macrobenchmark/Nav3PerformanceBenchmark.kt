package io.sentry.uitest.android.macrobenchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.TraceMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.traceprocessor.TraceProcessor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import kotlin.math.ceil
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Repeatable A/B and stress benchmarks for the Nav3 sample's Performance tab. */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class Nav3PerformanceBenchmark {

  @get:Rule val benchmarkRule = MacrobenchmarkRule()

  @Test
  fun disabledTopReplacement() =
    measurePerformanceRun(
      preset = "DISABLED_CONTROL",
      run = "TOP_REPLACEMENTS",
      includeCostAttribution = false,
    )

  @Test
  fun topOnlyTopReplacement() = measurePerformanceRun(preset = "TOP_ONLY", run = "TOP_REPLACEMENTS")

  @Test
  fun lightTopReplacement() = measurePerformanceRun(preset = "LIGHT", run = "TOP_REPLACEMENTS")

  @Test
  fun normalTopReplacement() = measurePerformanceRun(preset = "NORMAL", run = "TOP_REPLACEMENTS")

  @Test
  fun captureOneOfHundredTopReplacement() =
    measurePerformanceRun(preset = "CAPTURE_1_OF_100", run = "TOP_REPLACEMENTS")

  @Test
  fun captureTwentyOfHundredTopReplacement() =
    measurePerformanceRun(preset = "CAPTURE_20_OF_100", run = "TOP_REPLACEMENTS")

  @Test
  fun captureHundredOfHundredTopReplacement() =
    measurePerformanceRun(preset = "CAPTURE_100_OF_100", run = "TOP_REPLACEMENTS")

  @Test
  fun heavyTopReplacement() = measurePerformanceRun(preset = "HEAVY", run = "TOP_REPLACEMENTS")

  @Test
  fun superHeavyTopReplacement() =
    measurePerformanceRun(preset = "SUPER_HEAVY", run = "TOP_REPLACEMENTS")

  @Test
  fun fullStackWithoutArgumentsTopReplacement() =
    measurePerformanceRun(preset = "NO_ARGUMENTS", run = "TOP_REPLACEMENTS")

  @Test
  fun superHeavyUnrelatedRecomposition() =
    measurePerformanceRun(
      preset = "SUPER_HEAVY",
      run = "UNRELATED_RECOMPOSITIONS",
      includeFrameTiming = false,
    )

  @Test
  fun interleavedAbComparison() {
    benchmarkRule.measureRepeated(
      packageName = TARGET_PACKAGE,
      metrics =
        listOf(
          Nav3CostAttributionMetric(NORMAL_AB_THRESHOLDS),
          Nav3AbComparisonMetric(INTERLEAVED_AB_EXPECTATIONS),
        ),
      compilationMode = CompilationMode.Full(),
      iterations = 5,
      setupBlock = {
        pressHome()
        startActivityAndWait(performanceIntent(preset = "NORMAL"))
        check(device.wait(Until.hasObject(By.text("Ready: Normal")), RUN_TIMEOUT_MILLIS)) {
          "Nav3 performance preset was not ready"
        }
      },
    ) {
      repeat(2) {
        startActivityAndWait(performanceIntent(run = "AB_COMPARISON", reuseActivity = true))
        check(device.wait(Until.hasObject(By.text("Complete")), RUN_TIMEOUT_MILLIS)) {
          "Nav3 A/B comparison did not complete"
        }
      }
    }
  }

  private fun measurePerformanceRun(
    preset: String,
    run: String,
    includeCostAttribution: Boolean = true,
    includeFrameTiming: Boolean = true,
  ) {
    val metrics = buildList {
      if (includeFrameTiming) {
        add(FrameTimingMetric())
      }
      if (includeCostAttribution) {
        add(Nav3CostAttributionMetric(regressionThresholds(preset, run)))
      }
    }
    benchmarkRule.measureRepeated(
      packageName = TARGET_PACKAGE,
      metrics = metrics,
      compilationMode = CompilationMode.Full(),
      iterations = 5,
      setupBlock = {
        pressHome()
        startActivityAndWait(performanceIntent(preset = preset))
        check(
          device.wait(Until.hasObject(By.text("Ready: ${presetLabel(preset)}")), RUN_TIMEOUT_MILLIS)
        ) {
          "Nav3 performance preset was not ready"
        }
        startActivityAndWait(performanceIntent(run = run, reuseActivity = true, warmUpOnly = true))
        check(device.wait(Until.hasObject(By.text("Warm-up complete")), RUN_TIMEOUT_MILLIS)) {
          "Nav3 performance warm-up did not complete"
        }
      },
    ) {
      startActivityAndWait(performanceIntent(run = run, reuseActivity = true, skipWarmUp = true))
      check(device.wait(Until.hasObject(By.text("Complete")), RUN_TIMEOUT_MILLIS)) {
        "Nav3 performance run did not complete"
      }
    }
  }

  private fun performanceIntent(
    preset: String? = null,
    run: String? = null,
    reuseActivity: Boolean = false,
    warmUpOnly: Boolean = false,
    skipWarmUp: Boolean = false,
  ): Intent =
    Intent().apply {
      component = ComponentName(TARGET_PACKAGE, BENCHMARK_ACTIVITY)
      flags =
        if (reuseActivity) {
          Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
        } else {
          Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
      preset?.let { putExtra(PERFORMANCE_PRESET_EXTRA, it) }
      run?.let { putExtra(PERFORMANCE_RUN_EXTRA, it) }
      putExtra(PERFORMANCE_WARM_UP_ONLY_EXTRA, warmUpOnly)
      putExtra(PERFORMANCE_SKIP_WARM_UP_EXTRA, skipWarmUp)
    }

  private fun presetLabel(preset: String): String =
    when (preset) {
      "DISABLED_CONTROL" -> "Disabled control"
      "TOP_ONLY" -> "Top only"
      "LIGHT" -> "Light"
      "NORMAL" -> "Normal"
      "CAPTURE_1_OF_100" -> "Capture 1 of 100"
      "CAPTURE_20_OF_100" -> "Capture 20 of 100"
      "CAPTURE_100_OF_100" -> "Capture 100 of 100"
      "HEAVY" -> "Heavy"
      "SUPER_HEAVY" -> "Super heavy"
      "NO_ARGUMENTS" -> "No arguments"
      else -> error("Unknown Nav3 performance preset: $preset")
    }

  private fun regressionThresholds(preset: String, run: String): Nav3RegressionThresholds {
    if (run == "UNRELATED_RECOMPOSITIONS") {
      return UNRELATED_RECOMPOSITION_THRESHOLDS
    }
    return PERFORMANCE_THRESHOLDS[preset] ?: error("No thresholds for Nav3 preset: $preset")
  }

  private class Nav3CostAttributionMetric(private val thresholds: Nav3RegressionThresholds) :
    TraceMetric() {
    override fun getMeasurements(
      captureInfo: Metric.CaptureInfo,
      traceSession: TraceProcessor.Session,
    ): List<Metric.Measurement> {
      val packageName = captureInfo.targetPackageName.replace("'", "''")
      val operations =
        traceSession
          .query(
            """
            WITH target_effects AS (
              SELECT slice.id, slice.dur
              FROM slice
              INNER JOIN thread_track ON slice.track_id = thread_track.id
              INNER JOIN thread USING (utid)
              INNER JOIN process USING (upid)
              WHERE process.name = '$packageName'
                AND slice.name = '$SENTRY_NAV_EFFECT_TRACE_SECTION'
                AND slice.dur > 0
            )
            SELECT
              effect.dur AS effect_dur_ns,
              COALESCE(SUM(extractor.dur), 0) AS extractor_dur_ns,
              COALESCE(
                SUM(CASE WHEN extractor.name = '$NAME_EXTRACTOR_TRACE_SECTION' THEN 1 ELSE 0 END),
                0
              ) AS captured_entry_count
            FROM target_effects AS effect
            LEFT JOIN slice AS extractor
              ON extractor.parent_id = effect.id
              AND extractor.name IN (
                '$NAME_EXTRACTOR_TRACE_SECTION',
                '$ARGUMENTS_EXTRACTOR_TRACE_SECTION'
              )
              AND extractor.dur > 0
            GROUP BY effect.id, effect.dur
            ORDER BY effect.id
            """
              .trimIndent()
          )
          .map { row ->
            val effectNanos = row.long("effect_dur_ns").toDouble()
            val extractorNanos = row.long("extractor_dur_ns").toDouble()
            Nav3OperationCost(
              effectNanos = effectNanos,
              extractorNanos = extractorNanos,
              capturedEntryCount = row.long("captured_entry_count").toDouble(),
            )
          }
          .toList()
      check(operations.isNotEmpty()) {
        "Nav3 benchmark did not record any $SENTRY_NAV_EFFECT_TRACE_SECTION slices. " +
          "The trace section may be missing or renamed."
      }

      val effectMillis = operations.map { it.effectNanos.nanosToMillis() }
      val extractorMillis = operations.map { it.extractorNanos.nanosToMillis() }
      val nonExtractorMillis = operations.map {
        (it.effectNanos - it.extractorNanos).coerceAtLeast(0.0).nanosToMillis()
      }
      val totalEffectNanos = operations.sumOf { it.effectNanos }
      val totalExtractorNanos = operations.sumOf { it.extractorNanos }
      val capturedEntryCount = operations.sumOf { it.capturedEntryCount }
      thresholds.verify(
        effectMillis = effectMillis,
        extractorMillis = extractorMillis,
        nonExtractorMillis = nonExtractorMillis,
        operationCount = operations.size,
        capturedEntryCount = capturedEntryCount.toInt(),
      )

      return buildList {
        add(Metric.Measurement("nav3IntegrationDurationMs", effectMillis))
        add(Metric.Measurement("nav3ExtractorDurationPerOperationMs", extractorMillis))
        add(Metric.Measurement("nav3NonExtractorDurationMs", nonExtractorMillis))
        add(Metric.Measurement("nav3IntegrationTotalMs", totalEffectNanos.nanosToMillis()))
        add(Metric.Measurement("nav3ExtractorTotalMs", totalExtractorNanos.nanosToMillis()))
        add(
          Metric.Measurement(
            "nav3NonExtractorTotalMs",
            (totalEffectNanos - totalExtractorNanos).coerceAtLeast(0.0).nanosToMillis(),
          )
        )
        add(Metric.Measurement("nav3IntegrationOperationCount", operations.size.toDouble()))
        add(Metric.Measurement("nav3CapturedEntryResolutionCount", capturedEntryCount))
        add(
          Metric.Measurement(
            "nav3IntegrationPerOperationMs",
            totalEffectNanos.nanosToMillis() / operations.size,
          )
        )
        if (capturedEntryCount > 0) {
          add(
            Metric.Measurement(
              "nav3IntegrationPerCapturedEntryUs",
              totalEffectNanos / capturedEntryCount / NANOS_PER_MICROSECOND,
            )
          )
        }
      }
    }
  }

  private data class Nav3OperationCost(
    val effectNanos: Double,
    val extractorNanos: Double,
    val capturedEntryCount: Double,
  )

  private data class Nav3RegressionThresholds(
    val expectedOperationCount: Int,
    val expectedCapturedEntryCount: Int,
    val maxIntegrationP90Ms: Double,
    val maxExtractorP90Ms: Double,
    val maxNonExtractorP90Ms: Double,
  ) {
    fun verify(
      effectMillis: List<Double>,
      extractorMillis: List<Double>,
      nonExtractorMillis: List<Double>,
      operationCount: Int,
      capturedEntryCount: Int,
    ) {
      check(operationCount == expectedOperationCount) {
        "Expected $expectedOperationCount Nav3 operations, measured $operationCount"
      }
      check(capturedEntryCount == expectedCapturedEntryCount) {
        "Expected $expectedCapturedEntryCount captured entries, measured $capturedEntryCount"
      }
      checkP90("integration", effectMillis, maxIntegrationP90Ms)
      checkP90("extractor", extractorMillis, maxExtractorP90Ms)
      checkP90("non-extractor", nonExtractorMillis, maxNonExtractorP90Ms)
    }

    private fun checkP90(label: String, values: List<Double>, maximumMs: Double) {
      val actualMs = values.percentile(90)
      check(actualMs <= maximumMs) {
        "Nav3 $label p90 was $actualMs ms, above the provisional $maximumMs ms threshold"
      }
    }
  }

  private class Nav3AbComparisonMetric(private val expectations: Nav3AbExpectations) :
    TraceMetric() {
    override fun getMeasurements(
      captureInfo: Metric.CaptureInfo,
      traceSession: TraceProcessor.Session,
    ): List<Metric.Measurement> {
      val packageName = captureInfo.targetPackageName.replace("'", "''")
      val operations =
        traceSession
          .query(
            """
            SELECT
              phase.name AS phase_name,
              operation.name AS operation_name,
              operation.dur AS duration_ns
            FROM slice AS phase
            INNER JOIN thread_track AS phase_track ON phase.track_id = phase_track.id
            INNER JOIN thread AS phase_thread ON phase_track.utid = phase_thread.utid
            INNER JOIN process AS phase_process ON phase_thread.upid = phase_process.upid
            INNER JOIN slice AS operation
              ON operation.ts >= phase.ts
              AND operation.ts + operation.dur <= phase.ts + phase.dur
            INNER JOIN thread_track AS operation_track ON operation.track_id = operation_track.id
            INNER JOIN thread AS operation_thread ON operation_track.utid = operation_thread.utid
            INNER JOIN process AS operation_process ON operation_thread.upid = operation_process.upid
            WHERE phase.name IN ('$AB_DISABLED_SECTION', '$AB_ENABLED_SECTION')
              AND operation.name IN (
                '$NAVIGATION_TO_COMPOSITION_SECTION',
                '$NAVIGATION_TO_FIRST_DRAW_SECTION'
              )
              AND phase_process.name = '$packageName'
              AND operation_process.name = '$packageName'
              AND phase.dur > 0
              AND operation.dur > 0
            ORDER BY operation.ts
            """
              .trimIndent()
          )
          .map { row ->
            AbOperationCost(
              phase = row.string("phase_name"),
              operation = row.string("operation_name"),
              durationMillis = row.long("duration_ns").toDouble().nanosToMillis(),
            )
          }
          .toList()

      val disabledComposition =
        operations.durations(AB_DISABLED_SECTION, NAVIGATION_TO_COMPOSITION_SECTION)
      val enabledComposition =
        operations.durations(AB_ENABLED_SECTION, NAVIGATION_TO_COMPOSITION_SECTION)
      val disabledFirstDraw =
        operations.durations(AB_DISABLED_SECTION, NAVIGATION_TO_FIRST_DRAW_SECTION)
      val enabledFirstDraw =
        operations.durations(AB_ENABLED_SECTION, NAVIGATION_TO_FIRST_DRAW_SECTION)
      expectations.verify(
        disabledComposition = disabledComposition,
        enabledComposition = enabledComposition,
        disabledFirstDraw = disabledFirstDraw,
        enabledFirstDraw = enabledFirstDraw,
      )

      return listOf(
        Metric.Measurement("nav3AbDisabledCompositionMs", disabledComposition),
        Metric.Measurement("nav3AbEnabledCompositionMs", enabledComposition),
        Metric.Measurement("nav3AbDisabledFirstDrawMs", disabledFirstDraw),
        Metric.Measurement("nav3AbEnabledFirstDrawMs", enabledFirstDraw),
        Metric.Measurement(
          "nav3AbCompositionDeltaMs",
          enabledComposition.average() - disabledComposition.average(),
        ),
        Metric.Measurement(
          "nav3AbFirstDrawDeltaMs",
          enabledFirstDraw.average() - disabledFirstDraw.average(),
        ),
      )
    }

    private fun List<AbOperationCost>.durations(
      phase: String,
      operation: String,
    ): List<Double> =
      filter { cost -> cost.phase == phase && cost.operation == operation }
        .map {
          it.durationMillis
        }
  }

  private data class AbOperationCost(
    val phase: String,
    val operation: String,
    val durationMillis: Double,
  )

  private data class Nav3AbExpectations(val expectedSamplesPerMeasurement: Int) {
    fun verify(
      disabledComposition: List<Double>,
      enabledComposition: List<Double>,
      disabledFirstDraw: List<Double>,
      enabledFirstDraw: List<Double>,
    ) {
      requireSamples("disabled A/B composition", disabledComposition, expectedSamplesPerMeasurement)
      requireSamples("enabled A/B composition", enabledComposition, expectedSamplesPerMeasurement)
      requireSamples("disabled A/B first draw", disabledFirstDraw, expectedSamplesPerMeasurement)
      requireSamples("enabled A/B first draw", enabledFirstDraw, expectedSamplesPerMeasurement)
    }

    private fun requireSamples(label: String, values: List<Double>, expectedCount: Int) {
      check(values.size == expectedCount) {
        "Nav3 benchmark expected $expectedCount $label samples, measured ${values.size}. " +
          "The async trace section may be missing, renamed, or incomplete."
      }
    }
  }

  private companion object {
    private const val TARGET_PACKAGE = "io.sentry.samples.android"
    private const val BENCHMARK_ACTIVITY =
      "$TARGET_PACKAGE.navigation.Nav3PerformanceBenchmarkActivity"
    private const val PERFORMANCE_PRESET_EXTRA = "nav3_performance_preset"
    private const val PERFORMANCE_RUN_EXTRA = "nav3_performance_run"
    private const val PERFORMANCE_WARM_UP_ONLY_EXTRA = "nav3_performance_warm_up_only"
    private const val PERFORMANCE_SKIP_WARM_UP_EXTRA = "nav3_performance_skip_warm_up"
    private const val SENTRY_NAV_EFFECT_TRACE_SECTION = "Nav3Stress.SentryNavEffect"
    private const val NAME_EXTRACTOR_TRACE_SECTION = "Nav3Stress.nameExtractor"
    private const val ARGUMENTS_EXTRACTOR_TRACE_SECTION = "Nav3Stress.argumentsExtractor"
    private const val NAVIGATION_TO_COMPOSITION_SECTION = "Nav3Stress.navigationToComposition"
    private const val NAVIGATION_TO_FIRST_DRAW_SECTION = "Nav3Stress.navigationToFirstDraw"
    private const val AB_DISABLED_SECTION = "Nav3Stress.ab.disabled"
    private const val AB_ENABLED_SECTION = "Nav3Stress.ab.enabled"
    private const val NANOS_PER_MICROSECOND = 1_000.0
    private const val RUN_TIMEOUT_MILLIS = 15_000L

    // TODO ADAM: Calibrate these provisional thresholds on physical devices, then update them.
    // Run release AOT benchmarks on at least one low/mid-tier device and one recent Pixel, record
    // model/build fingerprints, repeat interleaved runs at least three times, and account for the
    // observed coefficient of variation when choosing headroom. Keep exact operation/entry counts,
    // but replace emulator-derived latency limits before treating these as durable regression
    // gates.
    private val PERFORMANCE_THRESHOLDS =
      mapOf(
        "TOP_ONLY" to Nav3RegressionThresholds(20, 20, 0.5, 0.2, 0.5),
        "LIGHT" to Nav3RegressionThresholds(20, 20, 0.5, 0.2, 0.5),
        "NORMAL" to Nav3RegressionThresholds(20, 400, 0.75, 0.25, 0.75),
        "CAPTURE_1_OF_100" to Nav3RegressionThresholds(20, 20, 0.5, 0.2, 0.5),
        "CAPTURE_20_OF_100" to Nav3RegressionThresholds(20, 400, 0.75, 0.25, 0.75),
        "CAPTURE_100_OF_100" to Nav3RegressionThresholds(20, 2_000, 1.5, 0.75, 1.0),
        "HEAVY" to Nav3RegressionThresholds(20, 1_000, 1.0, 0.35, 0.75),
        "SUPER_HEAVY" to Nav3RegressionThresholds(20, 2_000, 3.0, 1.5, 2.0),
        "NO_ARGUMENTS" to Nav3RegressionThresholds(20, 2_000, 1.0, 0.5, 0.75),
      )
    private val UNRELATED_RECOMPOSITION_THRESHOLDS = Nav3RegressionThresholds(20, 0, 0.1, 0.01, 0.1)
    private val NORMAL_AB_THRESHOLDS = Nav3RegressionThresholds(40, 800, 0.75, 0.25, 0.75)
    private val INTERLEAVED_AB_EXPECTATIONS = Nav3AbExpectations(expectedSamplesPerMeasurement = 40)
  }
}

private fun Double.nanosToMillis(): Double = this / 1_000_000.0

private fun List<Double>.percentile(percentile: Int): Double {
  require(isNotEmpty())
  require(percentile in 0..100)
  val index = (ceil(percentile / 100.0 * size).toInt() - 1).coerceAtLeast(0)
  return sorted()[index]
}
