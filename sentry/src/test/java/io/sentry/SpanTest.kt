package io.sentry

import io.sentry.protocol.SentryId
import io.sentry.test.injectForField
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SpanTest {

    private class Fixture {
        val scopes = mock<IScopes>()

        init {
            whenever(scopes.options).thenReturn(
                SentryOptions().apply {
                    dsn = "https://key@sentry.io/proj"
                    isTraceSampling = true
                }
            )
        }

        fun getSut(options: SpanOptions = SpanOptions()): Span {
            val context = SpanContext(
                SentryId(),
                SpanId(),
                SpanId(),
                "op",
                null,
                null,
                null,
                null
            )
            return Span(
                SentryTracer(TransactionContext("name", "op"), scopes),
                scopes,
                context,
                options,
                null
            )
        }

        fun getRootSut(options: TransactionOptions = TransactionOptions()): Span {
            return SentryTracer(TransactionContext("name", "op"), scopes, options).root
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
        val spanContext = SpanContext(
            traceId,
            SpanId(),
            parentSpanId,
            "op",
            null,
            TracesSamplingDecision(true),
            null,
            null
        )
        val span = Span(
            SentryTracer(
                TransactionContext("name", "op", TracesSamplingDecision(true)),
                fixture.scopes
            ),
            fixture.scopes,
            spanContext,
            SpanOptions(),
            null
        )
        val sentryTrace = span.toSentryTrace()

        assertEquals(traceId, sentryTrace.traceId)
        assertEquals(span.spanId, sentryTrace.spanId)
        assertNotNull(sentryTrace.isSampled) {
            assertTrue(it)
        }
    }

    @Test
    fun `transfers span origin from options to span context`() {
        val traceId = SentryId()
        val parentSpanId = SpanId()
        val spanContext = SpanContext(
            traceId,
            SpanId(),
            parentSpanId,
            "op",
            null,
            TracesSamplingDecision(true),
            null,
            "old-origin"
        )

        val spanOptions = SpanOptions()
        spanOptions.origin = "new-origin"

        val span = Span(
            SentryTracer(
                TransactionContext("name", "op", TracesSamplingDecision(true)),
                fixture.scopes
            ),
            fixture.scopes,
            spanContext,
            spanOptions,
            null
        )

        assertEquals("new-origin", span.spanContext.origin)
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
    fun `when span has throwable set set, it assigns itself to throwable on the Scopes`() {
        val transaction = SentryTracer(
            TransactionContext("name", "op"),
            fixture.scopes
        )
        val span = transaction.startChild("op")
        val ex = RuntimeException()
        span.throwable = ex
        span.finish()

        verify(fixture.scopes).setSpanContext(ex, span, "name")
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
        verify(fixture.scopes).setSpanContext(any(), any(), any())
        assertEquals(SpanStatus.OK, span.status)
        assertEquals(timestamp, span.finishDate)
    }

    @Test
    fun `when span is finished, start child is a no-op`() {
        val span = fixture.getSut()
        span.finish(SpanStatus.OK)
        val child = span.startChild("op.invalid")
        assertTrue(child is NoOpSpan)
    }

    @Test
    fun `when span is finished, extra data can still be modified`() {
        val span = fixture.getSut()
        span.description = "desc"
        span.setTag("myTag", "myValue")
        span.setData("myData", "myValue")
        val ex = RuntimeException()
        span.throwable = ex

        span.finish(SpanStatus.OK)

        span.status = SpanStatus.ABORTED
        span.operation = "newOp"
        span.description = "newDesc"
        span.status = SpanStatus.ABORTED
        span.setTag("myTag", "myNewValue")
        val newEx = RuntimeException()
        span.throwable = newEx
        span.setData("myData", "myNewValue")

        assertEquals(SpanStatus.ABORTED, span.status)
        assertEquals("newOp", span.operation)
        assertEquals("newDesc", span.description)
        assertEquals("myNewValue", span.tags["myTag"])
        assertEquals("myNewValue", span.data["myData"])
        assertSame(newEx, span.throwable)
    }

    @Test
    fun `when span trim-start is enabled, trim to start of child span`() {
        // when trim start is enabled
        val span = fixture.getSut(
            SpanOptions().apply {
                isTrimStart = true
            }
        )

        // and a child span is created
        Thread.sleep(1)
        val child1 = span.startChild("op1") as Span
        child1.finish()

        val finishDate = SentryInstantDateProvider().now()
        span.finish(SpanStatus.OK, finishDate)

        // then the span start should match the child
        // but the finish date should be kept the same
        assertEquals(child1.startDate, span.startDate)
        assertEquals(finishDate, span.finishDate)
    }

    @Test
    fun `when span trim-start is enabled, do not trim to start of child span if it started earlier`() {
        // when trim start is enabled
        val span = fixture.getSut(
            SpanOptions().apply {
                isTrimStart = true
            }
        )
        val startDate = span.startDate

        // and a child span is created but has an earlier timestamp
        val child1 = span.startChild(
            "op1",
            "desc",
            SentryLongDate(span.startDate.nanoTimestamp() - 1000L),
            Instrumenter.SENTRY,
            SpanOptions()
        ) as Span
        child1.finish()
        span.finish(SpanStatus.OK)

        // then the span start should remain unchanged
        assertEquals(startDate, span.startDate)
    }

    @Test
    fun `when span trim-end is enabled, trim to end of child span`() {
        // when trim end is enabled
        val span = fixture.getSut(
            SpanOptions().apply {
                isTrimEnd = true
            }
        )

        val startDate = span.startDate

        // and a child span is created
        Thread.sleep(1)
        val child1 = span.startChild("op1") as Span
        child1.finish()

        span.finish(SpanStatus.OK)

        // then the start should be left unchanged
        // but the end should match the child
        assertEquals(startDate, span.startDate)
        assertEquals(child1.finishDate, span.finishDate)
    }

    @Test
    fun `when span trim-end is enabled, do not trim to end of child span if parent already finishes earlier`() {
        // when trim end is enabled
        val span = fixture.getSut(
            SpanOptions().apply {
                isTrimEnd = true
            }
        )

        val startDate = span.startDate

        // and a child span is created, but finished later than the parent
        val child1 = span.startChild("op1") as Span
        span.finish(SpanStatus.OK)

        val finishDate = span.finishDate!!

        Thread.sleep(1)
        child1.finish()

        // then both start and finish date should be left unchanged
        assertEquals(startDate, span.startDate)
        assertEquals(finishDate, span.finishDate)
    }

    @Test
    fun `when span trimming is enabled, trim to direct children spans only`() {
        // when a span with start+end trimming is enabled
        val span = fixture.getSut(
            SpanOptions().apply {
                isTrimStart = true
                isTrimEnd = true
            }
        )

        // and two child spans are started
        val child1 = span.startChild("op1") as Span
        Thread.sleep(1)
        child1.finish()

        val child2 = span.startChild("op2") as Span

        // and another child is started from a child
        val subChild = child2.startChild("op3") as Span

        Thread.sleep(1)
        child2.finish()
        Thread.sleep(1)
        subChild.finish()

        span.finish(SpanStatus.OK)
        assertTrue(span.isFinished)

        // then the span start/finish should match its direct children only
        assertEquals(child1.startDate, span.startDate)
        assertEquals(child2.finishDate, span.finishDate)
        assertNotEquals(subChild.finishDate, span.finishDate)
    }

    @Test
    fun `when span trimming is enabled, root span trim to all children spans`() {
        // when a root span with start+end trimming is enabled
        val span = fixture.getRootSut(
            TransactionOptions().apply {
                isTrimStart = true
                isTrimEnd = true
            }
        )

        // and two child spans are started
        val child1 = span.startChild("op1") as Span
        Thread.sleep(1)
        child1.finish()

        val child2 = span.startChild("op2") as Span

        // and another child is started from a child
        val subChild = child2.startChild("op3") as Span

        Thread.sleep(1)
        child2.finish()
        Thread.sleep(1)
        subChild.finish()

        span.finish(SpanStatus.OK)
        assertTrue(span.isFinished)

        // then the root span start/finish should match first/last of its direct and indirect children
        assertEquals(child1.startDate, span.startDate)
        assertNotEquals(child2.finishDate, span.finishDate)
        assertEquals(subChild.finishDate, span.finishDate)
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

    @Test
    fun `setMeasurement sets a measurement`() {
        val span = fixture.getSut()
        span.setMeasurement("test", 1)
        assertNotNull(span.measurements["test"])
        assertEquals(1, span.measurements["test"]!!.value)
    }

    @Test
    fun `setMeasurement does not set a measurement if a span is finished`() {
        val span = fixture.getSut()
        span.finish()
        span.setMeasurement("test", 1)
        assertTrue(span.measurements.isEmpty())
    }

    @Test
    fun `setMeasurement also set a measurement to the transaction root span`() {
        val transaction = spy(getTransaction())
        val span = transaction.startChild("op") as Span
        // We need to inject the mock, otherwise the span calls the real transaction object
        span.injectForField("transaction", transaction)
        span.setMeasurement("test", 1)
        verify(transaction).setMeasurementFromChild(eq("test"), eq(1))
        verify(transaction).setMeasurement(eq("test"), eq(1))
        assertNotNull(span.measurements["test"])
        assertEquals(1, span.measurements["test"]!!.value)
        assertNotNull(transaction.root.measurements["test"])
        assertEquals(1, transaction.root.measurements["test"]!!.value)
    }

    @Test
    fun `setMeasurement on transaction root span does not call transaction setMeasurement to avoid infinite recursion`() {
        val transaction = spy(getTransaction())
        // We need to inject the mock, otherwise the span calls the real transaction object
        transaction.root.injectForField("transaction", transaction)
        transaction.root.setMeasurement("test", 1)
        verify(transaction, never()).setMeasurementFromChild(any(), any())
        verify(transaction, never()).setMeasurementFromChild(any(), any(), any())
        assertNotNull(transaction.root.measurements["test"])
        assertEquals(1, transaction.root.measurements["test"]!!.value)
    }

    // test to ensure that the span is not finished when the finishCallback is called
    @Test
    fun `span is not finished when finishCallback is called`() {
        val span = fixture.getSut()
        span.setSpanFinishedCallback {
            assertFalse(span.isFinished)
            assertNotNull(span.finishDate)
        }
        assertFalse(span.isFinished)
        assertNull(span.finishDate)
        span.finish()
    }

    private fun getTransaction(transactionContext: TransactionContext = TransactionContext("name", "op")): SentryTracer {
        return SentryTracer(transactionContext, fixture.scopes)
    }

    private fun startChildFromSpan(): Span {
        val transaction = getTransaction()
        return transaction.startChild("op") as Span
    }
}
