package io.sentry.android.uitests.end2end

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ProfilingTraceData
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.protocol.SentryTransaction
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class EnvelopeTests : BaseUiTest() {

    @Test
    fun checkEnvelopeCaptureMessage() {

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
    fun checkEnvelopeProfiledTransaction() {

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
                assertEquals(profilingTraceData.transactionId, transactionItem.eventId.toString())
                assertTrue(profilingTraceData.transactionName == "e2etests")
            }
            assertNoOtherEnvelopes()
            assertNoOtherRequests()
        }
    }
}
