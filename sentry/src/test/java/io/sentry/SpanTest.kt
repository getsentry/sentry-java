package io.sentry

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.sentry.protocol.SentryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SpanTest {

    private class Fixture {
        val hub = mock<IHub>()

        fun getSut(): Span {
            return Span(SentryId(), SpanId(),
                    SentryTransaction("name", "op"), "op", hub)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `finishing span sets the timestamp`() {
        val span = fixture.getSut()
        span.finish()

        assertNotNull(span.timestamp)
    }

    @Test
    fun `finishing span with status sets the timestamp and status`() {
        val span = fixture.getSut()
        span.finish(SpanStatus.CANCELLED)

        assertNotNull(span.timestamp)
        assertEquals(SpanStatus.CANCELLED, span.status)
    }

    @Test
    fun `starting a child sets parent span id`() {
        val span = fixture.getSut()
        val child = span.startChild("op") as Span

        assertEquals(span.spanId, child.parentSpanId)
    }

    @Test
    fun `starting a child adds span to transaction`() {
        val transaction = getTransaction()
        val span = transaction.startChild("op")
        span.startChild("op")

        assertEquals(2, transaction.spans.size)
    }

    @Test
    fun `starting a child creates a new span`() {
        val span = fixture.getSut()

        val child = span.startChild("child-op", "description") as Span

        assertEquals(span.spanId, child.parentSpanId)
        assertEquals("child-op", child.operation)
        assertEquals("description", child.description)
    }

    @Test
    fun `converts to Sentry trace header`() {
        val traceId = SentryId()
        val parentSpanId = SpanId()
        val span = Span(traceId, parentSpanId,
                SentryTransaction(
                        TransactionContext("name", "op", true), fixture.hub),
                "op", fixture.hub)
        val sentryTrace = span.toSentryTrace()

        assertEquals(traceId, sentryTrace.traceId)
        assertEquals(span.spanId, sentryTrace.spanId)
        assertNotNull(sentryTrace.isSampled) {
            assertTrue(it)
        }
    }

    @Test
    fun `starting a child with details adds span to transaction`() {
        val transaction = getTransaction()
        val span = transaction.startChild("operation", "description")

        span.startChild("op")
        assertEquals(2, transaction.spans.size)
    }

    @Test
    fun `when span was not finished, isFinished returns false`() {
        val span = startChildFromSpan()

        assertFalse(span.isFinished)
    }

    @Test
    fun `when span was finished, isFinished returns true`() {
        val span = startChildFromSpan()
        span.finish()

        assertTrue(span.isFinished)
    }

    @Test
    fun `when span has throwable set set, it assigns itself to throwable on the Hub`() {
        val transaction = SentryTransaction(
                TransactionContext("name", "op"), fixture.hub)
        val span = transaction.startChild("op")
        val ex = RuntimeException()
        span.throwable = ex
        span.finish()

        verify(fixture.hub).setSpanContext(ex, span)
    }

    @Test
    fun `when finish is called twice, do nothing`() {
        val span = fixture.getSut()
        val ex = RuntimeException()
        span.throwable = ex

        span.finish(SpanStatus.OK)
        val timestamp = span.timestamp

        span.finish(SpanStatus.UNKNOWN_ERROR)

        // call only once
        verify(fixture.hub).setSpanContext(any(), any())
        assertEquals(SpanStatus.OK, span.status)
        assertEquals(timestamp, span.timestamp)
    }

    private fun getTransaction(): SentryTransaction {
        return SentryTransaction("name", "op")
    }

    private fun startChildFromSpan(): Span {
        val transaction = getTransaction()
        return transaction.startChild("op") as Span
    }
}
