package io.sentry

import io.sentry.protocol.SentryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SpanTest {

    @Test
    fun `finishing span sets the timestamp`() {
        val span = Span(SentryId(), SpanId(), Transaction("name"))
        span.finish()
        assertNotNull(span.timestamp)
    }

    @Test
    fun `starting a child sets parent span id`() {
        val span = Span(SentryId(), SpanId(), Transaction("name"))
        val child = span.startChild()
        assertEquals(span.spanId, child.parentSpanId)
    }

    @Test
    fun `starting a child adds span to transaction`() {
        val transaction = Transaction("name")
        val span = transaction.startChild()
        span.startChild()
        assertEquals(2, transaction.spans.size)
    }

    @Test
    fun `when span has no timestamp set, it is considered unfinished`() {
        val transaction = Transaction("name")
        val span = transaction.startChild()
        assertFalse(span.isFinished)
    }

    @Test
    fun `when span has timestamp set, it is considered finished`() {
        val transaction = Transaction("name")
        val span = transaction.startChild()
        span.finish()
        assertTrue(span.isFinished)
    }
}
