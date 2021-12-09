package io.sentry

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertFalse
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
        assertFalse(sampler.sample(SamplingContext(TransactionContext("name", "op"), null)))
    }

    @Test
    fun `when tracesSampleRate is set and random returns lower number returns true`() {
        val sampler = fixture.getSut(randomResult = 0.1, tracesSampleRate = 0.2)
        assertTrue(sampler.sample(SamplingContext(TransactionContext("name", "op"), null)))
    }

    @Test
    fun `when tracesSampleRate is not set, tracesSampler is set and random returns lower number returns false`() {
        val sampler = fixture.getSut(randomResult = 0.1, tracesSamplerResult = 0.2)
        assertTrue(sampler.sample(SamplingContext(TransactionContext("name", "op"), CustomSamplingContext())))
    }

    @Test
    fun `when tracesSampleRate is not set, tracesSampler is set and random returns greater number returns false`() {
        val sampler = fixture.getSut(randomResult = 0.9, tracesSamplerResult = 0.2)
        assertFalse(sampler.sample(SamplingContext(TransactionContext("name", "op"), CustomSamplingContext())))
    }

    @Test
    fun `when tracesSampler returns null and parentSampled is set sampler uses it as a sampling decision`() {
        val sampler = fixture.getSut(tracesSamplerResult = null)
        val transactionContextParentSampled = TransactionContext("name", "op")
        transactionContextParentSampled.parentSampled = true
        assertTrue(sampler.sample(SamplingContext(transactionContextParentSampled, CustomSamplingContext())))
    }

    @Test
    fun `when tracesSampler returns null and tracesSampleRate is set sampler uses it as a sampling decision`() {
        val sampler = fixture.getSut(randomResult = 0.1, tracesSampleRate = 0.2, tracesSamplerResult = null)
        assertTrue(sampler.sample(SamplingContext(TransactionContext("name", "op"), CustomSamplingContext())))
    }

    @Test
    fun `when tracesSampleRate is not set, and tracesSampler is not set returns false`() {
        val sampler = fixture.getSut(randomResult = 0.1)
        assertFalse(sampler.sample(SamplingContext(TransactionContext("name", "op"), CustomSamplingContext())))
    }

    @Test
    fun `when parentSampled is set, sampler uses it as a sampling decision`() {
        val sampler = fixture.getSut()
        val transactionContextParentNotSampled = TransactionContext("name", "op")
        transactionContextParentNotSampled.parentSampled = false
        assertFalse(sampler.sample(SamplingContext(transactionContextParentNotSampled, CustomSamplingContext())))
        val transactionContextParentSampled = TransactionContext("name", "op")
        transactionContextParentSampled.parentSampled = true
        assertTrue(sampler.sample(SamplingContext(transactionContextParentSampled, CustomSamplingContext())))
    }

    @Test
    fun `when tracing decision is set on SpanContext, sampler uses it as a sampling decision`() {
        val sampler = fixture.getSut()
        val transactionContextNotSampled = TransactionContext("name", "op")
        transactionContextNotSampled.sampled = false
        assertFalse(sampler.sample(SamplingContext(transactionContextNotSampled, CustomSamplingContext())))
        val transactionContextSampled = TransactionContext("name", "op")
        transactionContextSampled.sampled = true
        assertTrue(sampler.sample(SamplingContext(transactionContextSampled, CustomSamplingContext())))
    }
}
