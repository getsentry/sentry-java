package io.sentry

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals

data class TracingEnabledTestData(val enableTracing: Boolean?, val tracesSampleRate: Double?, val tracesSamplerPresent: Boolean, val isTracingEnabled: Boolean)

/**
 * Test @link{SentryOptions#isTracingEnabled()} with combination of other options.
 */
@RunWith(Parameterized::class)
class SentryOptionsTracingTest(private val testData: TracingEnabledTestData) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun data(): Collection<Array<Any>> {
            return listOf(
                TracingEnabledTestData(null, null, false, false),
                TracingEnabledTestData(null, 1.0, false, true),
                TracingEnabledTestData(false, 1.0, false, false),
                TracingEnabledTestData(true, 1.0, false, true),
                TracingEnabledTestData(null, 0.0, false, true),
                TracingEnabledTestData(false, 0.0, false, false),
                TracingEnabledTestData(true, 0.0, false, true),
                TracingEnabledTestData(true, null, false, true),
                TracingEnabledTestData(false, null, false, false),

                TracingEnabledTestData(null, null, true, true),
                TracingEnabledTestData(null, 1.0, true, true),
                TracingEnabledTestData(false, 1.0, true, false),
                TracingEnabledTestData(true, 1.0, true, true),
                TracingEnabledTestData(null, 0.0, true, true),
                TracingEnabledTestData(false, 0.0, true, false),
                TracingEnabledTestData(true, 0.0, true, true),
                TracingEnabledTestData(true, null, true, true),
                TracingEnabledTestData(false, null, true, false)
            ).map { arrayOf(it) }
        }
    }

    @Test
    fun `test isTracingEnabled`() {
        val options = SentryOptions().apply {
            testData.enableTracing?.let { this.enableTracing = it }
            testData.tracesSampleRate?.let { this.tracesSampleRate = it }
            if (testData.tracesSamplerPresent) {
                this.tracesSampler = SentryOptions.TracesSamplerCallback { samplingContext -> 1.0 }
            }
        }

        assertEquals(testData.isTracingEnabled, options.isTracingEnabled, "combination failed: $testData")
    }
}
