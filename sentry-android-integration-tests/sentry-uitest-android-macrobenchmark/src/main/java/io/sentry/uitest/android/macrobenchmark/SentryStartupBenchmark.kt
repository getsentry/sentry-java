package io.sentry.uitest.android.macrobenchmark

import android.util.Log
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.AfterClass
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

  // Not private: @AfterClass needs a public static method.
  companion object {
    private const val TARGET_PACKAGE = "io.sentry.samples.android"

    // Matches the android.os.Trace section name in SentryAndroid.init.
    private const val INIT_TRACE_SECTION = "SentryAndroid.init"

    private const val BENCHMARK_DATA_SUFFIX = "-benchmarkData.json"

    /** Kept in sync with `scripts/parse-macrobenchmark-log.py`. */
    private const val LOG_TAG = "SentryBenchmarkData"

    /** Well under logcat's ~4 KB per-message cap, so a chunk is never silently truncated. */
    private const val CHUNK_LENGTH = 2000

    /**
     * Echoes the benchmark results into logcat so CI can recover them.
     *
     * Macrobenchmark reports its numbers two ways, and on Sauce Labs neither one arrives: the
     * human-readable summary goes into the instrumentation status bundle (which only Studio and AGP
     * read), and `<pkg>-benchmarkData.json` is written to the app's external media dir, which Sauce
     * cannot pull — it only returns assets it produces itself, and logcat is one of them.
     *
     * Safe to run at this point because `ResultWriter` writes the file synchronously as each result
     * is appended; only its *reporting* is deferred to the end of the run. Locally this is just
     * extra logcat noise — Gradle still copies the real file into the build directory.
     */
    @JvmStatic
    @AfterClass
    fun logBenchmarkDataToLogcat() {
      val benchmarkData = findBenchmarkData()
      if (benchmarkData == null) {
        Log.w(LOG_TAG, "No *$BENCHMARK_DATA_SUFFIX found, cannot report results to CI")
        return
      }

      // Dropping the indentation makes the JSON compact enough to survive as a few logcat
      // messages. Only structural whitespace is affected: JSON forbids raw newlines inside
      // strings, so no value can span lines or start with the indentation being trimmed.
      val compactJson = benchmarkData.readText().lineSequence().joinToString("") { it.trimStart() }
      val chunks = compactJson.chunked(CHUNK_LENGTH)
      chunks.forEachIndexed { index, chunk ->
        Log.i(LOG_TAG, "[${index + 1}/${chunks.size}]$chunk")
      }
    }

    private fun findBenchmarkData(): File? {
      val context = InstrumentationRegistry.getInstrumentation().targetContext
      // This is where Macrobenchmark writes to for some reason.
      @Suppress("DEPRECATION") val mediaDirs = context.externalMediaDirs.toList()
      return (mediaDirs + context.externalCacheDir).filterNotNull().firstNotNullOfOrNull { dir ->
        dir.listFiles()?.firstOrNull { it.name.endsWith(BENCHMARK_DATA_SUFFIX) }
      }
    }
  }
}
