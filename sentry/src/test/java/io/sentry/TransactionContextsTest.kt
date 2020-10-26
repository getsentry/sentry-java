package io.sentry

import io.sentry.protocol.SentryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class TransactionContextsTest {

    @Test
    fun `creates context from correct traceparent header`() {
        val traceId = SentryId()
        val spanId = SpanId()
        val contexts = TransactionContexts.fromTraceparent("$traceId-$spanId")
        assertEquals(contexts.trace.traceId, traceId)
        assertEquals(contexts.trace.parentSpanId, spanId)
        assertNotNull(contexts.trace.spanId)
    }

    @Test
    fun `when traceparent header is incorrect throws exception`() {
        val sentryId = SentryId()
        val ex = assertFailsWith<IllegalArgumentException> { TransactionContexts.fromTraceparent("$sentryId") }
        assertEquals("Traceparent header does not conform to expected format: $sentryId", ex.message)
    }
}
