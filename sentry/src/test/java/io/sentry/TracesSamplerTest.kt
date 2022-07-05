package io.sentry

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TracesSamplerTest {
    class Fixture {
        internal fun getSut(randomResult: Double? = null, tracesSampleRate: Double? = null, tracesSamplerResult: Double? = Double.MIN_VALUE): TracesSampler {
            val random = mock<SecureRandom>()
            if (randomResult != null) {
                whenever(random.nextDouble()).thenReturn(randomResult)
            }
            val options = SentryOptions()
            if (tracesSampleRate != null) {
                options.tracesSampleRate = tracesSampleRate
            }
            if (tracesSamplerResult != Double.MIN_VALUE) {
                options.tracesSampler = SentryOptions.TracesSamplerCallback { tracesSamplerResult }
            }
            return TracesSampler(options, random)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when tracesSampleRate is set and random returns greater number returns false`() {
        val sampler = fixture.getSut(randomResult = 0.9, tracesSampleRate = 0.2)
        val samplingDecision = sampler.sample(SamplingContext(TransactionContext("name", "op"), null))
        assertFalse(samplingDecision.sampled)
        assertEquals(0.2, samplingDecision.sampleRate)
    }

    @Test
    fun `when tracesSampleRate is set and random returns lower number returns true`() {
        val sampler = fixture.getSut(randomResult = 0.1, tracesSampleRate = 0.2)
        val samplingDecision = sampler.sample(SamplingContext(TransactionContext("name", "op"), null))
        assertTrue(samplingDecision.sampled)
        assertEquals(0.2, samplingDecision.sampleRate)
    }

    @Test
    fun `when tracesSampleRate is not set, tracesSampler is set and random returns lower number returns false`() {
        val sampler = fixture.getSut(randomResult = 0.1, tracesSamplerResult = 0.2)
        val samplingDecision = sampler.sample(
            SamplingContext(
                TransactionContext("name", "op"),
                CustomSamplingContext()
            )
        )
        assertTrue(samplingDecision.sampled)
        assertEquals(0.2, samplingDecision.sampleRate)
    }

    @Test
    fun `when tracesSampleRate is not set, tracesSampler is set and random returns greater number returns false`() {
        val sampler = fixture.getSut(randomResult = 0.9, tracesSamplerResult = 0.2)
        val samplingDecision = sampler.sample(
            SamplingContext(
                TransactionContext("name", "op"),
                CustomSamplingContext()
            )
        )
        assertFalse(samplingDecision.sampled)
        assertEquals(0.2, samplingDecision.sampleRate)
    }

    @Test
    fun `when tracesSampler returns null and parentSampled is set sampler uses it as a sampling decision`() {
        val sampler = fixture.getSut(tracesSamplerResult = null)
        val transactionContextParentSampled = TransactionContext("name", "op")
        transactionContextParentSampled.parentSampled = true
        val samplingDecision = sampler.sample(
            SamplingContext(
                transactionContextParentSampled,
                CustomSamplingContext()
            )
        )
        assertTrue(samplingDecision.sampled)
        assertNull(samplingDecision.sampleRate)
    }

    @Test
    fun `when tracesSampler returns null and tracesSampleRate is set sampler uses it as a sampling decision`() {
        val sampler = fixture.getSut(randomResult = 0.1, tracesSampleRate = 0.2, tracesSamplerResult = null)
        val samplingDecision = sampler.sample(
            SamplingContext(
                TransactionContext("name", "op"),
                CustomSamplingContext()
            )
        )
        assertTrue(samplingDecision.sampled)
        assertEquals(0.2, samplingDecision.sampleRate)
    }

    @Test
    fun `when tracesSampleRate is not set, and tracesSampler is not set returns false`() {
        val sampler = fixture.getSut(randomResult = 0.1)
        val samplingDecision = sampler.sample(
            SamplingContext(
                TransactionContext("name", "op"),
                CustomSamplingContext()
            )
        )
        assertFalse(samplingDecision.sampled)
        assertNull(samplingDecision.sampleRate)
    }

    @Test
    fun `when parentSampled is set, sampler uses it as a sampling decision`() {
        val sampler = fixture.getSut()
        val transactionContextParentNotSampled = TransactionContext("name", "op")
        transactionContextParentNotSampled.parentSampled = false
        val samplingDecision = sampler.sample(
            SamplingContext(
                transactionContextParentNotSampled,
                CustomSamplingContext()
            )
        )
        assertFalse(samplingDecision.sampled)
        assertNull(samplingDecision.sampleRate)

        val transactionContextParentSampled = TransactionContext("name", "op")
        transactionContextParentSampled.parentSampled = true
        val samplingDecisionParentSampled = sampler.sample(
            SamplingContext(
                transactionContextParentSampled,
                CustomSamplingContext()
            )
        )
        assertTrue(samplingDecisionParentSampled.sampled)
        assertNull(samplingDecisionParentSampled.sampleRate)
    }

    @Test
    fun `when tracing decision is set on SpanContext, sampler uses it as a sampling decision`() {
        val sampler = fixture.getSut()
        val transactionContextNotSampled = TransactionContext("name", "op")
        transactionContextNotSampled.sampled = false
        val samplingDecision =
            sampler.sample(SamplingContext(transactionContextNotSampled, CustomSamplingContext()))
        assertFalse(samplingDecision.sampled)
        assertNull(samplingDecision.sampleRate)

        val transactionContextSampled = TransactionContext("name", "op")
        transactionContextSampled.sampled = true
        val samplingDecisionContextSampled =
            sampler.sample(SamplingContext(transactionContextSampled, CustomSamplingContext()))
        assertTrue(samplingDecisionContextSampled.sampled)
        assertNull(samplingDecisionContextSampled.sampleRate)
    }
}
