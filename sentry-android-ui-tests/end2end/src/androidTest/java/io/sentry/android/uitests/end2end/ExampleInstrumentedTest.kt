package io.sentry.android.uitests.end2end

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.launchActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ProfilingTraceData
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.protocol.SentryTransaction
import okhttp3.mockwebserver.MockResponse

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest : BaseUiTest() {

    @Test
    fun useAppContext() {

        initSentry(true)
        relayIdlingResource.increment()
        Sentry.captureMessage("Message captured during test")

        relay.assert {
            assertEnvelope {
                val event = it.assertItem(SentryEvent::class.java)
                it.assertNoOtherItems()
                assertTrue(event.message?.formatted == "Message captured during test")
            }
            assertNoOtherEnvelopes()
            assertNoOtherRequests()
        }
    }

    @Test
    fun useAppContex2t() {

        initSentry(true) { options: SentryOptions ->
            options.isEnableAutoSessionTracking = false
            options.tracesSampleRate = 1.0
            options.isTraceSampling = true
            options.isProfilingEnabled = true
        }
        relayIdlingResource.increment()
        val transaction = Sentry.startTransaction("e2etests", "test1")

        transaction.finish()
        relay.assert {
            assertEnvelope {
                val transactionItem = it.assertItem(SentryTransaction::class.java)
                val profilingTraceData = it.assertItem(ProfilingTraceData::class.java)
                it.assertNoOtherItems()
                assertTrue(transactionItem.transaction == "e2etests")
                assertTrue(profilingTraceData.transactionId == transactionItem.eventId.toString())
                assertTrue(profilingTraceData.transactionName == "e2etests")
            }
            assertNoOtherEnvelopes()
            assertNoOtherRequests()
        }
    }
}
