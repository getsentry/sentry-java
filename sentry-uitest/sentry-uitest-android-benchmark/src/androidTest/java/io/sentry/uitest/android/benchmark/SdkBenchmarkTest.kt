package io.sentry.uitest.android.benchmark

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.launchActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import io.sentry.uitest.android.benchmark.util.BenchmarkOperation
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SdkBenchmarkTest : BaseBenchmarkTest() {

    @Test
    fun benchmarkSdkInit() {

        // We compare two operation that are the same. We expect the increases to be negligible, as the results
        // should be very similar.
        val op1 = BenchmarkOperation(runner) {
            runner.runOnMainSync {  }
            val benchmarkScenario = launchActivity<BenchmarkActivity>()
            benchmarkScenario.moveToState(Lifecycle.State.DESTROYED)
        }
        val op2 = BenchmarkOperation(
            runner,
            op = {
                runner.runOnMainSync {
                    SentryAndroid.init(context) {
                        it.dsn = "https://key@host/proj"
                    }
                }
                val benchmarkScenario = launchActivity<BenchmarkActivity>()
                benchmarkScenario.moveToState(Lifecycle.State.DESTROYED)
            },
            after = {
                Sentry.close()
            }
        )
        val comparisonResult = BenchmarkOperation.compare(op1, "Nothing", op2, "Init")

        assertTrue { comparisonResult.durationIncreaseNanos > 0 }
//        assertTrue { comparisonResult.durationIncrease < 1 }
        assertTrue { comparisonResult.cpuTimeIncreaseMillis > 0 }
//        assertTrue { comparisonResult.cpuTimeIncrease < 1 }
//        assertTrue { comparisonResult.fpsDecrease >= -2 }
//        assertTrue { comparisonResult.fpsDecrease < 2 }
//        assertTrue { comparisonResult.droppedFramesIncrease > 0 }
//        assertTrue { comparisonResult.droppedFramesIncrease < 1 }
    }

}
