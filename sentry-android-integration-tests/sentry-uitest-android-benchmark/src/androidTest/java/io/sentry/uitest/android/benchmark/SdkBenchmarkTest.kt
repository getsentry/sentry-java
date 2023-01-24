package io.sentry.uitest.android.benchmark

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.launchActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import io.sentry.uitest.android.benchmark.util.BenchmarkOperation
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SdkBenchmarkTest : BaseBenchmarkTest() {

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
                it.profilesSampleRate = 1.0
                it.tracesSampleRate = 1.0
            }
        }
        val refreshRate = BenchmarkActivity.refreshRate ?: 60F
        val simpleSdkResults = BenchmarkOperation.compare(opNoSdk, "No Sdk", opSimpleSdk, "Simple Sdk", refreshRate)
        val simpleSdkResult = simpleSdkResults.getSummaryResult()
        simpleSdkResult.printResults()
        val perfProfilingSdkResults = BenchmarkOperation.compare(opNoSdk2, "No Sdk", opPerfProfilingSdk, "Sdk with perf and profiling", refreshRate)
        val perfProfilingSdkResult = perfProfilingSdkResults.getSummaryResult()
        perfProfilingSdkResult.printResults()

        assertTrue(simpleSdkResult.cpuTimeIncreaseNanos in 0..TimeUnit.MILLISECONDS.toNanos(100))
        assertTrue(perfProfilingSdkResult.cpuTimeIncreaseNanos in 0..TimeUnit.MILLISECONDS.toNanos(100))
    }

    private fun getOperation(init: (() -> Unit)? = null) = BenchmarkOperation(
        choreographer,
        op = {
            runner.runOnMainSync {
                init?.invoke()
            }
            val benchmarkScenario = launchActivity<BenchmarkActivity>(
                activityOptions = Bundle().apply { putBoolean(BenchmarkActivity.EXTRA_SUSTAINED_PERFORMANCE_MODE, false) }
            )
            benchmarkScenario.moveToState(Lifecycle.State.DESTROYED)
        },
        after = {
            Sentry.close()
        }
    )
}
