package io.sentry.uitest.android

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso
import androidx.test.espresso.IdlingPolicies
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ProfilingTraceData
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.assertEnvelopeItem
import io.sentry.profilemeasurements.ProfileMeasurement
import io.sentry.protocol.SentryTransaction
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
            assertFirstEnvelope {
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
        initSentry(true) { options: SentryAndroidOptions ->
            options.tracesSampleRate = 1.0
            options.profilesSampleRate = 1.0
        }

        IdlingRegistry.getInstance().register(ProfilingSampleActivity.scrollingIdlingResource)
        val transaction = Sentry.startTransaction("profiledTransaction", "test1")
        val sampleScenario = launchActivity<ProfilingSampleActivity>()
        swipeList(1)
        sampleScenario.moveToState(Lifecycle.State.DESTROYED)
        IdlingRegistry.getInstance().unregister(ProfilingSampleActivity.scrollingIdlingResource)
        relayIdlingResource.increment()
        relayIdlingResource.increment()
        relayIdlingResource.increment()

        transaction.finish()
        relay.assert {
            findEnvelope {
                assertEnvelopeItem<SentryTransaction>(it.items.toList()).transaction == "ProfilingSampleActivity"
            }.assert {
                val transactionItem: SentryTransaction = it.assertItem()
                it.assertNoOtherItems()
                assertEquals("ProfilingSampleActivity", transactionItem.transaction)
            }

            findEnvelope {
                assertEnvelopeItem<SentryTransaction>(it.items.toList()).transaction == "profiledTransaction"
            }.assert {
                val transactionItem: SentryTransaction = it.assertItem()
                it.assertNoOtherItems()
                assertEquals("profiledTransaction", transactionItem.transaction)
            }

            findEnvelope {
                assertEnvelopeItem<ProfilingTraceData>(it.items.toList()).transactionName == "profiledTransaction"
            }.assert {
                val profilingTraceData: ProfilingTraceData = it.assertItem()
                it.assertNoOtherItems()
                assertEquals(profilingTraceData.transactionId, transaction.eventId.toString())
                assertEquals("profiledTransaction", profilingTraceData.transactionName)
                assertTrue(profilingTraceData.environment.isNotEmpty())
                assertTrue(profilingTraceData.cpuArchitecture.isNotEmpty())
                assertTrue(profilingTraceData.transactions.isNotEmpty())
                assertTrue(profilingTraceData.measurementsMap.isNotEmpty())

                // We check the measurements have been collected with expected units
                val slowFrames = profilingTraceData.measurementsMap[ProfileMeasurement.ID_SLOW_FRAME_RENDERS]
                val frozenFrames = profilingTraceData.measurementsMap[ProfileMeasurement.ID_FROZEN_FRAME_RENDERS]
                val frameRates = profilingTraceData.measurementsMap[ProfileMeasurement.ID_SCREEN_FRAME_RATES]!!
                // Slow and frozen frames can be null (in case there were none)
                if (slowFrames != null) {
                    assertEquals(ProfileMeasurement.UNIT_NANOSECONDS, slowFrames.unit)
                }
                if (frozenFrames != null) {
                    assertEquals(ProfileMeasurement.UNIT_NANOSECONDS, frozenFrames.unit)
                }
                // There could be no slow/frozen frames, but we expect at least one frame rate
                assertEquals(ProfileMeasurement.UNIT_HZ, frameRates.unit)
                assertTrue(frameRates.values.isNotEmpty())

                // We should find the transaction id that started the profiling in the list of transactions
                val transactionData = profilingTraceData.transactions
                    .firstOrNull { t -> t.id == transaction.eventId.toString() }
                assertNotNull(transactionData)
            }
            assertNoOtherEnvelopes()
            assertNoOtherRequests()
        }
    }

    @Test
    fun checkEnvelopeConcurrentTransactions() {
        initSentry(true) { options: SentryAndroidOptions ->
            options.tracesSampleRate = 1.0
            options.profilesSampleRate = 1.0
        }
        relayIdlingResource.increment()
        relayIdlingResource.increment()
        relayIdlingResource.increment()
        relayIdlingResource.increment()

        val transaction = Sentry.startTransaction("e2etests", "test1")
        val transaction2 = Sentry.startTransaction("e2etests1", "test2")
        val transaction3 = Sentry.startTransaction("e2etests2", "test3")
        transaction.finish()
        transaction2.finish()
        transaction3.finish()

        relay.assert {
            findEnvelope {
                assertEnvelopeItem<SentryTransaction>(it.items.toList()).transaction == "e2etests"
            }.assert {
                val transactionItem: SentryTransaction = it.assertItem()
                it.assertNoOtherItems()
                assertEquals(transaction.eventId.toString(), transactionItem.eventId.toString())
            }
            findEnvelope {
                assertEnvelopeItem<SentryTransaction>(it.items.toList()).transaction == "e2etests1"
            }.assert {
                val transactionItem: SentryTransaction = it.assertItem()
                it.assertNoOtherItems()
                assertEquals(transaction2.eventId.toString(), transactionItem.eventId.toString())
            }
            findEnvelope {
                assertEnvelopeItem<SentryTransaction>(it.items.toList()).transaction == "e2etests2"
            }.assert {
                val transactionItem: SentryTransaction = it.assertItem()
                it.assertNoOtherItems()
                assertEquals(transaction3.eventId.toString(), transactionItem.eventId.toString())
            }
            // The profile is sent in its own envelope
            findEnvelope {
                assertEnvelopeItem<ProfilingTraceData>(it.items.toList()).transactionName == "e2etests2"
            }.assert {
                val profilingTraceData: ProfilingTraceData = it.assertItem()
                it.assertNoOtherItems()
                assertEquals("e2etests2", profilingTraceData.transactionName)
                assertEquals("normal", profilingTraceData.truncationReason)

                // The transaction list is not ordered, since it's stored using a map to be able to quickly check the
                // existence of a certain id. So we order the list to make more meaningful checks on timestamps.
                val transactions = profilingTraceData.transactions.sortedBy { it.relativeStartNs }
                assertEquals(transactions.last().id, transaction3.eventId.toString())
                val startTimes = transactions.map { t -> t.relativeStartNs }
                val endTimes = transactions.mapNotNull { t -> t.relativeEndNs }
                val startCpuTimes = transactions.map { t -> t.relativeStartCpuMs }
                val endCpuTimes = transactions.mapNotNull { t -> t.relativeEndCpuMs }

                // Transaction timestamps should be all different from each other
                assertTrue(startTimes[0] < startTimes[1])
                assertTrue(startTimes[1] < startTimes[2])
                assertTrue(endTimes[0] < endTimes[1])
                assertTrue(endTimes[1] < endTimes[2])

                // The cpu timestamps use milliseconds precision, so few of them could have the same timestamps
                // However, it's basically impossible that all of them have the same timestamps
                assertFalse(startCpuTimes[0] == startCpuTimes[1] && startCpuTimes[1] == startCpuTimes[2])
                assertTrue(startCpuTimes[0] <= startCpuTimes[1])
                assertTrue(startCpuTimes[1] <= startCpuTimes[2])
                assertFalse(endCpuTimes[0] == endCpuTimes[1] && endCpuTimes[1] == endCpuTimes[2])
                assertTrue(endCpuTimes[0] <= endCpuTimes[1])
                assertTrue(endCpuTimes[1] <= endCpuTimes[2])

                // The first and last transactions should be aligned to the start/stop of profile
                assertEquals(endTimes.last() - startTimes.first(), profilingTraceData.durationNs.toLong())
            }
            assertNoOtherEnvelopes()
            assertNoOtherRequests()
        }
    }

    @Test
    fun checkProfileNotSentIfEmpty() {
        initSentry(true) { options: SentryAndroidOptions ->
            options.tracesSampleRate = 1.0
            options.profilesSampleRate = 1.0
        }
        relayIdlingResource.increment()
        relayIdlingResource.increment()
        val profilesDirPath = Sentry.getCurrentHub().options.profilingTracesDirPath
        val transaction = Sentry.startTransaction("emptyProfileTransaction", "test empty")

        var finished = false
        Thread {
            while (!finished) {
                // Let's modify the trace file to be empty, so that the profile will actually be empty.
                val origProfileFile = File(profilesDirPath!!).listFiles()?.maxByOrNull { f -> f.lastModified() }
                origProfileFile?.writeBytes(ByteArray(0))
            }
        }.start()
        transaction.finish()
        // The profiler is stopped in background on the executor service, so we can stop deleting the trace file
        // only after the profiler is stopped. This means we have to stop the deletion in the executorService
        Sentry.getCurrentHub().options.executorService.submit {
            finished = true
        }

        relay.assert {
            findEnvelope {
                assertEnvelopeItem<SentryTransaction>(it.items.toList()).transaction == "emptyProfileTransaction"
            }.assert {
                val transactionItem: SentryTransaction = it.assertItem()
                it.assertNoOtherItems()
                assertEquals("emptyProfileTransaction", transactionItem.transaction)
            }
            // The profile failed to be sent. Trying to read the envelope from the data transmitted throws an exception
            assertFailsWith<IllegalArgumentException> { assertFirstEnvelope {} }
            assertNoOtherEnvelopes()
            assertNoOtherRequests()
        }
    }

    @Test
    fun checkTimedOutProfile() {
        // We increase the IdlingResources timeout to exceed the profiling timeout
        IdlingPolicies.setIdlingResourceTimeout(1, TimeUnit.MINUTES)
        initSentry(true) { options: SentryAndroidOptions ->
            options.tracesSampleRate = 1.0
            options.profilesSampleRate = 1.0
        }
        relayIdlingResource.increment()
        Sentry.startTransaction("timedOutProfile", "testTimeout")
        // We don't call transaction.finish() and let the timeout do its job

        relay.assert {
            findEnvelope {
                assertEnvelopeItem<ProfilingTraceData>(it.items.toList()).transactionName == "timedOutProfile"
            }.assert {
                val profilingTraceData: ProfilingTraceData = it.assertItem()
                it.assertNoOtherItems()
                assertEquals("timedOutProfile", profilingTraceData.transactionName)
                assertEquals(ProfilingTraceData.TRUNCATION_REASON_TIMEOUT, profilingTraceData.truncationReason)
            }
            assertNoOtherEnvelopes()
            assertNoOtherRequests()
        }
    }

    @Test
    fun sendProfiledTransaction() {
        // This is a dogfooding test
        IdlingRegistry.getInstance().register(ProfilingSampleActivity.scrollingIdlingResource)
        initSentry(false) { options: SentryAndroidOptions ->
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
        // Let this test send all data, so that it doesn't interfere with other tests
        Thread.sleep(1000)
    }

    private fun swipeList(times: Int) {
        repeat(times) {
            Thread.sleep(100)
            Espresso.onView(ViewMatchers.withId(R.id.profiling_sample_list)).perform(ViewActions.swipeUp())
            Espresso.onIdle()
        }
    }
}
