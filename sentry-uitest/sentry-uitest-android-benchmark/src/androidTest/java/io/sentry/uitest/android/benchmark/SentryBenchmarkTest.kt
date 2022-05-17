package io.sentry.uitest.android.benchmark

import android.content.Context
import android.view.Choreographer
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import io.sentry.ITransaction
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.uitest.android.benchmark.util.BenchmarkOperation
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SentryBenchmarkTest {

    private lateinit var runner: AndroidJUnitRunner
    private lateinit var context: Context
    private lateinit var choreographer: Choreographer

    @BeforeTest
    fun setUp() {
        runner = InstrumentationRegistry.getInstrumentation() as AndroidJUnitRunner
        context = ApplicationProvider.getApplicationContext()
        context.cacheDir.deleteRecursively()
        IdlingRegistry.getInstance().register(BenchmarkActivity.scrollingIdlingResource)
        // Must run on the main thread to get the main thread choreographer.
        runner.runOnMainSync {
            choreographer = Choreographer.getInstance()
        }
    }

    @AfterTest
    fun cleanup() {
        IdlingRegistry.getInstance().unregister(BenchmarkActivity.scrollingIdlingResource)
    }

    @Test
    fun benchmarkSameOperation() {

        // We compare two operation that are the same. We expect the increases to be negligible, as the results
        // should be very similar.
        val op1 = BenchmarkOperation(choreographer, getOperation(runner))
        val op2 = BenchmarkOperation(choreographer, getOperation(runner))
        val comparisonResult = BenchmarkOperation.compare(op1, "Op1", op2, "Op2")

        assertTrue { comparisonResult.durationIncrease in -1F..1F }
        assertTrue { comparisonResult.cpuTimeIncrease in -1F..1F }
        // The fps decrease is skipped for the moment, due to approximation:
        // if an operation runs at 59.49 fps and the other at 59.51, they are considered 59 and 60 fps respectively.
        // Their difference would be 1 / 60 * 100 = 1.66666%
        // On slow devices, a difference of 1 fps on an average of 20 fps would account for 5% decrease.
        // On even slower devices the difference would be even higher. Let's skip for now, as it's not important anyway.
//        assertTrue { comparisonResult.fpsDecrease in -2F..2F }
        assertTrue { comparisonResult.droppedFramesIncrease in -1F..1F }
    }

    @Test
    fun benchmarkProfiledTransaction() {

        runner.runOnMainSync {
            SentryAndroid.init(context) { options: SentryOptions ->
                options.dsn = "https://key@uri/1234567"
                options.isEnableAutoSessionTracking = false
                options.tracesSampleRate = 1.0
                options.isTraceSampling = true
                options.isProfilingEnabled = true
            }
        }

        // We compare the same operation with and without profiled transaction.
        // We expect the profiled transaction operation to be slower, but not slower than 5%.
        val benchmarkOperationNoTransaction = BenchmarkOperation(choreographer, getOperation(runner))
        val benchmarkOperationProfiled = BenchmarkOperation(
            choreographer,
            getOperation(runner) {
                Sentry.startTransaction("Benchmark", "ProfiledTransaction")
            }
        )
        val comparisonResult = BenchmarkOperation.compare(
            benchmarkOperationNoTransaction,
            "NoTransaction",
            benchmarkOperationProfiled,
            "ProfiledTransaction"
        )

        assertTrue { comparisonResult.durationIncrease in -1F..5F }
        assertTrue { comparisonResult.cpuTimeIncrease in -1F..5F }
        assertTrue { comparisonResult.fpsDecrease in -1F..5F }
        assertTrue { comparisonResult.droppedFramesIncrease in -1F..5F }
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
