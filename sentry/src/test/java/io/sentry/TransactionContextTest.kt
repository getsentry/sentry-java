package io.sentry

import io.sentry.protocol.SentryId
import org.mockito.kotlin.mock
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
        assertFalse(context.isForNextAppStart)
    }

    @Test
    fun `when context is created from propagation context, parent sampling decision of false is set from trace header`() {
        val logger = mock<ILogger>()
        val propagationContext = PropagationContext.fromHeaders(
            logger,
            SentryTraceHeader(SentryId(), SpanId(), false).value,
            "sentry-trace_id=a,sentry-transaction=sentryTransaction,sentry-sample_rate=0.3"
        )
        val context = TransactionContext.fromPropagationContext(propagationContext)
        assertNull(context.sampled)
        assertNull(context.profileSampled)
        assertFalse(context.parentSampled!!)
        assertEquals(0.3, context.parentSamplingDecision!!.sampleRate)
        assertFalse(context.isForNextAppStart)
    }

    @Test
    fun `when context is created from propagation context, parent sampling decision of false is set from trace header if no sample rate is available`() {
        val logger = mock<ILogger>()
        val propagationContext = PropagationContext.fromHeaders(
            logger,
            SentryTraceHeader(SentryId(), SpanId(), false).value,
            "sentry-trace_id=a,sentry-transaction=sentryTransaction"
        )
        val context = TransactionContext.fromPropagationContext(propagationContext)
        assertNull(context.sampled)
        assertNull(context.profileSampled)
        assertFalse(context.parentSampled!!)
        assertNull(context.parentSamplingDecision!!.sampleRate)
        assertFalse(context.isForNextAppStart)
    }

    @Test
    fun `when context is created from propagation context, parent sampling decision of true is set from trace header`() {
        val logger = mock<ILogger>()
        val propagationContext = PropagationContext.fromHeaders(
            logger,
            SentryTraceHeader(SentryId(), SpanId(), true).value,
            "sentry-trace_id=a,sentry-transaction=sentryTransaction,sentry-sample_rate=0.3"
        )
        val context = TransactionContext.fromPropagationContext(propagationContext)
        assertNull(context.sampled)
        assertNull(context.profileSampled)
        assertTrue(context.parentSampled!!)
        assertEquals(0.3, context.parentSamplingDecision!!.sampleRate)
        assertFalse(context.isForNextAppStart)
    }

    @Test
    fun `when context is created from propagation context, parent sampling decision of true is set from trace header if no sample rate is available`() {
        val logger = mock<ILogger>()
        val propagationContext = PropagationContext.fromHeaders(
            logger,
            SentryTraceHeader(SentryId(), SpanId(), true).value,
            "sentry-trace_id=a,sentry-transaction=sentryTransaction"
        )
        val context = TransactionContext.fromPropagationContext(propagationContext)
        assertNull(context.sampled)
        assertNull(context.profileSampled)
        assertTrue(context.parentSampled!!)
        assertNull(context.parentSamplingDecision!!.sampleRate)
        assertFalse(context.isForNextAppStart)
    }

    @Test
    fun `setForNextAppStart sets the isForNextAppStart flag`() {
        val context = TransactionContext("name", "op")
        context.isForNextAppStart = true
        assertTrue(context.isForNextAppStart)
    }
}
