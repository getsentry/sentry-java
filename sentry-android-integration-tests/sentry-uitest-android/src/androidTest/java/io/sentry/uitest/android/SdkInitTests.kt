package io.sentry.uitest.android

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.launchActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ProfilingTraceData
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.assertEnvelopeTransaction
import io.sentry.protocol.SentryTransaction
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals

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
                assertEnvelopeTransaction(it.items.toList()).transaction == "e2etests2"
            }.assert {
                val transactionItem: SentryTransaction = it.assertTransaction()
                // Profiling uses executorService, so if the executorService is shutdown it would fail
                val profilingTraceData: ProfilingTraceData = it.assertProfile()
                it.assertNoOtherItems()
                assertEquals("e2etests2", transactionItem.transaction)
                assertEquals("e2etests2", profilingTraceData.transactionName)
            }
            assertNoOtherEnvelopes()
            assertNoOtherRequests()
        }
    }
}
