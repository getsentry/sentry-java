package io.sentry.uitest.android.benchmark

import android.content.Context
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import io.sentry.transport.NoOpTransport
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionApiBenchmarkTest {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        SentryAndroid.init(
            context
        ) { config ->
            config.dsn = "https://1053864c67cc410aa1ffc9701bd6f93d@o447951.ingest.sentry.io/5428559"
            config.isEnableAutoSessionTracking = false
            config.sampleRate = 1.0
            config.tracesSampleRate = 1.0
            config.profilesSampleRate = 0.0

            config.setTransportFactory { options, requestDetails -> NoOpTransport.getInstance() }
        }
    }

    @Test
    fun benchmarkTransactionStartFinishApi() {
        benchmarkRule.measureRepeated {
            val transaction = Sentry.startTransaction("name", "op")
            runWithTimingDisabled {
                constantWork()
            }
            transaction.finish()
        }
    }

    @Test
    fun benchmarkSpanStartFinishApi() {
        val transaction = Sentry.startTransaction("name", "op")

        benchmarkRule.measureRepeated {
            val span = transaction.startChild("child.op")
            runWithTimingDisabled {
                constantWork()
            }
            span.finish()
        }

        transaction.finish()
    }

    fun constantWork() = Thread.sleep(10)
}
