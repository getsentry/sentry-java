package io.sentry

import io.sentry.protocol.SentryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TransactionContextsTest {

    @Test
    fun `creates context from correct not sampled sentry-trace header`() {
        val traceId = SentryId()
        val spanId = SpanId()
        val contexts = TransactionContexts.fromSentryTrace(SentryTraceHeader("$traceId-$spanId-0"))
        assertEquals(contexts.traceContext.traceId, traceId)
        assertEquals(contexts.traceContext.parentSpanId, spanId)
        assertEquals(contexts.traceContext.isSampled, false)
        assertNotNull(contexts.traceContext.spanId)
    }

    @Test
    fun `creates context from correct sampled sentry-trace header`() {
        val traceId = SentryId()
        val spanId = SpanId()
        val contexts = TransactionContexts.fromSentryTrace(SentryTraceHeader("$traceId-$spanId-1"))
        assertEquals(contexts.traceContext.isSampled, true)
    }
}
