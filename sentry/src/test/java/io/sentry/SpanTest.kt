package io.sentry

import io.sentry.protocol.SentryId
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpanTest {

    private class Fixture {
        val hub = mock<IHub>()

        init {
            whenever(hub.options).thenReturn(
                SentryOptions().apply {
                    dsn = "https://key@sentry.io/proj"
                    isTraceSampling = true
                }
            )
        }

        fun getSut(): Span {
            return Span(
                SentryId(),
                SpanId(),
                SentryTracer(TransactionContext("name", "op"), hub),
                "op",
                hub
            )
        }
    }

    private val fixture = Fixture()

    @Test
    fun `finishing span sets the timestamp`() {
        val span = fixture.getSut()
        span.finish()

        assertNotNull(span.finishDate)
    }

    @Test
    fun `finishing span with status sets the timestamp and status`() {
        val span = fixture.getSut()
        span.finish(SpanStatus.CANCELLED)

        assertNotNull(span.finishDate)
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
        val span = Span(
            traceId,
            parentSpanId,
            SentryTracer(
                TransactionContext("name", "op", TracesSamplingDecision(true)),
                fixture.hub
            ),
            "op",
            fixture.hub
        )
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
    fun `starting a child with different instrumenter no-ops`() {
        val transaction = getTransaction(TransactionContext("name", "op").also { it.instrumenter = Instrumenter.OTEL })
        val span = transaction.startChild("operation", "description")

        span.startChild("op")
        assertEquals(0, transaction.spans.size)
    }

    @Test
    fun `starting a child with same instrumenter adds span to transaction`() {
        val transaction = getTransaction(TransactionContext("name", "op").also { it.instrumenter = Instrumenter.OTEL })
        val span = transaction.startChild("operation", "description", null, Instrumenter.OTEL)

        span.startChild("op", "desc", null, Instrumenter.OTEL)
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
        val transaction = SentryTracer(
            TransactionContext("name", "op"),
            fixture.hub
        )
        val span = transaction.startChild("op")
        val ex = RuntimeException()
        span.throwable = ex
        span.finish()

        verify(fixture.hub).setSpanContext(ex, span, "name")
    }

    @Test
    fun `when finish is called twice, do nothing`() {
        val span = fixture.getSut()
        val ex = RuntimeException()
        span.throwable = ex

        span.finish(SpanStatus.OK)
        val timestamp = span.finishDate

        span.finish(SpanStatus.UNKNOWN_ERROR)

        // call only once
        verify(fixture.hub).setSpanContext(any(), any(), any())
        assertEquals(SpanStatus.OK, span.status)
        assertEquals(timestamp, span.finishDate)
    }

    @Test
    fun `when span is finished, do nothing`() {
        val span = fixture.getSut()
        span.description = "desc"
        span.setTag("myTag", "myValue")
        span.setData("myData", "myValue")
        val ex = RuntimeException()
        span.throwable = ex

        span.finish(SpanStatus.OK)
        assertTrue(span.isFinished)

        assertEquals(NoOpSpan.getInstance(), span.startChild("op", "desc", null, Instrumenter.SENTRY))
        assertEquals(NoOpSpan.getInstance(), span.startChild("op", "desc"))

        span.finish(SpanStatus.UNKNOWN_ERROR)
        span.operation = "newOp"
        span.description = "newDesc"
        span.status = SpanStatus.ABORTED
        span.setTag("myTag", "myNewValue")
        span.throwable = RuntimeException()
        span.setData("myData", "myNewValue")

        assertEquals(SpanStatus.OK, span.status)
        assertEquals("op", span.operation)
        assertEquals("desc", span.description)
        assertEquals("myValue", span.tags["myTag"])
        assertEquals("myValue", span.data["myData"])
        assertEquals(ex, span.throwable)
    }

    @Test
    fun `child trace state is equal to transaction trace state`() {
        val transaction = getTransaction()
        val span = transaction.startChild("operation", "description")

        val transactionTraceContext = transaction.traceContext()
        val spanTraceContext = span.traceContext()
        assertNotNull(transactionTraceContext)
        assertNotNull(spanTraceContext)
        assertEquals(transactionTraceContext.traceId, spanTraceContext.traceId)
        assertEquals(transactionTraceContext.transaction, spanTraceContext.transaction)
        assertEquals(transactionTraceContext.environment, spanTraceContext.environment)
        assertEquals(transactionTraceContext.release, spanTraceContext.release)
        assertEquals(transactionTraceContext.publicKey, spanTraceContext.publicKey)
        assertEquals(transactionTraceContext.sampleRate, spanTraceContext.sampleRate)
        assertEquals(transactionTraceContext.userId, spanTraceContext.userId)
        assertEquals(transactionTraceContext.userSegment, spanTraceContext.userSegment)
    }

    @Test
    fun `child trace state header is equal to transaction trace state header`() {
        val transaction = getTransaction()
        val span = transaction.startChild("operation", "description")

        assertNotNull(transaction.toBaggageHeader(null)) {
            assertEquals(it.value, span.toBaggageHeader(null)!!.value)
        }
    }

    @Test
    fun `updateEndDate is ignored and returns false if span is not finished`() {
        val span = fixture.getSut()
        assertFalse(span.isFinished)
        assertNull(span.finishDate)
        assertFalse(span.updateEndDate(mock()))
        assertNull(span.finishDate)
    }

    @Test
    fun `updateEndDate updates finishDate and returns true if span is finished`() {
        val span = fixture.getSut()
        val endDate: SentryDate = mock()
        span.finish()
        assertTrue(span.isFinished)
        assertNotNull(span.finishDate)
        assertTrue(span.updateEndDate(endDate))
        assertEquals(endDate, span.finishDate)
    }

    private fun getTransaction(transactionContext: TransactionContext = TransactionContext("name", "op")): SentryTracer {
        return SentryTracer(transactionContext, fixture.hub)
    }

    private fun startChildFromSpan(): Span {
        val transaction = getTransaction()
        return transaction.startChild("op") as Span
    }
}
