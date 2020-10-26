package io.sentry

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull

class TransactionTest {

    @Test
    fun `when transaction is created, startTimestamp is set`() {
        val transaction = Transaction()
        assertNotNull(transaction.startTimestamp)
    }

    @Test
    fun `when transaction is created, timestamp is not set`() {
        val transaction = Transaction()
        assertNull(transaction.timestamp)
    }

    @Test
    fun `when transaction is created, context is set`() {
        val transaction = Transaction()
        assertNotNull(transaction.contexts)
    }

    @Test
    fun `when transaction is finished, timestamp is set`() {
        val transaction = Transaction()
        transaction.finish()
        assertNotNull(transaction.timestamp)
    }

    @Test
    fun `when transaction is finished, transaction is captured`() {
        val hub = mock<IHub>()
        val transaction = Transaction(TransactionContexts(), hub)
        transaction.finish()
        verify(hub).captureTransaction(transaction, null)
    }

    @Test
    fun `cloned transaction has the same values`() {
        val transaction = Transaction()
        transaction.setName("custom name")
        transaction.contexts = TransactionContexts()
        val trace = Trace()
        trace.op = "http"
        trace.description = "some description"
        trace.status = SpanStatus.CANCELLED
        trace.unknown = mapOf("unknown-key" to "unknown-value")
        trace.tags = mapOf("tag1" to "tag2")
        transaction.contexts.trace = trace

        val clone = transaction.clone()

        assertEquals(transaction.transaction, clone.transaction)
        assertEquals(transaction.contexts.trace.op, clone.contexts.trace.op)
        assertEquals(transaction.contexts.trace.description, clone.contexts.trace.description)
        assertEquals(transaction.contexts.trace.status, clone.contexts.trace.status)
        assertEquals(transaction.contexts.trace.unknown["unknown-key"], clone.contexts.trace.unknown["unknown-key"])
        assertEquals(transaction.contexts.trace.tags!!["tag1"], clone.contexts.trace.tags!!["tag1"])
    }

    @Test
    fun `cloned transaction does not have the same references`() {
        val transaction = Transaction()
        transaction.setName("custom name")
        transaction.contexts = TransactionContexts()
        val trace = Trace()
        trace.op = "http"
        trace.description = "some description"
        trace.status = SpanStatus.CANCELLED
        trace.unknown = mapOf("unknown-key" to "unknown-value")
        trace.tags = mapOf("tag1" to "tag2")
        transaction.contexts.trace = trace

        val clone = transaction.clone()

        assertNotSame(transaction.contexts, clone.contexts)
        assertNotSame(transaction.contexts.trace, clone.contexts.trace)
    }

    @Test
    fun `returns sentry-trace header`() {
        val transaction = Transaction()
        transaction.contexts = TransactionContexts()
        val trace = Trace()
        transaction.contexts.trace = trace

        assertNotNull(transaction.toTraceparent())
        assertEquals("${trace.traceId}-${trace.spanId}", transaction.toTraceparent())
    }

    @Test
    fun `starting child creates a new span`() {
        val transaction = Transaction()
        val span = transaction.startChild()
        assertNotNull(span)
        assertNotNull(span.spanId)
        assertNotNull(span.startTimestamp)
    }

    @Test
    fun `starting child adds a span to transaction`() {
        val transaction = Transaction()
        val span = transaction.startChild()
        assertEquals(1, transaction.spans.size)
        assertEquals(span, transaction.spans.first())
    }

    @Test
    fun `span created with startChild has parent span id the same as transaction span id`() {
        val transaction = Transaction()
        val span = transaction.startChild()
        assertEquals(transaction.spanId, span.parentSpanId)
    }

    @Test
    fun `span created with startChild has the same trace id as transaction`() {
        val transaction = Transaction()
        val span = transaction.startChild()
        assertEquals(transaction.traceId, span.traceId)
    }
}
