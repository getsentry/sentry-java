package io.sentry

import io.sentry.protocol.SentryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TransactionContextsTest {

    @Test
    fun `creates context from correct sentry-trace header`() {
        val traceId = SentryId()
        val spanId = SpanId()
        val contexts = TransactionContexts.fromSentryTrace(SentryTraceHeader("$traceId-$spanId"))
        assertEquals(contexts.traceContext.traceId, traceId)
        assertEquals(contexts.traceContext.parentSpanId, spanId)
        assertNotNull(contexts.traceContext.spanId)
    }
}
