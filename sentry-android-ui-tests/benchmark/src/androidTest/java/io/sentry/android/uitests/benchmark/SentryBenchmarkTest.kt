package io.sentry.android.uitests.benchmark

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import io.sentry.ITransaction
import io.sentry.Sentry
import io.sentry.android.uitests.benchmark.util.BenchmarkOperation
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.BeforeTest
import kotlin.test.assertTrue

/**
 * ## On Comparing Operations
 *
 * Originally [androidx.benchmark.junit4.BenchmarkRule] was used along with two tests: one to
 * benchmark an operation without Specto tracing, and another to measure the same operation with
 * Specto tracing. However when running this setup to compare the exact same operation showed that
 * the one that ran first always had significantly more frames dropped, so comparing two different
 * operations on equal terms was not possible.
 *
 * The current approach interweaves the two operations, running them in an alternating sequence.
 * When the two operations are the same, we get (nearly) identical results, as expected.
 */
@RunWith(AndroidJUnit4::class)
class SentryBenchmarkTest {

    private lateinit var runner: AndroidJUnitRunner
    private lateinit var context: Context

    @BeforeTest
    fun baseSetUp() {
        runner = InstrumentationRegistry.getInstrumentation() as AndroidJUnitRunner
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun benchmarkProfiledTransaction() {
        // runOnMainSync ensure that we're using Sentry on a thread that belongs to the actual
        // application, rather than the test application.
        // todo Right now we cannot enable profiling via code, but only via manifest.
        //  We should fix it and initialize via code, so that different tests can use different options.
        runner.runOnMainSync {
//            SentryAndroid.init(context) { options: SentryOptions ->
//                options.dsn = "https://12345678901234567890123456789012@fakeuri.ingest.sentry.io/1234567"
//                options.isEnableAutoSessionTracking = false
//                options.tracesSampleRate = 1.0
//                options.isTraceSampling = true
//                options.isProfilingEnabled = true
//            }
        }

        val benchmarkOperationNoTransaction = BenchmarkOperation(runner, getOperation(runner) { null })
        val benchmarkOperationProfiled = BenchmarkOperation(runner, getOperation(runner) {
            Sentry.startTransaction("Benchmark", "ProfiledTransaction")
        })
        repeat(2) {
            benchmarkOperationNoTransaction.warmup()
            benchmarkOperationProfiled.warmup()
        }
        repeat(10) {
            benchmarkOperationNoTransaction.iterate()
            benchmarkOperationProfiled.iterate()
        }

        val noTransactionResult = benchmarkOperationNoTransaction.getResult("NoTransaction")
        val profiledResult = benchmarkOperationProfiled.getResult("ProfiledTransaction")

        println("=====================================")
        println(noTransactionResult)
        println(profiledResult)
        println("=====================================")

        // We expect the profiled operation to be slower than the no transaction one, but not slower than 5%
        val comparisonResult = profiledResult.compare(noTransactionResult)
        assertTrue { comparisonResult.durationIncrease >= 0 }
        assertTrue { comparisonResult.durationIncrease < 5.0 }
        assertTrue { comparisonResult.cpuTimeIncrease >= 0 }
        assertTrue { comparisonResult.cpuTimeIncrease < 5.0 }
        assertTrue { comparisonResult.fpsDecrease >= 0 }
        assertTrue { comparisonResult.fpsDecrease < 5.0 }
        assertTrue { comparisonResult.droppedFramesIncrease >= 0 }
        assertTrue { comparisonResult.droppedFramesIncrease < 5.0 }
    }

    // Operation that will be compared
    private fun getOperation(runner: AndroidJUnitRunner, transactionBuilder: () -> ITransaction?): () -> Unit = {
        var transaction: ITransaction? = null
        // Launch the benchmark activity
        val benchmarkScenario = launchActivity<BenchmarkActivity>()
        // Starts a transaction (it can be null, but we still runOnMainSync to make operations as similar as possible)
        runner.runOnMainSync {
            transaction = transactionBuilder()
        }
        // Just swipe the list some times: this is the benchmarked operation
        repeat(2) {
            onView(withId(R.id.benchmark_transaction_list)).perform(swipeUp())
            Espresso.onIdle()
        }
        // We finish the transaction
        runner.runOnMainSync {
            transaction?.finish()
        }
        // We swipe a last time to measure how finishing the transaction may affect other operations
        onView(withId(R.id.benchmark_transaction_list)).perform(swipeUp())
        Espresso.onIdle()

        benchmarkScenario.moveToState(Lifecycle.State.DESTROYED)
    }
}
