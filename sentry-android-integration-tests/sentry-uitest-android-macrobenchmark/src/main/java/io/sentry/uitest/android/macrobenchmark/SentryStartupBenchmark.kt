package io.sentry.uitest.android.macrobenchmark

import android.content.pm.PackageManager
import android.util.Log
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import org.junit.AfterClass
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** The two builds of the sample app a run can measure. */
enum class Variant(val packageName: String) {
  /**
   * The merge-base build, suffixed by `scripts/baseline-app-id.init.gradle` so both can coexist.
   */
  BASELINE("io.sentry.samples.android.baseline"),

  /** The build under test. The only variant present on a plain local run. */
  CANDIDATE("io.sentry.samples.android"),
}

/** One `measureRepeated` call: which build to launch, and where it sits in the run order. */
data class Step(private val index: Int, val variant: Variant) {
  // Becomes the JUnit parameter name, so results arrive as `startup[01-baseline]`. The index keeps
  // the run order visible in the raw data; the label is what parse-macrobenchmark-log.py groups on.
  override fun toString() =
    String.format(Locale.ROOT, "%02d-%s", index, variant.name.lowercase(Locale.ROOT))
}

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
 * When both builds are installed, the run alternates between them ([steps]) so that whatever the
 * device does over the session — thermal throttling above all — lands on both and cancels out in
 * the difference. Absolute numbers from an alternating run are therefore worse than from a short
 * single-build run; the *delta* is what this is for. When only [Variant.CANDIDATE] is installed,
 * the baseline steps are skipped and the run degrades to measuring one build.
 *
 * [CompilationMode.Full] pins ART AOT compilation so dexopt state does not drift between runs.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(Parameterized::class)
class SentryStartupBenchmark(private val step: Step) {

  @get:Rule val benchmarkRule = MacrobenchmarkRule()

  @Test
  fun startup() {
    // A local run normally has only the candidate installed. Skip rather than fail, so the run
    // still produces single-build numbers.
    assumeTrue(
      "${step.variant.packageName} is not installed",
      isInstalled(step.variant.packageName),
    )
    benchmarkRule.measureRepeated(
      packageName = step.variant.packageName,
      metrics = listOf(StartupTimingMetric(), TraceSectionMetric(INIT_TRACE_SECTION)),
      compilationMode = CompilationMode.Full(),
      startupMode = StartupMode.COLD,
      iterations = ITERATIONS_PER_STEP,
      setupBlock = { pressHome() },
    ) {
      startActivityAndWait()
    }
  }

  private fun isInstalled(packageName: String): Boolean =
    try {
      // Needs the <queries> entries in the module's AndroidManifest to see past package visibility.
      InstrumentationRegistry.getInstrumentation()
        .context
        .packageManager
        .getPackageInfo(packageName, 0)
      true
    } catch (e: PackageManager.NameNotFoundException) {
      false
    }

  // Not private: @Parameters and @AfterClass need public static methods.
  companion object {
    // Matches the android.os.Trace section name in SentryAndroid.init.
    private const val INIT_TRACE_SECTION = "SentryAndroid.init"

    /**
     * Cold starts per step. Kept small so the run alternates often, but not 1: every step pays for
     * its own `CompilationMode.Full` AOT compile of the target.
     */
    private const val ITERATIONS_PER_STEP = 3

    /** How many times [AB_ROUND] repeats. 2 rounds x 3 iterations = 12 cold starts per variant. */
    private const val ROUNDS = 2

    /**
     * ABBA rather than ABAB: in ABAB the candidate always follows the baseline, so any drift within
     * a pair is charged to the candidate every single time. Mirroring the second half of each round
     * makes each variant the trailing one equally often.
     */
    private val AB_ROUND =
      listOf(Variant.BASELINE, Variant.CANDIDATE, Variant.CANDIDATE, Variant.BASELINE)

    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun steps(): List<Step> =
      List(ROUNDS) { AB_ROUND }.flatten().mapIndexed { index, variant -> Step(index + 1, variant) }

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
     *
     * [Parameterized] runs this once, after the last step, so one dump covers every step. Were that
     * ever to change, the parser would still cope: the file only grows, and its later-chunk-wins
     * reassembly ends up with the most complete document in the log.
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
