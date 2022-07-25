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
        internal fun getSut(randomResult: Double? = null, tracesSampleRate: Double? = null, profilesSampleRate: Double? = null, tracesSamplerResult: Double? = Double.MIN_VALUE, profilesSamplerResult: Double? = Double.MIN_VALUE): TracesSampler {
            val random = mock<SecureRandom>()
            if (randomResult != null) {
                whenever(random.nextDouble()).thenReturn(randomResult)
            }
            val options = SentryOptions()
            if (tracesSampleRate != null) {
                options.tracesSampleRate = tracesSampleRate
            }
            if (profilesSampleRate != null) {
                options.profilesSampleRate = profilesSampleRate
            }
            if (tracesSamplerResult != Double.MIN_VALUE) {
                options.tracesSampler = SentryOptions.TracesSamplerCallback { tracesSamplerResult }
            }
            if (profilesSamplerResult != Double.MIN_VALUE) {
                options.profilesSampler = SentryOptions.ProfilesSamplerCallback { profilesSamplerResult }
            }
            return TracesSampler(options, random)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when tracesSampleRate is set and random returns greater number returns false`() {
        val sampler = fixture.getSut(randomResult = 0.9, tracesSampleRate = 0.2, profilesSampleRate = 0.2)
        val samplingDecision = sampler.sample(SamplingContext(TransactionContext("name", "op"), null))
        assertFalse(samplingDecision.sampled)
        assertEquals(0.2, samplingDecision.sampleRate)
    }

    @Test
    fun `when tracesSampleRate is set and random returns lower number returns true`() {
        val sampler = fixture.getSut(randomResult = 0.1, tracesSampleRate = 0.2, profilesSampleRate = 0.2)
        val samplingDecision = sampler.sample(SamplingContext(TransactionContext("name", "op"), null))
        assertTrue(samplingDecision.sampled)
        assertEquals(0.2, samplingDecision.sampleRate)
    }

    @Test
    fun `when profilesSampleRate is set and random returns greater number returns false`() {
        val sampler = fixture.getSut(randomResult = 0.9, tracesSampleRate = 1.0, profilesSampleRate = 0.2)
        val samplingDecision = sampler.sample(SamplingContext(TransactionContext("name", "op"), null))
        assertTrue(samplingDecision.sampled)
        assertFalse(samplingDecision.profileSampled)
        assertEquals(0.2, samplingDecision.profileSampleRate)
    }

    @Test
    fun `when profilesSampleRate is set and random returns lower number returns true`() {
        val sampler = fixture.getSut(randomResult = 0.1, tracesSampleRate = 1.0, profilesSampleRate = 0.2)
        val samplingDecision = sampler.sample(SamplingContext(TransactionContext("name", "op"), null))
        assertTrue(samplingDecision.sampled)
        assertTrue(samplingDecision.profileSampled)
        assertEquals(0.2, samplingDecision.profileSampleRate)
    }

    @Test
    fun `when trace is not sampled, profile is not sampled`() {
        val sampler = fixture.getSut(randomResult = 0.3, tracesSampleRate = 0.0, profilesSampleRate = 1.0)
        val samplingDecision = sampler.sample(SamplingContext(TransactionContext("name", "op"), null))
        assertFalse(samplingDecision.sampled)
        assertFalse(samplingDecision.profileSampled)
        assertEquals(1.0, samplingDecision.profileSampleRate)
    }

    @Test
    fun `when tracesSampleRate is not set, tracesSampler is set and random returns lower number returns true`() {
        val sampler = fixture.getSut(randomResult = 0.1, tracesSamplerResult = 0.2, profilesSamplerResult = 0.2)
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
    fun `when profilesSampleRate is not set, profilesSampler is set and random returns lower number returns true`() {
        val sampler = fixture.getSut(randomResult = 0.1, tracesSampleRate = 1.0, profilesSamplerResult = 0.2)
        val samplingDecision = sampler.sample(
            SamplingContext(
                TransactionContext("name", "op"),
                CustomSamplingContext()
            )
        )
        assertTrue(samplingDecision.sampled)
        assertTrue(samplingDecision.profileSampled)
        assertEquals(0.2, samplingDecision.profileSampleRate)
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
    fun `when profilesSampleRate is not set, profilesSampler is set and random returns greater number returns false`() {
        val sampler = fixture.getSut(randomResult = 0.9, tracesSampleRate = 1.0, profilesSamplerResult = 0.2)
        val samplingDecision = sampler.sample(
            SamplingContext(
                TransactionContext("name", "op"),
                CustomSamplingContext()
            )
        )
        assertTrue(samplingDecision.sampled)
        assertFalse(samplingDecision.profileSampled)
        assertEquals(0.2, samplingDecision.profileSampleRate)
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
    fun `when profilesSampler returns null and parentSampled is set sampler uses it as a sampling decision`() {
        val sampler = fixture.getSut(tracesSampleRate = 1.0, profilesSamplerResult = null)
        val transactionContextParentSampled = TransactionContext("name", "op")
        transactionContextParentSampled.setParentSampled(true, true)
        val samplingDecision = sampler.sample(
            SamplingContext(
                transactionContextParentSampled,
                CustomSamplingContext()
            )
        )
        assertTrue(samplingDecision.sampled)
        assertTrue(samplingDecision.profileSampled)
        assertNull(samplingDecision.profileSampleRate)
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
    fun `when profilesSampler returns null and profilesSampleRate is set sampler uses it as a sampling decision`() {
        val sampler = fixture.getSut(randomResult = 0.1, tracesSampleRate = 1.0, profilesSampleRate = 0.2, profilesSamplerResult = null)
        val samplingDecision = sampler.sample(
            SamplingContext(
                TransactionContext("name", "op"),
                CustomSamplingContext()
            )
        )
        assertTrue(samplingDecision.sampled)
        assertTrue(samplingDecision.profileSampled)
        assertEquals(0.2, samplingDecision.profileSampleRate)
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
    fun `when profilesSampleRate is not set, and profilesSampler is not set returns false`() {
        val sampler = fixture.getSut(randomResult = 0.1, tracesSampleRate = 1.0)
        val samplingDecision = sampler.sample(
            SamplingContext(
                TransactionContext("name", "op"),
                CustomSamplingContext()
            )
        )
        assertTrue(samplingDecision.sampled)
        assertFalse(samplingDecision.profileSampled)
        assertNull(samplingDecision.profileSampleRate)
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
        assertFalse(samplingDecision.profileSampled)
        assertNull(samplingDecision.profileSampleRate)

        val transactionContextParentSampled = TransactionContext("name", "op")
        transactionContextParentSampled.setParentSampled(true, true)
        val samplingDecisionParentSampled = sampler.sample(
            SamplingContext(
                transactionContextParentSampled,
                CustomSamplingContext()
            )
        )
        assertTrue(samplingDecisionParentSampled.sampled)
        assertNull(samplingDecisionParentSampled.sampleRate)
        assertTrue(samplingDecisionParentSampled.profileSampled)
        assertNull(samplingDecisionParentSampled.profileSampleRate)
    }

    @Test
    fun `when parentSampled is not set and parentProfileSampled is set, profile is not sampled`() {
        val sampler = fixture.getSut()
        val transactionContextParentUnsampled = TransactionContext("name", "op")
        transactionContextParentUnsampled.setParentSampled(false, true)
        val samplingDecisionParentSampled = sampler.sample(
            SamplingContext(
                transactionContextParentUnsampled,
                CustomSamplingContext()
            )
        )
        assertFalse(samplingDecisionParentSampled.sampled)
        assertNull(samplingDecisionParentSampled.sampleRate)
        assertFalse(samplingDecisionParentSampled.profileSampled)
        assertNull(samplingDecisionParentSampled.profileSampleRate)
    }

    @Test
    fun `when tracing decision is set on SpanContext, sampler uses it as a sampling decision`() {
        val sampler = fixture.getSut()
        val transactionContextNotSampled = TransactionContext("name", "op")
        transactionContextNotSampled.setSampled(false, false)
        val samplingDecision =
            sampler.sample(SamplingContext(transactionContextNotSampled, CustomSamplingContext()))
        assertFalse(samplingDecision.sampled)
        assertNull(samplingDecision.sampleRate)
        assertFalse(samplingDecision.profileSampled)
        assertNull(samplingDecision.profileSampleRate)

        val transactionContextSampled = TransactionContext("name", "op")
        transactionContextSampled.setSampled(true, true)
        val samplingDecisionContextSampled =
            sampler.sample(SamplingContext(transactionContextSampled, CustomSamplingContext()))
        assertTrue(samplingDecisionContextSampled.sampled)
        assertNull(samplingDecisionContextSampled.sampleRate)
        assertTrue(samplingDecisionContextSampled.profileSampled)
        assertNull(samplingDecisionContextSampled.profileSampleRate)

        val transactionContextUnsampledWithProfile = TransactionContext("name", "op")
        transactionContextUnsampledWithProfile.setSampled(false, true)
        val samplingDecisionContextUnsampledWithProfile =
            sampler.sample(SamplingContext(transactionContextUnsampledWithProfile, CustomSamplingContext()))
        assertFalse(samplingDecisionContextUnsampledWithProfile.sampled)
        assertNull(samplingDecisionContextUnsampledWithProfile.sampleRate)
        assertFalse(samplingDecisionContextUnsampledWithProfile.profileSampled)
        assertNull(samplingDecisionContextUnsampledWithProfile.profileSampleRate)
    }
}
