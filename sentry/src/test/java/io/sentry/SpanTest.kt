package io.sentry

import io.sentry.protocol.SentryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SpanTest {

    @Test
    fun `finishing span sets the timestamp`() {
        val span = Span(SentryId(), SpanId(), Transaction())
        span.finish()
        assertNotNull(span.timestamp)
    }

    @Test
    fun `starting a child sets parent span id`() {
        val span = Span(SentryId(), SpanId(), Transaction())
        val child = span.startChild()
        assertEquals(span.spanId, child.parentSpanId)
    }

    @Test
    fun `starting a child adds span to transaction`() {
        val transaction = Transaction()
        val span = transaction.startChild()
        span.startChild()
        assertEquals(2, transaction.spans.size)
    }
}
