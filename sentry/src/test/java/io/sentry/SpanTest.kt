package io.sentry

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.protocol.SentryId
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
                SentryId(), SpanId(),
                SentryTracer(TransactionContext("name", "op"), hub), "op", hub
            )
        }
    }

    private val fixture = Fixture()

    @Test
    fun `finishing span sets the timestamp`() {
        val span = fixture.getSut()
        span.finish()

        assertNotNull(span.timestamp)
        assertNotNull(span.highPrecisionTimestamp)
    }

    @Test
    fun `when span is created without a start timestamp, high precision timestamp is more precise than timestamp`() {
        val span = fixture.getSut().startChild("op", "desc") as Span
        span.finish()

        assertNotNull(span.highPrecisionTimestamp) { highPrecisionTimestamp ->
            assertNotNull(span.timestamp) { timestamp ->
                assertTrue(highPrecisionTimestamp >= timestamp - 0.001)
                assertTrue(highPrecisionTimestamp <= timestamp + 0.001)
            }
        }
    }

    @Test
    fun `when span is created with a start timestamp, finish timestamp is equals to high precision timestamp`() {
        val span = fixture.getSut().startChild("op", "desc", Date()) as Span
        span.finish()

        assertNotNull(span.timestamp)
        assertNotNull(span.highPrecisionTimestamp)
        assertEquals(span.timestamp, span.highPrecisionTimestamp)
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
        val span = Span(
            traceId, parentSpanId,
            SentryTracer(
                TransactionContext("name", "op", TracesSamplingDecision(true)), fixture.hub
            ),
            "op", fixture.hub
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
            TransactionContext("name", "op"), fixture.hub
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
        val timestamp = span.timestamp
        val highPrecisionTimestamp = span.highPrecisionTimestamp

        span.finish(SpanStatus.UNKNOWN_ERROR)

        // call only once
        verify(fixture.hub).setSpanContext(any(), any(), any())
        assertEquals(SpanStatus.OK, span.status)
        assertEquals(timestamp, span.timestamp)
        assertEquals(highPrecisionTimestamp, span.highPrecisionTimestamp)
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

        assertEquals(NoOpSpan.getInstance(), span.startChild("op", "desc", null))
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

        assertEquals(transaction.traceContext(), span.traceContext())
    }

    @Test
    fun `child trace state header is equal to transaction trace state header`() {
        val transaction = getTransaction()
        val span = transaction.startChild("operation", "description")

        assertNotNull(transaction.toBaggageHeader(null)) {
            assertEquals(it.value, span.toBaggageHeader(null)!!.value)
        }
    }

    private fun getTransaction(): SentryTracer {
        return SentryTracer(TransactionContext("name", "op"), fixture.hub)
    }

    private fun startChildFromSpan(): Span {
        val transaction = getTransaction()
        return transaction.startChild("op") as Span
    }
}
