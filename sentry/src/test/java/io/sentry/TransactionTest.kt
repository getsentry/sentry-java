package io.sentry

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
        assertEquals(transaction.contexts.trace.tags["tag1"], clone.contexts.trace.tags["tag1"])
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
}
