package io.sentry

import io.sentry.protocol.SentryId
import io.sentry.protocol.TransactionNameSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransactionContextTest {

    @Test
    fun `when created using primary constructor, sampling decision and parent sampling are not set`() {
        val context = TransactionContext("name", "op")
        assertNull(context.sampled)
        assertNull(context.profileSampled)
        assertNull(context.parentSampled)
        assertEquals("name", context.name)
        assertEquals("op", context.op)
    }

    @Test
    fun `when context is created from trace header, parent sampling decision is set`() {
        val header = SentryTraceHeader(SentryId(), SpanId(), true)
        val context = TransactionContext.fromSentryTrace("name", "op", header)
        assertNull(context.sampled)
        assertNull(context.profileSampled)
        assertTrue(context.parentSampled!!)
    }

    @Test
    fun `when context is created from trace header and baggage header, parent sampling decision of false is set from trace header`() {
        val traceHeader = SentryTraceHeader(SentryId(), SpanId(), false)
        val baggageHeader = Baggage.fromHeader("sentry-trace_id=a,sentry-transaction=sentryTransaction,sentry-sample_rate=0.3")
        val context = TransactionContext.fromSentryTrace("name", TransactionNameSource.CUSTOM, "op", traceHeader, baggageHeader, null)
        assertNull(context.sampled)
        assertNull(context.profileSampled)
        assertFalse(context.parentSampled!!)
        assertEquals(0.3, context.parentSamplingDecision!!.sampleRate)
    }

    @Test
    fun `when context is created from trace header and baggage header, parent sampling decision of false is set from trace header if no sample rate is available`() {
        val traceHeader = SentryTraceHeader(SentryId(), SpanId(), false)
        val baggageHeader = Baggage.fromHeader("sentry-trace_id=a,sentry-transaction=sentryTransaction")
        val context = TransactionContext.fromSentryTrace("name", TransactionNameSource.CUSTOM, "op", traceHeader, baggageHeader, null)
        assertNull(context.sampled)
        assertNull(context.profileSampled)
        assertFalse(context.parentSampled!!)
        assertNull(context.parentSamplingDecision!!.sampleRate)
    }

    @Test
    fun `when context is created from trace header and baggage header, parent sampling decision of true is set from trace header`() {
        val traceHeader = SentryTraceHeader(SentryId(), SpanId(), true)
        val baggageHeader = Baggage.fromHeader("sentry-trace_id=a,sentry-transaction=sentryTransaction,sentry-sample_rate=0.3")
        val context = TransactionContext.fromSentryTrace("name", TransactionNameSource.CUSTOM, "op", traceHeader, baggageHeader, null)
        assertNull(context.sampled)
        assertNull(context.profileSampled)
        assertTrue(context.parentSampled!!)
        assertEquals(0.3, context.parentSamplingDecision!!.sampleRate)
    }

    @Test
    fun `when context is created from trace header and baggage header, parent sampling decision of true is set from trace header if no sample rate is available`() {
        val traceHeader = SentryTraceHeader(SentryId(), SpanId(), true)
        val baggageHeader = Baggage.fromHeader("sentry-trace_id=a,sentry-transaction=sentryTransaction")
        val context = TransactionContext.fromSentryTrace("name", TransactionNameSource.CUSTOM, "op", traceHeader, baggageHeader, null)
        assertNull(context.sampled)
        assertNull(context.profileSampled)
        assertTrue(context.parentSampled!!)
        assertNull(context.parentSamplingDecision!!.sampleRate)
    }
}
