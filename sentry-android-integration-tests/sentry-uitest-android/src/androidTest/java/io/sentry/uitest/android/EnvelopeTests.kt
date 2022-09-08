package io.sentry.uitest.android

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ProfilingTraceData
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.protocol.SentryTransaction
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
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
            options.profilesSampleRate = 1.0
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
                assertTrue(profilingTraceData.environment.isNotEmpty())
                assertTrue(profilingTraceData.cpuArchitecture.isNotEmpty())
                assertTrue(profilingTraceData.transactions.isNotEmpty())
                // We should find the transaction id that started the profiling in the list of transactions
                val transactionData = profilingTraceData.transactions
                    .firstOrNull { t -> t.id == transactionItem.eventId.toString() }
                assertNotNull(transactionData)
            }
            assertNoOtherEnvelopes()
            assertNoOtherRequests()
        }
    }

    @Test
    fun checkEnvelopeConcurrentTransactions() {

        initSentry(true) { options: SentryOptions ->
            options.tracesSampleRate = 1.0
            options.profilesSampleRate = 1.0
        }
        relayIdlingResource.increment()
        relayIdlingResource.increment()
        relayIdlingResource.increment()

        val transaction = Sentry.startTransaction("e2etests", "test1")
        val transaction2 = Sentry.startTransaction("e2etests", "test2")
        val transaction3 = Sentry.startTransaction("e2etests", "test3")
        transaction.finish()
        transaction2.finish()
        transaction3.finish()

        relay.assert {
            assertEnvelope {
                val transactionItem: SentryTransaction = it.assertItem()
                it.assertNoOtherItems()
                assertEquals(transaction.eventId.toString(), transactionItem.eventId.toString())
            }
            assertEnvelope {
                val transactionItem: SentryTransaction = it.assertItem()
                it.assertNoOtherItems()
                assertEquals(transaction2.eventId.toString(), transactionItem.eventId.toString())
            }
            // The profile is sent only in the last transaction envelope
            assertEnvelope {
                val transactionItem: SentryTransaction = it.assertItem()
                val profilingTraceData: ProfilingTraceData = it.assertItem()
                it.assertNoOtherItems()
                assertEquals(transaction3.eventId.toString(), transactionItem.eventId.toString())
                assertEquals(profilingTraceData.transactionId, transactionItem.eventId.toString())
                assertTrue(profilingTraceData.transactionName == "e2etests")

                // Transaction timestamps should be all different from each other
                val transactions = profilingTraceData.transactions
                assertContains(transactions.map { t -> t.id }, transactionItem.eventId.toString())
                val startTimes = transactions.map { t -> t.relativeStartNs }
                val endTimes = transactions.mapNotNull { t -> t.relativeEndNs }
                val startCpuTimes = transactions.map { t -> t.relativeStartCpuMs }
                val endCpuTimes = transactions.mapNotNull { t -> t.relativeEndCpuMs }
                assertNotEquals(startTimes[0], startTimes[1])
                assertNotEquals(startTimes[0], startTimes[2])
                assertNotEquals(startTimes[1], startTimes[2])
                assertNotEquals(endTimes[0], endTimes[1])
                assertNotEquals(endTimes[0], endTimes[2])
                assertNotEquals(endTimes[1], endTimes[2])

                // The cpu timestamps shouldn't be all the same
                assertFalse(startCpuTimes[0] == startCpuTimes[1] && startCpuTimes[1] == startCpuTimes[2])
                assertFalse(endCpuTimes[0] == endCpuTimes[1] && endCpuTimes[1] == endCpuTimes[2])

                // The first and last transactions should be aligned to the start/stop of profile
                assertEquals(endTimes.maxOrNull()!! - startTimes.minOrNull()!!, profilingTraceData.durationNs.toLong())
            }
            assertNoOtherEnvelopes()
            assertNoOtherRequests()
        }
    }

    @Test
    fun sendProfiledTransaction() {
        // This is a dogfooding test
        IdlingRegistry.getInstance().register(ProfilingSampleActivity.scrollingIdlingResource)
        initSentry(false) { options: SentryOptions ->
            options.dsn = "https://640fae2f19ac4ba78ad740175f50195f@o1137848.ingest.sentry.io/6191083"
            options.tracesSampleRate = 1.0
            options.profilesSampleRate = 1.0
        }

        val transaction = Sentry.startTransaction("e2etests", "testProfile")
        val benchmarkScenario = launchActivity<ProfilingSampleActivity>()
        swipeList(10)
        benchmarkScenario.moveToState(Lifecycle.State.DESTROYED)
        transaction.finish()
        IdlingRegistry.getInstance().unregister(ProfilingSampleActivity.scrollingIdlingResource)
    }

    private fun swipeList(times: Int) {
        repeat(times) {
            Thread.sleep(100)
            Espresso.onView(ViewMatchers.withId(R.id.profiling_sample_list)).perform(ViewActions.swipeUp())
            Espresso.onIdle()
        }
    }
}
