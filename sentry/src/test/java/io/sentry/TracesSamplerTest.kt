package io.sentry

import io.sentry.util.Random
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TracesSamplerTest {
    class Fixture {
        internal fun getSut(
            randomResult: Double? = null,
            tracesSampleRate: Double? = null,
            profilesSampleRate: Double? = null,
            continuousProfilesSampleRate: Double? = null,
            tracesSamplerCallback: SentryOptions.TracesSamplerCallback? = null,
            profilesSamplerCallback: SentryOptions.ProfilesSamplerCallback? = null,
            logger: ILogger? = null
        ): TracesSampler {
            val random = mock<Random>()
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
            if (continuousProfilesSampleRate != null) {
                options.continuousProfilesSampleRate = continuousProfilesSampleRate
            }
            if (tracesSamplerCallback != null) {
                options.tracesSampler = tracesSamplerCallback
            }
            if (profilesSamplerCallback != null) {
                options.profilesSampler = profilesSamplerCallback
            }
            if (logger != null) {
                options.isDebug = true
                options.setLogger(logger)
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
        val sampler = fixture.getSut(
            randomResult = 0.1,
            tracesSamplerCallback = { 0.2 },
            profilesSamplerCallback = { 0.2 }
        )
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
        val sampler = fixture.getSut(randomResult = 0.1, tracesSampleRate = 1.0, profilesSamplerCallback = { 0.2 })
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
        val sampler = fixture.getSut(randomResult = 0.9, tracesSamplerCallback = { 0.2 })
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
        val sampler = fixture.getSut(randomResult = 0.9, tracesSampleRate = 1.0, profilesSamplerCallback = { 0.2 })
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
    fun `when continuousProfilesSampleRate is not set returns true`() {
        val sampler = fixture.getSut(randomResult = 1.0)
        val sampled = sampler.sampleContinuousProfile()
        assertTrue(sampled)
    }

    @Test
    fun `when continuousProfilesSampleRate is set and random returns lower number returns true`() {
        val sampler = fixture.getSut(randomResult = 0.1, continuousProfilesSampleRate = 0.2)
        val sampled = sampler.sampleContinuousProfile()
        assertTrue(sampled)
    }

    @Test
    fun `when continuousProfilesSampleRate is set and random returns greater number returns false`() {
        val sampler = fixture.getSut(randomResult = 0.9, continuousProfilesSampleRate = 0.2)
        val sampled = sampler.sampleContinuousProfile()
        assertFalse(sampled)
    }

    @Test
    fun `when tracesSampler returns null and parentSampled is set sampler uses it as a sampling decision`() {
        val sampler = fixture.getSut(tracesSamplerCallback = null)
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
        val sampler = fixture.getSut(tracesSampleRate = 1.0, profilesSamplerCallback = null)
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
        val sampler = fixture.getSut(randomResult = 0.1, tracesSampleRate = 0.2, tracesSamplerCallback = null)
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
        val sampler = fixture.getSut(randomResult = 0.1, tracesSampleRate = 1.0, profilesSampleRate = 0.2, profilesSamplerCallback = null)
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
        transactionContextNotSampled.sampled = false
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

    @Test
    fun `when ProfilesSamplerCallback throws an exception then profiling is disabled and an error is logged`() {
        val logger = mock<ILogger>()

        val exception = Exception("faulty ProfilesSamplerCallback")
        val sampler = fixture.getSut(
            tracesSampleRate = 1.0,
            profilesSamplerCallback = {
                throw exception
            },
            logger = logger
        )
        val decision = sampler.sample(
            SamplingContext(TransactionContext("name", "op"), null)
        )
        assertFalse(decision.profileSampled)
        verify(logger).log(eq(SentryLevel.ERROR), any(), eq(exception))
    }

    @Test
    fun `when a profilingRate and a ProfilesSamplerCallback is set but the callback throws an exception then profiling should still be enabled`() {
        val exception = Exception("faulty ProfilesSamplerCallback")
        val sampler = fixture.getSut(
            randomResult = 0.0,
            tracesSampleRate = 1.0,
            profilesSampleRate = 1.0,
            profilesSamplerCallback = {
                throw exception
            }
        )
        val decision = sampler.sample(
            SamplingContext(TransactionContext("name", "op"), null)
        )
        assertTrue(decision.profileSampled)
    }

    @Test
    fun `when TracesSamplerCallback throws an exception then tracing is disabled and an error is logged`() {
        val logger = mock<ILogger>()

        val exception = Exception("faulty TracesSamplerCallback")
        val sampler = fixture.getSut(
            tracesSamplerCallback = {
                throw exception
            },
            logger = logger
        )
        val decision = sampler.sample(
            SamplingContext(TransactionContext("name", "op"), null)
        )
        assertFalse(decision.sampled)
        verify(logger).log(eq(SentryLevel.ERROR), any(), eq(exception))
    }

    @Test
    fun `when a tracesSampleRate and a TracesSamplerCallback is set but the callback throws an exception then tracing should still be enabled`() {
        val exception = Exception("faulty TracesSamplerCallback")
        val sampler = fixture.getSut(
            randomResult = 0.0,
            tracesSampleRate = 1.0,
            tracesSamplerCallback = {
                throw exception
            }
        )
        val decision = sampler.sample(
            SamplingContext(TransactionContext("name", "op"), null)
        )
        assertTrue(decision.sampled)
    }
}
