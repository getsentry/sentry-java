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
