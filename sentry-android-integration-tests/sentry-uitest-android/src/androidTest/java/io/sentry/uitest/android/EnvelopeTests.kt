package io.sentry.uitest.android

import android.util.Log
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
import io.sentry.android.core.AndroidLogger
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.assertEnvelopeProfile
import io.sentry.assertEnvelopeTransaction
import io.sentry.profilemeasurements.ProfileMeasurement
import io.sentry.protocol.SentryTransaction
import org.junit.Assume.assumeNotNull
import org.junit.runner.RunWith
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
        Thread.sleep(1000)
        val transaction = Sentry.startTransaction("profiledTransaction", "test1")
        val sampleScenario = launchActivity<ProfilingSampleActivity>()
        swipeList(1)
        sampleScenario.moveToState(Lifecycle.State.DESTROYED)
        IdlingRegistry.getInstance().unregister(ProfilingSampleActivity.scrollingIdlingResource)
        relayIdlingResource.increment()

        transaction.finish()
        relay.assert {
            findEnvelope {
                assertEnvelopeTransaction(it.items.toList(), AndroidLogger()).transaction == "ProfilingSampleActivity"
            }.assert {
                val transactionItem: SentryTransaction = it.assertTransaction()
                it.assertNoOtherItems()
                assertEquals("ProfilingSampleActivity", transactionItem.transaction)
            }

            findEnvelope {
                assertEnvelopeProfile(it.items.toList()).transactionName == "profiledTransaction"
            }.assert {
                val transactionItem: SentryTransaction = it.assertTransaction()
                val profilingTraceData: ProfilingTraceData = it.assertProfile()
                it.assertNoOtherItems()

                // We check the measurements have been collected with expected units
                val slowFrames = profilingTraceData.measurementsMap[ProfileMeasurement.ID_SLOW_FRAME_RENDERS]
                val frozenFrames = profilingTraceData.measurementsMap[ProfileMeasurement.ID_FROZEN_FRAME_RENDERS]
                val frameRates = profilingTraceData.measurementsMap[ProfileMeasurement.ID_SCREEN_FRAME_RATES]
                val memoryStats = profilingTraceData.measurementsMap[ProfileMeasurement.ID_MEMORY_FOOTPRINT]
                val memoryNativeStats = profilingTraceData.measurementsMap[ProfileMeasurement.ID_MEMORY_NATIVE_FOOTPRINT]
                val cpuStats = profilingTraceData.measurementsMap[ProfileMeasurement.ID_CPU_USAGE]

                // Frame rate could be null in headless emulator tests (agp-matrix workflow)
                assumeNotNull(frameRates)

                assertEquals("profiledTransaction", transactionItem.transaction)
                assertEquals(profilingTraceData.transactionId, transaction.eventId.toString())
                assertEquals("profiledTransaction", profilingTraceData.transactionName)
                assertTrue(profilingTraceData.environment.isNotEmpty())
                assertTrue(profilingTraceData.cpuArchitecture.isNotEmpty())
                assertTrue(profilingTraceData.transactions.isNotEmpty())
                assertTrue(profilingTraceData.measurementsMap.isNotEmpty())

                // Slow and frozen frames can be null (in case there were none)
                if (slowFrames != null) {
                    assertEquals(ProfileMeasurement.UNIT_NANOSECONDS, slowFrames.unit)
                }
                if (frozenFrames != null) {
                    assertEquals(ProfileMeasurement.UNIT_NANOSECONDS, frozenFrames.unit)
                }
                // There could be no slow/frozen frames, but we expect at least one frame rate
                assertEquals(ProfileMeasurement.UNIT_HZ, frameRates!!.unit)
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

                // We allow measurements to be added since the start up to the end of the profile
                val maxTimestampAllowed = profilingTraceData.durationNs.toLong()

                profilingTraceData.measurementsMap.entries.filter {
                    // We need at least two measurements, as one will be dropped by our tests
                    it.value.values.size > 1
                }.forEach { entry ->
                    val name = entry.key
                    val measurement = entry.value
                    val values = measurement.values.sortedBy { it.relativeStartNs.toLong() }

                    // There should be no measurement before the profile starts
                    assertTrue(
                        values.first().relativeStartNs.toLong() > 0,
                        "First measurement value for '$name' is <=0"
                    )

                    // The last frame measurements could be outside the transaction duration,
                    //  since when the transaction finishes, the frame callback is removed from the activity,
                    //  but internally it is already cached and will be called anyway in the next frame.
                    //  Also, they are not completely accurate, so they could be flaky.
                    if (entry.key !in listOf(ProfileMeasurement.ID_FROZEN_FRAME_RENDERS, ProfileMeasurement.ID_SLOW_FRAME_RENDERS, ProfileMeasurement.ID_SCREEN_FRAME_RATES)) {
                        // There should be no measurement after the profile ends
                        // Due to the nature of frozen frames, they could be measured after the transaction finishes
                        assertTrue(
                            values.last().relativeStartNs.toLong() <= maxTimestampAllowed,
                            "Last measurement value for '$name' is outside bounds (was: ${values.last().relativeStartNs.toLong()}ns, max: ${maxTimestampAllowed}ns"
                        )
                    }

                    // Timestamps of measurements should differ at least 10 milliseconds from each other
                    (1 until values.size).forEach { i ->
                        assertTrue(
                            values[i].relativeStartNs.toLong() > values[i - 1].relativeStartNs.toLong() + TimeUnit.MILLISECONDS.toNanos(
                                10
                            ),
                            "Measurement value timestamp for '$name' does not differ at least 10ms"
                        )
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
                Log.e("ITEMS", it.items.joinToString { item -> item.header.type.itemType })
                assertEnvelopeTransaction(it.items.toList(), AndroidLogger()).transaction == "timedOutProfile"
            }.assert {
                val transactionItem: SentryTransaction = it.assertTransaction()
                val profilingTraceData: ProfilingTraceData = it.assertProfile()
                it.assertNoOtherItems()
                assertEquals("timedOutProfile", transactionItem.transaction)
                assertEquals("timedOutProfile", profilingTraceData.transactionName)
                // The profile should timeout after 30 seconds
                assertTrue(profilingTraceData.durationNs.toLong() < TimeUnit.SECONDS.toNanos(31), "Profile duration expected to be less than 31 seconds. It was ${profilingTraceData.durationNs.toLong()} ns")
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
        swipeList(5)
        benchmarkScenario.moveToState(Lifecycle.State.DESTROYED)
        transaction.finish()
        IdlingRegistry.getInstance().unregister(ProfilingSampleActivity.scrollingIdlingResource)
        // Let this test send all data, so that it doesn't interfere with other tests
        Thread.sleep(5000)
    }

    private fun swipeList(times: Int) {
        repeat(times) {
            Thread.sleep(100)
            Espresso.onView(ViewMatchers.withId(R.id.profiling_sample_list)).perform(ViewActions.swipeUp())
            Espresso.onIdle()
        }
    }
}
