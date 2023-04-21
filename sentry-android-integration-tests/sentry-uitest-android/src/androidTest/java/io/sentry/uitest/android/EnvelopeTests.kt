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

        relayIdlingResource.increment()
        IdlingRegistry.getInstance().register(ProfilingSampleActivity.scrollingIdlingResource)
        val transaction = Sentry.startTransaction("profiledTransaction", "test1")
        val sampleScenario = launchActivity<ProfilingSampleActivity>()
        swipeList(1)
        sampleScenario.moveToState(Lifecycle.State.DESTROYED)
        IdlingRegistry.getInstance().unregister(ProfilingSampleActivity.scrollingIdlingResource)
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
                val profilingTraceData: ProfilingTraceData = it.assertItem()
                it.assertNoOtherItems()

                assertEquals("profiledTransaction", transactionItem.transaction)
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
                val memoryStats = profilingTraceData.measurementsMap[ProfileMeasurement.ID_MEMORY_FOOTPRINT]
                val memoryNativeStats = profilingTraceData.measurementsMap[ProfileMeasurement.ID_MEMORY_NATIVE_FOOTPRINT]
                val cpuStats = profilingTraceData.measurementsMap[ProfileMeasurement.ID_CPU_USAGE]
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
                memoryStats?.let {
                    assertEquals(ProfileMeasurement.UNIT_BYTES, it.unit)
                    assertEquals(true, it.values.isNotEmpty())
                }
                memoryNativeStats?.let {
                    assertEquals(ProfileMeasurement.UNIT_BYTES, it.unit)
                    assertEquals(true, it.values.isNotEmpty())
                }
                cpuStats?.let {
                    assertEquals(ProfileMeasurement.UNIT_PERCENT, it.unit)
                    assertEquals(true, it.values.isNotEmpty())
                }

                // We allow measurements to be added since the start up to the end of the profile, with a small tolerance due to threading
                val maxTimestampAllowed = profilingTraceData.durationNs.toLong() + TimeUnit.SECONDS.toNanos(2)
                val allMeasurements = profilingTraceData.measurementsMap.values

                allMeasurements.filter { it.values.isNotEmpty() }.forEach { measurement ->
                    val values = measurement.values.sortedBy { it.relativeStartNs.toLong() }
                    // There should be no measurement before the profile starts
                    assertTrue(values.first().relativeStartNs.toLong() > 0)
                    // There should be no measurement after the profile ends
                    assertTrue(values.last().relativeStartNs.toLong() < maxTimestampAllowed)

                    // Timestamps of measurements should differ at least 10 milliseconds from each other
                    (1 until values.size).forEach { i ->
                        assertTrue(values[i].relativeStartNs.toLong() > values[i - 1].relativeStartNs.toLong() + TimeUnit.MILLISECONDS.toNanos(10))
                    }
                }

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
    fun checkProfileNotSentIfEmpty() {
        initSentry(true) { options: SentryAndroidOptions ->
            options.tracesSampleRate = 1.0
            options.profilesSampleRate = 1.0
        }
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
        val transaction = Sentry.startTransaction("timedOutProfile", "testTimeout")
        Thread {
            Thread.sleep(35_000)
            transaction.finish()
        }.start()
        // We call transaction.finish() after 35 seconds, and check profile times out after 30 seconds

        relay.assert {
            findEnvelope {
                assertEnvelopeItem<SentryTransaction>(it.items.toList()).transaction == "timedOutProfile"
            }.assert {
                val transactionItem: SentryTransaction = it.assertItem()
                val profilingTraceData: ProfilingTraceData = it.assertItem()
                it.assertNoOtherItems()
                assertEquals("timedOutProfile", transactionItem.transaction)
                assertEquals("timedOutProfile", profilingTraceData.transactionName)
                // The profile should timeout after 30 seconds
                assertTrue(profilingTraceData.durationNs.toLong() < TimeUnit.SECONDS.toNanos(31))
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
