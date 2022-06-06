package io.sentry.uitest.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ProfilingTraceData
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.protocol.SentryTransaction
import org.junit.runner.RunWith
import kotlin.test.Test
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
                val event: SentryEvent = it.assertItem()
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
            options.tracesSampleRate = 1.0
            options.isProfilingEnabled = true
        }
        relayIdlingResource.increment()
        val transaction = Sentry.startTransaction("e2etests", "test1")

        transaction.finish()
        relay.assert {
            assertEnvelope {
                val transactionItem: SentryTransaction = it.assertItem()
                val profilingTraceData: ProfilingTraceData = it.assertItem()
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
