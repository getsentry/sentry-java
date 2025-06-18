package io.sentry.uitest.android.benchmark

import android.content.Context
import android.os.Bundle
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
import io.sentry.Sentry.OptionsConfiguration
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.test.applyTestOptions
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
        val refreshRate = BenchmarkActivity.refreshRate ?: 60F
        // since we benchmark the same operation, warmupIterations = 1 would effectively mean
        // 2 warmup runs which should be enough
        val comparisonResults =
            BenchmarkOperation.compare(
                op1,
                "Op1",
                op2,
                "Op2",
                refreshRate,
                warmupIterations = 1,
                measuredIterations = 10,
            )
        val comparisonResult = comparisonResults.getSummaryResult()
        comparisonResult.printResults()

        // Currently we just want to assert the cpu overhead
        assertTrue(
            comparisonResult.cpuTimeIncreasePercentage in -2F..2F,
            "Expected ${comparisonResult.cpuTimeIncreasePercentage} to be in range -2 < x < 2",
        )
        // The fps decrease comparison is skipped, due to approximation: 59.51 and 59.49 fps are considered 60 and 59,
        // respectively. Also, if the average fps is 20 or 60, a difference of 1 fps becomes 5% or 1.66% respectively.
    }

    @Test
    fun benchmarkProfiledTransaction() {
        // We compare the same operation with and without profiled transaction.
        // We expect the profiled transaction operation to be slower, but not slower than 5%.
        val benchmarkOperationNoTransaction = BenchmarkOperation(choreographer, op = getOperation(runner))
        val benchmarkOperationProfiled =
            BenchmarkOperation(
                choreographer,
                before = {
                    runner.runOnMainSync {
                        initForTest(context) { options: SentryOptions ->
                            options.dsn = "https://key@uri/1234567"
                            options.tracesSampleRate = 1.0
                            options.profilesSampleRate = 1.0
                            options.isEnableAutoSessionTracking = false
                        }
                    }
                },
                op =
                    getOperation(runner) {
                        Sentry.startTransaction("Benchmark", "ProfiledTransaction")
                    },
                after = {
                    runner.runOnMainSync {
                        Sentry.close()
                    }
                },
            )
        val refreshRate = BenchmarkActivity.refreshRate ?: 60F
        val comparisonResults =
            BenchmarkOperation.compare(
                benchmarkOperationNoTransaction,
                "NoTransaction",
                benchmarkOperationProfiled,
                "ProfiledTransaction",
                refreshRate,
            )
        comparisonResults.printAllRuns("Profiling Benchmark")
        val comparisonResult = comparisonResults.getSummaryResult()
        comparisonResult.printResults()

        // Currently we just want to assert the cpu overhead
        assertTrue(
            comparisonResult.cpuTimeIncreasePercentage in 0F..5F,
            "Expected ${comparisonResult.cpuTimeIncreasePercentage} to be in range 0 < x < 5",
        )
    }

    /**
     * Operation that will be compared: it launches [BenchmarkActivity], swipe the list and closes it.
     * The [transactionBuilder] is used to create the transaction before the swipes.
     */
    private fun getOperation(
        runner: AndroidJUnitRunner,
        transactionBuilder: () -> ITransaction? = { null },
    ): () -> Unit =
        {
            var transaction: ITransaction? = null
            // Launch the sentry-uitest-android-benchmark activity
            val benchmarkScenario =
                launchActivity<BenchmarkActivity>(
                    activityOptions = Bundle().apply { putBoolean(BenchmarkActivity.EXTRA_SUSTAINED_PERFORMANCE_MODE, true) },
                )
            // Starts a transaction (it can be null, but we still runOnMainSync to make operations as similar as possible)
            runner.runOnMainSync {
                transaction = transactionBuilder()
            }
            // Just swipe the list some times: this is the benchmarked operation
            swipeList(2)
            // We finish the transaction. We do it on main thread, so there's no need to perform other operations after it
            runner.runOnMainSync {
                transaction?.finish()
            }

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

fun initForTest(
    context: Context,
    optionsConfiguration: OptionsConfiguration<SentryAndroidOptions>,
) {
    SentryAndroid.init(context) {
        applyTestOptions(it)
        optionsConfiguration.configure(it)
    }
}
