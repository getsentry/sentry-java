package io.sentry.uitest.android.benchmark

import android.view.Choreographer
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.launchActivity
import androidx.test.espresso.IdlingRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import io.sentry.uitest.android.benchmark.util.BenchmarkOperation
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SdkBenchmarkTest : BaseBenchmarkTest() {

    private lateinit var choreographer: Choreographer

    @BeforeTest
    fun setUp() {
//        IdlingRegistry.getInstance().register(BenchmarkActivity.scrollingIdlingResource)
        // Must run on the main thread to get the main thread choreographer.
        runner.runOnMainSync {
            choreographer = Choreographer.getInstance()
        }
    }

    @AfterTest
    fun cleanup() {
//        IdlingRegistry.getInstance().unregister(BenchmarkActivity.scrollingIdlingResource)
    }

    @Test
    fun benchmarkSdkInit() {

        // We compare two operation that are the same. We expect the increases to be negligible, as the results
        // should be very similar.
        val op1 = BenchmarkOperation(choreographer) {
            runner.runOnMainSync {  }
            val benchmarkScenario = launchActivity<BenchmarkActivity>()
            benchmarkScenario.moveToState(Lifecycle.State.DESTROYED)
        }
        val op2 = BenchmarkOperation(
            choreographer,
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
