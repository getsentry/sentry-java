package io.sentry.uitest.android

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.launchActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ProfilingTraceData
import io.sentry.Sentry
import io.sentry.android.core.AndroidLogger
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.assertEnvelopeTransaction
import io.sentry.protocol.SentryTransaction
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SdkInitTests : BaseUiTest() {

    @Test
    fun doubleInitDoesNotThrow() {
        initSentry(false) { options: SentryAndroidOptions ->
            options.tracesSampleRate = 1.0
            options.profilesSampleRate = 1.0
        }
        val transaction = Sentry.startTransaction("e2etests", "testInit")
        val sampleScenario = launchActivity<EmptyActivity>()
        initSentry(false) { options: SentryAndroidOptions ->
            options.tracesSampleRate = 1.0
            options.profilesSampleRate = 1.0
        }
        transaction.finish()
        sampleScenario.moveToState(Lifecycle.State.DESTROYED)
        val transaction2 = Sentry.startTransaction("e2etests", "testInit")
        transaction2.finish()
    }

    @Test
    fun doubleInitWithSameOptionsDoesNotThrow() {
        val options = SentryAndroidOptions()

        initSentry(true) {
            it.tracesSampleRate = 1.0
            it.profilesSampleRate = 1.0
            // We use the same executorService before and after closing the SDK
            it.executorService = options.executorService
            it.isDebug = true
        }
        val transaction = Sentry.startTransaction("e2etests", "testInit")
        val sampleScenario = launchActivity<EmptyActivity>()
        initSentry(true) {
            it.tracesSampleRate = 1.0
            it.profilesSampleRate = 1.0
            // We use the same executorService before and after closing the SDK
            it.executorService = options.executorService
            it.isDebug = true
        }
        relayIdlingResource.increment()
        transaction.finish()
        sampleScenario.moveToState(Lifecycle.State.DESTROYED)
        val transaction2 = Sentry.startTransaction("e2etests2", "testInit")
        transaction2.finish()

        relay.assert {
            findEnvelope {
                assertEnvelopeTransaction(it.items.toList(), AndroidLogger()).transaction == "e2etests2"
            }.assert {
                val transactionItem: SentryTransaction = it.assertTransaction()
                // Profiling uses executorService, so if the executorService is shutdown it would fail
                val profilingTraceData: ProfilingTraceData = it.assertProfile()
                it.assertNoOtherItems()
                assertEquals("e2etests2", transactionItem.transaction)
                assertEquals("e2etests2", profilingTraceData.transactionName)
            }
            assertNoOtherEnvelopes()
        }
    }

    @Test
    fun doubleInitDoesNotWait() {
        relayIdlingResource.increment()
        relayIdlingResource.increment()
        // Let's make the first request timeout
        relay.addResponse { MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) }

        initSentry(true) { options: SentryAndroidOptions ->
            options.tracesSampleRate = 1.0
        }

        Sentry.startTransaction("beforeRestart", "emptyTransaction").finish()

        // We want the SDK to start sending the event. If we don't wait, it's possible we don't send anything before the SDK is restarted
        Thread.sleep(500)

        val beforeRestart = System.currentTimeMillis()
        // We restart the SDK. This shouldn't block the main thread, but new options (e.g. profiling) should work
        initSentry(false) { options: SentryAndroidOptions ->
            // Registering again the idling resource blocks for some time. But we already registered it before.
            // So we just need to update the mock relay flag, as we are overwriting it in this "initSentry(false)".
            relay.waitForRequests = true
            options.tracesSampleRate = 1.0
            options.profilesSampleRate = 1.0
        }
        val afterRestart = System.currentTimeMillis()
        val restartMs = afterRestart - beforeRestart

        Sentry.startTransaction("afterRestart", "emptyTransaction").finish()
        // We assert for less than 1 second just to account for slow devices in saucelabs or headless emulator
        assertTrue(restartMs < 1000, "Expected less than 1000 ms for SDK restart. Got $restartMs ms")

        relay.assert {
            findEnvelope {
                assertEnvelopeTransaction(it.items.toList()).transaction == "beforeRestart"
            }.assert {
                it.assertTransaction()
                // No profiling item, as in the first init it was not enabled
                it.assertNoOtherItems()
            }
            findEnvelope {
                assertEnvelopeTransaction(it.items.toList()).transaction == "afterRestart"
            }.assert {
                it.assertTransaction()
                // There is a profiling item, as in the second init it was enabled
                it.assertProfile()
                it.assertNoOtherItems()
            }
            assertNoOtherEnvelopes()
        }
    }

    @Test
    fun initCloseInitWaits() {
        relayIdlingResource.increment()
        relayIdlingResource.increment()
        // Let's make the first request timeout
        relay.addResponse { MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) }

        initSentry(true) { options: SentryAndroidOptions ->
            options.tracesSampleRate = 1.0
            options.flushTimeoutMillis = 3000
        }

        Sentry.startTransaction("beforeRestart", "emptyTransaction").finish()

        // We want the SDK to start sending the event. If we don't wait, it's possible we don't send anything before the SDK is restarted
        Thread.sleep(500)

        val beforeRestart = System.currentTimeMillis()
        Sentry.close()
        // We stop the SDK. This should block the main thread. Then we start it again with new options
        initSentry(false) { options: SentryAndroidOptions ->
            // Registering again the idling resource blocks for some time. But we already registered it before.
            // So we just need to update the mock relay flag, as we are overwriting it in this "initSentry(false)".
            relay.waitForRequests = true
            options.tracesSampleRate = 1.0
            options.profilesSampleRate = 1.0
        }
        val afterRestart = System.currentTimeMillis()
        val restartMs = afterRestart - beforeRestart

        Sentry.startTransaction("afterRestart", "emptyTransaction").finish()
        assertTrue(restartMs > 3000, "Expected more than 3000 ms for SDK close and restart. Got $restartMs ms")

        relay.assert {
            findEnvelope {
                assertEnvelopeTransaction(it.items.toList()).transaction == "beforeRestart"
            }.assert {
                it.assertTransaction()
                // No profiling item, as in the first init it was not enabled
                it.assertNoOtherItems()
            }
            findEnvelope {
                assertEnvelopeTransaction(it.items.toList()).transaction == "afterRestart"
            }.assert {
                it.assertTransaction()
                // There is a profiling item, as in the second init it was enabled
                it.assertProfile()
                it.assertNoOtherItems()
            }
            assertNoOtherEnvelopes()
        }
    }
}
