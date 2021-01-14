package io.sentry

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SentryTransactionTest {

    @Test
    fun `when transaction is created, startTimestamp is set`() {
        val transaction = SentryTransaction("name")
        assertNotNull(transaction.startTimestamp)
    }

    @Test
    fun `when transaction is created, timestamp is not set`() {
        val transaction = SentryTransaction("name")
        assertNull(transaction.timestamp)
    }

    @Test
    fun `when transaction is created, context is set`() {
        val transaction = SentryTransaction("name")
        assertNotNull(transaction.contexts)
    }

    @Test
    fun `when transaction is created, by default is not sampled`() {
        val transaction = SentryTransaction("name")
        assertNull(transaction.isSampled)
    }

    @Test
    fun `when transaction is finished, timestamp is set`() {
        val transaction = SentryTransaction("name")
        transaction.finish()
        assertNotNull(transaction.timestamp)
    }

    @Test
    fun `when transaction is finished, transaction is captured`() {
        val hub = mock<IHub>()
        val transaction = SentryTransaction("name", SpanContext(), hub)
        transaction.finish()
        verify(hub).captureTransaction(transaction, null)
    }

    @Test
    fun `when transaction with throwable set is finished, span context is associated with throwable`() {
        val hub = mock<IHub>()
        val transaction = SentryTransaction("name", SpanContext(), hub)
        val ex = RuntimeException()
        transaction.setThrowable(ex)
        transaction.finish()
        verify(hub).setSpanContext(ex, transaction)
    }

    @Test
    fun `returns sentry-trace header`() {
        val transaction = SentryTransaction("name")

        assertNotNull(transaction.toSentryTrace())
    }

    @Test
    fun `starting child creates a new span`() {
        val transaction = SentryTransaction("name")
        val span = transaction.startChild()
        assertNotNull(span)
        assertNotNull(span.spanId)
        assertNotNull(span.startTimestamp)
    }

    @Test
    fun `starting child adds a span to transaction`() {
        val transaction = SentryTransaction("name")
        val span = transaction.startChild()
        assertEquals(1, transaction.spans.size)
        assertEquals(span, transaction.spans.first())
    }

    @Test
    fun `span created with startChild has parent span id the same as transaction span id`() {
        val transaction = SentryTransaction("name")
        val span = transaction.startChild()
        assertEquals(transaction.spanId, span.parentSpanId)
    }

    @Test
    fun `span created with startChild has the same trace id as transaction`() {
        val transaction = SentryTransaction("name")
        val span = transaction.startChild()
        assertEquals(transaction.traceId, span.traceId)
    }

    @Test
    fun `starting child with operation and description creates a new span`() {
        val transaction = SentryTransaction("name")
        val span = transaction.startChild("op", "description")
        assertNotNull(span)
        assertNotNull(span.spanId)
        assertNotNull(span.startTimestamp)
        assertEquals("op", span.operation)
        assertEquals("description", span.description)
    }

    @Test
    fun `starting child with operation and description adds a span to transaction`() {
        val transaction = SentryTransaction("name")
        val span = transaction.startChild("op", "description")
        assertEquals(1, transaction.spans.size)
        assertEquals(span, transaction.spans.first())
    }

    @Test
    fun `span created with startChild with operation and description has parent span id the same as transaction span id`() {
        val transaction = SentryTransaction("name")
        val span = transaction.startChild("op", "description")
        assertEquals(transaction.spanId, span.parentSpanId)
    }

    @Test
    fun `span created with startChild with operation and description has the same trace id as transaction`() {
        val transaction = SentryTransaction("name")
        val span = transaction.startChild("op", "description")
        assertEquals(transaction.traceId, span.traceId)
    }

    @Test
    fun `setting op sets op on TraceContext`() {
        val transaction = SentryTransaction("name")
        transaction.setOperation("op")
        assertEquals("op", transaction.contexts.trace!!.operation)
    }

    @Test
    fun `setting description sets description on TraceContext`() {
        val transaction = SentryTransaction("name")
        transaction.setDescription("desc")
        assertEquals("desc", transaction.contexts.trace!!.description)
    }

    @Test
    fun `setting status sets status on TraceContext`() {
        val transaction = SentryTransaction("name")
        transaction.setStatus(SpanStatus.ALREADY_EXISTS)
        assertEquals(SpanStatus.ALREADY_EXISTS, transaction.contexts.trace!!.status)
    }

    @Test
    fun `setName overwrites the transaction name`() {
        val transaction = SentryTransaction("initial name")
        transaction.setName("new name")
        assertEquals("new name", transaction.transaction)
    }
}
