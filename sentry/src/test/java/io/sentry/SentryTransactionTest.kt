package io.sentry

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentryTransactionTest {

    private class Fixture {
        val hub = mock<IHub>()

        fun getSut(): SentryTransaction {
            return SentryTransaction("name", "op", hub)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when transaction is created, startTimestamp is set`() {
        val transaction = fixture.getSut()

        assertNotNull(transaction.startTimestamp)
    }

    @Test
    fun `when transaction is created, timestamp is not set`() {
        val transaction = fixture.getSut()

        assertNull(transaction.timestamp)
    }

    @Test
    fun `when transaction is created, context is set`() {
        val transaction = fixture.getSut()

        assertNotNull(transaction.contexts)
    }

    @Test
    fun `when transaction is created, by default is not sampled`() {
        val transaction = fixture.getSut()

        assertNull(transaction.isSampled)
    }

    @Test
    fun `when transaction is finished, timestamp is set`() {
        val transaction = fixture.getSut()
        transaction.finish()

        assertNotNull(transaction.timestamp)
    }

    @Test
    fun `when transaction is finished with status, timestamp and status are set`() {
        val transaction = fixture.getSut()
        transaction.finish(SpanStatus.ABORTED)

        assertNotNull(transaction.timestamp)
        assertEquals(SpanStatus.ABORTED, transaction.status)
    }

    @Test
    fun `when transaction is finished, transaction is captured`() {
        val transaction = fixture.getSut()
        transaction.finish()

        verify(fixture.hub).captureTransaction(transaction, null)
    }

    @Test
    fun `when transaction with throwable set is finished, span context is associated with throwable`() {
        val transaction = fixture.getSut()
        val ex = RuntimeException()
        transaction.throwable = ex
        transaction.finish()

        verify(fixture.hub).setSpanContext(ex, transaction)
    }

    @Test
    fun `returns sentry-trace header`() {
        val transaction = fixture.getSut()

        assertNotNull(transaction.toSentryTrace())
    }

    @Test
    fun `starting child creates a new span`() {
        val transaction = fixture.getSut()
        val span = startChildFromTransaction(transaction)

        assertNotNull(span)
        assertNotNull(span.spanId)
        assertNotNull(span.startTimestamp)
    }

    @Test
    fun `starting child adds a span to transaction`() {
        val transaction = fixture.getSut()
        val span = startChildFromTransaction(transaction)

        assertEquals(1, transaction.spans.size)
        assertEquals(span, transaction.spans.first())
    }

    @Test
    fun `span created with startChild has parent span id the same as transaction span id`() {
        val transaction = fixture.getSut()
        val span = startChildFromTransaction(transaction)

        assertEquals(transaction.spanId, span.parentSpanId)
    }

    @Test
    fun `span created with startChild has the same trace id as transaction`() {
        val transaction = fixture.getSut()
        val span = startChildFromTransaction(transaction)

        assertEquals(transaction.traceId, span.traceId)
    }

    @Test
    fun `starting child with operation and description creates a new span`() {
        val transaction = fixture.getSut()
        val span = startChildFromTransaction(transaction)

        assertNotNull(span)
        assertNotNull(span.spanId)
        assertNotNull(span.startTimestamp)
        assertEquals("op", span.operation)
        assertEquals("description", span.description)
    }

    @Test
    fun `starting child with operation and description adds a span to transaction`() {
        val transaction = fixture.getSut()
        val span = startChildFromTransaction(transaction)

        assertEquals(1, transaction.spans.size)
        assertEquals(span, transaction.spans.first())
    }

    @Test
    fun `span created with startChild with operation and description has parent span id the same as transaction span id`() {
        val transaction = fixture.getSut()
        val span = startChildFromTransaction(transaction)

        assertEquals(transaction.spanId, span.parentSpanId)
    }

    @Test
    fun `span created with startChild with operation and description has the same trace id as transaction`() {
        val transaction = fixture.getSut()
        val span = startChildFromTransaction(transaction)

        assertEquals(transaction.traceId, span.traceId)
    }

    @Test
    fun `setting op sets op on TraceContext`() {
        val transaction = fixture.getSut()
        transaction.operation = "op"
        transaction.finish()

        assertEquals("op", transaction.contexts.trace!!.operation)
    }

    @Test
    fun `setting description sets description on TraceContext`() {
        val transaction = fixture.getSut()
        transaction.description = "desc"
        transaction.finish()

        assertEquals("desc", transaction.contexts.trace!!.description)
    }

    @Test
    fun `setting status sets status on TraceContext`() {
        val transaction = fixture.getSut()
        transaction.status = SpanStatus.ALREADY_EXISTS
        transaction.finish()

        assertEquals(SpanStatus.ALREADY_EXISTS, transaction.contexts.trace!!.status)
    }

    @Test
    fun `setName overwrites the transaction name`() {
        val transaction = fixture.getSut()
        transaction.name = "new name"

        assertEquals("new name", transaction.transaction)
    }

    @Test
    fun `when transaction is not finished, status is null`() {
        val transaction = fixture.getSut()

        assertNull(transaction.status)
    }

    @Test
    fun `when transaction is not finished, status can be read`() {
        val transaction = fixture.getSut()
        transaction.status = SpanStatus.ABORTED

        assertEquals(SpanStatus.ABORTED, transaction.status)
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
        verify(fixture.hub).setSpanContext(ex, transaction)
        verify(fixture.hub).captureTransaction(transaction, null)

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

    private fun startChildFromTransaction(transaction: SentryTransaction): Span {
        return transaction.startChild("op", "description") as Span
    }
}
