package io.sentry

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TransactionTest {

    @Test
    fun `when transaction is created, startTimestamp is set`() {
        val transaction = Transaction("name")
        assertNotNull(transaction.startTimestamp)
    }

    @Test
    fun `when transaction is created, timestamp is not set`() {
        val transaction = Transaction("name")
        assertNull(transaction.timestamp)
    }

    @Test
    fun `when transaction is created, context is set`() {
        val transaction = Transaction("name")
        assertNotNull(transaction.contexts)
    }

    @Test
    fun `when transaction is finished, timestamp is set`() {
        val transaction = Transaction("name")
        transaction.finish()
        assertNotNull(transaction.timestamp)
    }

    @Test
    fun `when transaction is finished, transaction is captured`() {
        val hub = mock<IHub>()
        val transaction = Transaction("name", TransactionContexts(), hub)
        transaction.finish()
        verify(hub).captureTransaction(transaction, null)
    }

    @Test
    fun `returns sentry-trace header`() {
        val transaction = Transaction("name")

        assertNotNull(transaction.toSentryTrace())
        assertEquals("${transaction.contexts.trace.traceId}-${transaction.contexts.trace.spanId}", transaction.toSentryTrace())
    }

    @Test
    fun `starting child creates a new span`() {
        val transaction = Transaction("name")
        val span = transaction.startChild()
        assertNotNull(span)
        assertNotNull(span.spanId)
        assertNotNull(span.startTimestamp)
    }

    @Test
    fun `starting child adds a span to transaction`() {
        val transaction = Transaction("name")
        val span = transaction.startChild()
        assertEquals(1, transaction.spans.size)
        assertEquals(span, transaction.spans.first())
    }

    @Test
    fun `span created with startChild has parent span id the same as transaction span id`() {
        val transaction = Transaction("name")
        val span = transaction.startChild()
        assertEquals(transaction.spanId, span.parentSpanId)
    }

    @Test
    fun `span created with startChild has the same trace id as transaction`() {
        val transaction = Transaction("name")
        val span = transaction.startChild()
        assertEquals(transaction.traceId, span.traceId)
    }
}
