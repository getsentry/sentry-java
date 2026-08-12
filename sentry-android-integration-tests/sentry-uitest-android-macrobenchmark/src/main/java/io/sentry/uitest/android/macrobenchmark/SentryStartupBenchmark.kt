package io.sentry.uitest.android.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-start benchmark for the sentry-samples-android app, used to evaluate SDK-init changes on a
 * real device in a stable, repeatable way.
 *
 * Reports two metrics per iteration:
 * - timeToInitialDisplay ([StartupTimingMetric]) — the whole app cold start from framework trace
 *   events. Because it captures the entire start, an SDK change has to be large enough (roughly
 *   tens of milliseconds) to show above cold-start noise.
 * - SentryAndroid.init ([TraceSectionMetric]) — the duration of the `SentryAndroid.init`
 *   [android.os.Trace] section the SDK emits, isolating SDK-init cost from the rest of the start.
 *
 * [CompilationMode.Full] pins ART AOT compilation so dexopt state does not drift between runs.
 * Iterations are capped at 12: on an unthrottled Pixel 3, back-to-back cold starts hit thermal
 * throttling after ~14 iterations, which inflates the tail of longer runs. This is NOT a CI test;
 * it requires a connected device. To A/B an SDK change, see README.md (build the app twice, once
 * per SDK variant, in interleaved rounds).
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class SentryStartupBenchmark {

  @get:Rule val benchmarkRule = MacrobenchmarkRule()

  @Test
  fun startupFullCompilation() =
    benchmarkRule.measureRepeated(
      packageName = TARGET_PACKAGE,
      metrics = listOf(StartupTimingMetric(), TraceSectionMetric(INIT_TRACE_SECTION)),
      compilationMode = CompilationMode.Full(),
      startupMode = StartupMode.COLD,
      iterations = 12,
      setupBlock = { pressHome() },
    ) {
      startActivityAndWait()
    }

  private companion object {
    const val TARGET_PACKAGE = "io.sentry.samples.android"

    // Matches the android.os.Trace section name in SentryAndroid.init.
    const val INIT_TRACE_SECTION = "SentryAndroid.init"
  }
}
