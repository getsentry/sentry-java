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

  companion object {
    private const val TARGET_PACKAGE = "io.sentry.samples.android"

    // Matches the android.os.Trace section name in SentryAndroid.init.
    private const val INIT_TRACE_SECTION = "SentryAndroid.init"
  }
}
