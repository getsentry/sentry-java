package io.sentry

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.sentry.protocol.SentryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SpanTest {

    @Test
    fun `finishing span sets the timestamp`() {
        val span = Span(SentryId(), SpanId(), SentryTransaction("name"), mock())
        span.finish()
        assertNotNull(span.timestamp)
    }

    @Test
    fun `finishing span with status sets the timestamp and status`() {
        val span = Span(SentryId(), SpanId(), SentryTransaction("name"), mock())
        span.finish(SpanStatus.CANCELLED)
        assertNotNull(span.timestamp)
        assertEquals(SpanStatus.CANCELLED, span.status)
    }

    @Test
    fun `starting a child sets parent span id`() {
        val span = Span(SentryId(), SpanId(), SentryTransaction("name"), mock())
        val child = span.startChild("op") as Span
        assertEquals(span.spanId, child.parentSpanId)
    }

    @Test
    fun `starting a child adds span to transaction`() {
        val transaction = SentryTransaction("name")
        val span = transaction.startChild("op")
        span.startChild("op")
        assertEquals(2, transaction.spans.size)
    }

    @Test
    fun `starting a child creates a new span`() {
        val span = Span(SentryId(), SpanId(), SentryTransaction("name"), mock())
        val child = span.startChild("op", "description") as Span
        assertEquals(span.spanId, child.parentSpanId)
        assertEquals("op", child.operation)
        assertEquals("description", child.description)
    }

    @Test
    fun `converts to Sentry trace header`() {
        val traceId = SentryId()
        val parentSpanId = SpanId()
        val hub = mock<IHub>()
        val span = Span(traceId, parentSpanId, SentryTransaction(TransactionContext("name", true), hub), hub)
        val sentryTrace = span.toSentryTrace()
        assertEquals(traceId, sentryTrace.traceId)
        assertEquals(span.spanId, sentryTrace.spanId)
        assertNotNull(sentryTrace.isSampled) {
            assertTrue(it)
        }
    }

    @Test
    fun `starting a child with details adds span to transaction`() {
        val transaction = SentryTransaction("name")
        val span = transaction.startChild("operation", "description")
        span.startChild("op")
        assertEquals(2, transaction.spans.size)
    }

    @Test
    fun `when span has no timestamp set, it is considered unfinished`() {
        val transaction = SentryTransaction("name")
        val span = transaction.startChild("op") as Span
        assertFalse(span.isFinished)
    }

    @Test
    fun `when span has timestamp set, it is considered finished`() {
        val transaction = SentryTransaction("name")
        val span = transaction.startChild("op") as Span
        span.finish()
        assertTrue(span.isFinished)
    }

    @Test
    fun `when span has throwable set set, it assigns itself to throwable on the Hub`() {
        val hub = mock<IHub>()
        val transaction = SentryTransaction(TransactionContext("name"), hub)
        val span = transaction.startChild("op")
        val ex = RuntimeException()
        span.throwable = ex
        span.finish()
        verify(hub).setSpanContext(ex, span)
    }
}
