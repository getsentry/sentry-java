package io.sentry

import io.sentry.protocol.SentryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TransactionContextsTest {

    @Test
    fun `creates context from correct not sampled sentry-trace header`() {
        val traceId = SentryId()
        val spanId = SpanId()
        val contexts = TransactionContexts.fromSentryTrace(SentryTraceHeader("$traceId-$spanId-0"))
        assertEquals(traceId, contexts.traceContext.traceId)
        assertEquals(spanId, contexts.traceContext.parentSpanId)
        assertEquals(false, contexts.sampled)
        assertNotNull(contexts.traceContext.spanId)
    }

    @Test
    fun `creates context from correct sampled sentry-trace header`() {
        val traceId = SentryId()
        val spanId = SpanId()
        val contexts = TransactionContexts.fromSentryTrace(SentryTraceHeader("$traceId-$spanId-1"))
        assertEquals(true, contexts.sampled)
    }

    @Test
    fun `creates context from correct sentry-trace header without sampling decision`() {
        val traceId = SentryId()
        val spanId = SpanId()
        val contexts = TransactionContexts.fromSentryTrace(SentryTraceHeader("$traceId-$spanId"))
        assertNull(contexts.sampled)
    }

    @Test
    fun `when sentry-trace header is incorrect throws exception`() {
        val sentryId = SentryId()
        val ex = assertFailsWith<InvalidSentryTraceHeaderException> { TransactionContexts.fromSentryTrace(SentryTraceHeader("$sentryId")) }
        assertEquals("sentry-trace header does not conform to expected format: $sentryId", ex.message)
    }
}
