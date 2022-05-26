package io.sentry.uitest.android.benchmark

import android.view.Choreographer
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso
import androidx.test.espresso.IdlingRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import io.sentry.uitest.android.benchmark.util.BenchmarkOperation
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SdkBenchmarkTest : BaseBenchmarkTest() {

    private lateinit var choreographer: Choreographer

    @BeforeTest
    fun setUp() {
        IdlingRegistry.getInstance().register(BenchmarkActivity.activityStartedIdlingResource)
        // Must run on the main thread to get the main thread choreographer.
        runner.runOnMainSync {
            choreographer = Choreographer.getInstance()
        }
    }

    @AfterTest
    fun cleanup() {
        IdlingRegistry.getInstance().unregister(BenchmarkActivity.activityStartedIdlingResource)
    }

    @Test
    fun benchmarkSdkInit() {

        // We compare starting an activity with and without the sdk init, to measure its impact on startup time.
        val opNoSdk = getOperation()
        val opSimpleSdk = getOperation {
            SentryAndroid.init(context) {
                it.dsn = "https://key@host/proj"
            }
        }
        val opNoSdk2 = getOperation()
        val opPerfProfilingSdk = getOperation {
            SentryAndroid.init(context) {
                it.dsn = "https://key@host/proj"
                it.isProfilingEnabled = true
                it.isEnableAutoActivityLifecycleTracing = true
                it.tracesSampleRate = 1.0
            }
        }
        val simpleSdkResult = BenchmarkOperation.compare(opNoSdk, "No Sdk", opSimpleSdk, "Simple Sdk")
        val perfProfilingSdkResult = BenchmarkOperation.compare(opNoSdk2, "No Sdk", opPerfProfilingSdk, "Sdk with perf and profiling")

        val threshold = TimeUnit.MILLISECONDS.toNanos(100)
        assertTrue(simpleSdkResult.durationIncreaseNanos in 0..threshold)
        assertTrue(simpleSdkResult.cpuTimeIncreaseMillis in 0..threshold)
        assertTrue(perfProfilingSdkResult.durationIncreaseNanos in simpleSdkResult.durationIncreaseNanos..threshold)
        assertTrue(perfProfilingSdkResult.cpuTimeIncreaseMillis in simpleSdkResult.cpuTimeIncreaseMillis..threshold)
    }

    private fun getOperation(init: (() -> Unit)? = null) = BenchmarkOperation(
        choreographer,
        before = { BenchmarkActivity.activityStartedIdlingResource.setIdle(false) },
        op = {
            runner.runOnMainSync {
                init?.invoke()
            }
            val benchmarkScenario = launchActivity<BenchmarkActivity>()
            Espresso.onIdle()
            benchmarkScenario.moveToState(Lifecycle.State.DESTROYED)
        },
        after = {
            Sentry.close()
        }
    )
}
