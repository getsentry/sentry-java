package io.sentry.uitest.android.benchmark

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.AndroidJUnitRunner
import io.sentry.ITransaction
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.uitest.android.benchmark.util.BenchmarkOperation
import org.junit.runner.RunWith
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SentryBenchmarkTest : BaseBenchmarkTest() {

    @BeforeTest
    fun setUp() {
        IdlingRegistry.getInstance().register(BenchmarkActivity.scrollingIdlingResource)
    }

    @AfterTest
    fun cleanup() {
        IdlingRegistry.getInstance().unregister(BenchmarkActivity.scrollingIdlingResource)
    }

    @Test
    fun benchmarkSameOperation() {

        // We compare two operation that are the same. We expect the increases to be negligible, as the results
        // should be very similar.
        val op1 = BenchmarkOperation(choreographer, op = getOperation(runner))
        val op2 = BenchmarkOperation(choreographer, op = getOperation(runner))
        val comparisonResult = BenchmarkOperation.compare(op1, "Op1", op2, "Op2")

        assertTrue(comparisonResult.durationIncreasePercentage in -1F..1F)
        assertTrue(comparisonResult.cpuTimeIncreasePercentage in -1F..1F)
        // The fps decrease comparison is skipped, due to approximation: 59.51 and 59.49 fps are considered 60 and 59,
        // respectively. Also, if the average fps is 20 or 60, a difference of 1 fps becomes 5% or 1.66% respectively.
        assertTrue(comparisonResult.droppedFramesIncreasePercentage in -1F..1F)
    }

    @Test
    fun benchmarkProfiledTransaction() {

        // We compare the same operation with and without profiled transaction.
        // We expect the profiled transaction operation to be slower, but not slower than 5%.
        val benchmarkOperationNoTransaction = BenchmarkOperation(choreographer, op = getOperation(runner))
        val benchmarkOperationProfiled = BenchmarkOperation(
            choreographer,
            before = {
                runner.runOnMainSync {
                    SentryAndroid.init(context) { options: SentryOptions ->
                        options.dsn = "https://key@uri/1234567"
                        options.tracesSampleRate = 1.0
                        options.isProfilingEnabled = true
                        options.isEnableAutoSessionTracking = false
                    }
                }
            },
            op = getOperation(runner) {
                Sentry.startTransaction("Benchmark", "ProfiledTransaction")
            },
            after = {
                runner.runOnMainSync {
                    Sentry.close()
                }
            }
        )
        val comparisonResult = BenchmarkOperation.compare(
            benchmarkOperationNoTransaction,
            "NoTransaction",
            benchmarkOperationProfiled,
            "ProfiledTransaction"
        )

        assertTrue(comparisonResult.durationIncreasePercentage in 0F..5F)
        assertTrue(comparisonResult.cpuTimeIncreasePercentage in 0F..5F)
        assertTrue(comparisonResult.fpsDecreasePercentage in 0F..5F)
        assertTrue(comparisonResult.droppedFramesIncreasePercentage in 0F..5F)
    }

    /**
     * Operation that will be compared: it launches [BenchmarkActivity], swipe the list and closes it.
     * The [transactionBuilder] is used to create the transaction before the swipes.
     */
    private fun getOperation(runner: AndroidJUnitRunner, transactionBuilder: () -> ITransaction? = { null }): () -> Unit = {
        var transaction: ITransaction? = null
        // Launch the sentry-uitest-android-benchmark activity
        val benchmarkScenario = launchActivity<BenchmarkActivity>()
        // Starts a transaction (it can be null, but we still runOnMainSync to make operations as similar as possible)
        runner.runOnMainSync {
            transaction = transactionBuilder()
        }
        // Just swipe the list some times: this is the benchmarked operation
        swipeList(2)
        // We finish the transaction
        runner.runOnMainSync {
            transaction?.finish()
        }
        // We swipe a last time to measure how finishing the transaction may affect other operations
        swipeList(1)

        benchmarkScenario.moveToState(Lifecycle.State.DESTROYED)
    }

    private fun swipeList(times: Int) {
        repeat(times) {
            Thread.sleep(100)
            onView(withId(R.id.benchmark_transaction_list)).perform(swipeUp())
            Espresso.onIdle()
        }
    }
}
