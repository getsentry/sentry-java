package io.sentry

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentryTracerTest {

    private class Fixture {
        val hub: Hub

        init {
            val options = SentryOptions()
            options.dsn = "https://key@sentry.io/proj"
            hub = spy(Hub(options))
            hub.bindClient(mock())
        }

        fun getSut(): SentryTracer {
            return SentryTracer(TransactionContext("name", "op"), hub)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when transaction is created, startTimestamp is set`() {
        val tracer = fixture.getSut()
        assertNotNull(tracer.startTimestamp)
    }

    @Test
    fun `when transaction is created, timestamp is not set`() {
        val tracer = fixture.getSut()
        assertNull(tracer.timestamp)
    }

    @Test
    fun `when transaction is created, context is set`() {
        val tracer = fixture.getSut()
        assertNotNull(tracer.spanContext)
    }

    @Test
    fun `when transaction is created, by default is not sampled`() {
        val tracer = fixture.getSut()
        assertNull(tracer.isSampled)
    }

    @Test
    fun `when transaction is finished, timestamp is set`() {
        val tracer = fixture.getSut()
        tracer.finish()
        assertNotNull(tracer.timestamp)
    }

    @Test
    fun `when transaction is finished with status, timestamp and status are set`() {
        val tracer = fixture.getSut()
        tracer.finish(SpanStatus.ABORTED)
        assertNotNull(tracer.timestamp)
        assertEquals(SpanStatus.ABORTED, tracer.status)
    }

    @Test
    fun `when transaction is finished, transaction is captured`() {
        val tracer = fixture.getSut()
        tracer.finish()
        verify(fixture.hub).captureTransaction(any())
    }

    @Test
    fun `when transaction is finished, transaction is cleared from the scope`() {
        val tracer = fixture.getSut()
        fixture.hub.configureScope { it.setTransaction(tracer) }
        tracer.finish()
        assertNull(fixture.hub.span)
    }

    @Test
    fun `when transaction with throwable set is finished, span context is associated with throwable`() {
        val tracer = fixture.getSut()
        val ex = RuntimeException()
        tracer.throwable = ex
        tracer.finish()
        verify(fixture.hub).setSpanContext(ex, tracer.root)
    }

    @Test
    fun `returns sentry-trace header`() {
        val tracer = fixture.getSut()

        assertNotNull(tracer.toSentryTrace())
    }

    @Test
    fun `starting child creates a new span`() {
        val tracer = fixture.getSut()
        val span = tracer.startChild("op") as Span
        assertNotNull(span)
        assertNotNull(span.spanId)
        assertNotNull(span.startTimestamp)
    }

    @Test
    fun `starting child adds a span to transaction`() {
        val tracer = fixture.getSut()
        val span = tracer.startChild("op")
        assertEquals(1, tracer.children.size)
        assertEquals(span, tracer.children.first())
    }

    @Test
    fun `span created with startChild has parent span id the same as transaction span id`() {
        val tracer = fixture.getSut()
        val span = tracer.startChild("op") as Span
        assertEquals(tracer.root.spanId, span.parentSpanId)
    }

    @Test
    fun `span created with startChild has the same trace id as transaction`() {
        val tracer = fixture.getSut()
        val span = tracer.startChild("op") as Span
        assertEquals(tracer.root.traceId, span.traceId)
    }

    @Test
    fun `starting child with operation and description creates a new span`() {
        val tracer = fixture.getSut()
        val span = tracer.startChild("op", "description") as Span
        assertNotNull(span)
        assertNotNull(span.spanId)
        assertNotNull(span.startTimestamp)
        assertEquals("op", span.operation)
        assertEquals("description", span.description)
    }

    @Test
    fun `starting child with operation and description adds a span to transaction`() {
        val tracer = fixture.getSut()
        val span = tracer.startChild("op", "description")
        assertEquals(1, tracer.children.size)
        assertEquals(span, tracer.children.first())
    }

    @Test
    fun `span created with startChild with operation and description has parent span id the same as transaction span id`() {
        val tracer = fixture.getSut()
        val span = tracer.startChild("op", "description") as Span
        assertEquals(tracer.root.spanId, span.parentSpanId)
    }

    @Test
    fun `span created with startChild with operation and description has the same trace id as transaction`() {
        val tracer = fixture.getSut()
        val span = tracer.startChild("op", "description") as Span
        assertEquals(tracer.root.traceId, span.traceId)
    }

    @Test
    fun `setting op sets op on TraceContext`() {
        val tracer = fixture.getSut()
        tracer.operation = "op"
        tracer.finish()
        assertEquals("op", tracer.spanContext.operation)
    }

    @Test
    fun `setting description sets description on TraceContext`() {
        val tracer = fixture.getSut()
        tracer.description = "desc"
        tracer.finish()
        assertEquals("desc", tracer.spanContext.description)
    }

    @Test
    fun `setting status sets status on TraceContext`() {
        val tracer = fixture.getSut()
        tracer.status = SpanStatus.ALREADY_EXISTS
        tracer.finish()
        assertEquals(SpanStatus.ALREADY_EXISTS, tracer.spanContext.status)
    }

    @Test
    fun `when transaction is not finished, status is null`() {
        val tracer = fixture.getSut()
        assertNull(tracer.status)
    }

    @Test
    fun `when transaction is not finished, status can be read`() {
        val tracer = fixture.getSut()
        tracer.status = SpanStatus.ABORTED
        assertEquals(SpanStatus.ABORTED, tracer.status)
    }

    @Test
    fun `when finish is called twice, do nothing`() {
        val transaction = fixture.getSut()
        val ex = RuntimeException()
        transaction.throwable = ex

        transaction.finish(SpanStatus.OK)
        val timestamp = transaction.timestamp

        transaction.finish(SpanStatus.UNKNOWN_ERROR)

        // call only once
        verify(fixture.hub).setSpanContext(ex, transaction.root)
        verify(fixture.hub).captureTransaction(check {
            assertEquals(transaction.root.spanContext, it.contexts.trace)
        })

        assertEquals(SpanStatus.OK, transaction.status)
        assertEquals(timestamp, transaction.timestamp)
    }

    @Test
    fun `when transaction was not finished, isFinished returns false`() {
        val transaction = fixture.getSut()

        assertFalse(transaction.isFinished)
    }

    @Test
    fun `when span was finished, isFinished returns true`() {
        val transaction = fixture.getSut()
        transaction.finish()

        assertTrue(transaction.isFinished)
    }
}
