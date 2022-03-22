package io.sentry

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import io.sentry.protocol.App
import io.sentry.protocol.Request
import io.sentry.protocol.User
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SentryTracerTest {

    private class Fixture {
        val options = SentryOptions()
        val hub: Hub

        init {
            options.dsn = "https://key@sentry.io/proj"
            options.environment = "environment"
            options.release = "release@3.0.0"
            hub = spy(Hub(options))
            hub.bindClient(mock())
        }

        fun getSut(
            optionsConfiguration: Sentry.OptionsConfiguration<SentryOptions> = Sentry.OptionsConfiguration {},
            startTimestamp: Date? = null,
            waitForChildren: Boolean = false,
            transactionFinishedCallback: TransactionFinishedCallback? = null,
            sampled: Boolean? = null
        ): SentryTracer {
            optionsConfiguration.configure(options)
            return SentryTracer(TransactionContext("name", "op", sampled), hub, startTimestamp, waitForChildren, transactionFinishedCallback)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `does not add more spans than configured in options`() {
        val tracer = fixture.getSut({
            it.maxSpans = 2
            it.setDebug(true)
            it.setLogger(SystemOutLogger())
        })
        tracer.startChild("child1")
        tracer.startChild("child2")
        tracer.startChild("child3")
        assertEquals(2, tracer.children.size)
    }

    @Test
    fun `when span limit is reached, startChild returns NoOpSpan`() {
        val tracer = fixture.getSut({
            it.maxSpans = 2
            it.setDebug(true)
            it.setLogger(SystemOutLogger())
        })
        tracer.startChild("child1")
        tracer.startChild("child2")
        assertTrue(tracer.startChild("child3") is NoOpSpan)
    }

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
        verify(fixture.hub).captureTransaction(
            check {
                assertEquals(it.transaction, tracer.name)
            },
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `when transaction is finished and profiling is disabled, transactionProfiler is not called`() {
        val transactionProfiler = mock<ITransactionProfiler>()
        val tracer = fixture.getSut(optionsConfiguration = {
            it.isProfilingEnabled = false
            it.setTransactionProfiler(transactionProfiler)
        })
        tracer.finish()
        verify(transactionProfiler, never()).onTransactionFinish(any())
    }

    @Test
    fun `when transaction is finished and sampled and profiling is enabled, transactionProfiler is called`() {
        val transactionProfiler = mock<ITransactionProfiler>()
        val tracer = fixture.getSut(optionsConfiguration = {
            it.isProfilingEnabled = true
            it.setTransactionProfiler(transactionProfiler)
        }, sampled = true)
        tracer.finish()
        verify(transactionProfiler).onTransactionFinish(any())
    }

    @Test
    fun `when transaction is finished, transaction is cleared from the scope`() {
        val tracer = fixture.getSut()
        fixture.hub.configureScope { it.transaction = tracer }
        assertNotNull(fixture.hub.span)
        tracer.finish()
        assertNull(fixture.hub.span)
    }

    @Test
    fun `when transaction with throwable set is finished, span context is associated with throwable`() {
        val tracer = fixture.getSut()
        val ex = RuntimeException()
        tracer.throwable = ex
        tracer.finish()
        verify(fixture.hub).setSpanContext(ex, tracer.root, "name")
    }

    @Test
    fun `when transaction with request set is finished, request is set on the transaction`() {
        val tracer = fixture.getSut()
        val request = Request()
        tracer.request = request
        tracer.finish()
        verify(fixture.hub).captureTransaction(
            check {
                assertEquals(request, it.request)
            },
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `when transaction with contexts is finished, contexts are set on the transaction`() {
        val tracer = fixture.getSut()
        val contexts = tracer.contexts
        val app = App()
        contexts.setApp(app)
        contexts["custom"] = "value"
        tracer.finish()
        verify(fixture.hub).captureTransaction(
            check {
                assertEquals(app, it.contexts.app)
                assertEquals("value", it.contexts["custom"])
                assertNotNull(it.contexts.trace) {
                    assertEquals(tracer.spanContext.traceId, it.traceId)
                    assertEquals(tracer.spanContext.spanId, it.spanId)
                    assertEquals(tracer.spanContext.parentSpanId, it.parentSpanId)
                    assertEquals(tracer.spanContext.op, it.op)
                    assertEquals(tracer.spanContext.description, it.description)
                    assertEquals(tracer.spanContext.status, it.status)
                    assertEquals(tracer.spanContext.sampled, it.sampled)
                }
            },
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `when transaction with contexts has overwritten trace, tracer span context is applied to transaction`() {
        val tracer = fixture.getSut()
        val contexts = tracer.contexts
        val spanContext = SpanContext("op")
        contexts.trace = spanContext
        tracer.finish()
        verify(fixture.hub).captureTransaction(
            check {
                assertNotNull(it.contexts.trace) {
                    assertNotEquals(spanContext, it)
                    assertEquals(tracer.spanContext.traceId, it.traceId)
                    assertEquals(tracer.spanContext.spanId, it.spanId)
                    assertEquals(tracer.spanContext.parentSpanId, it.parentSpanId)
                    assertEquals(tracer.spanContext.op, it.op)
                    assertEquals(tracer.spanContext.description, it.description)
                    assertEquals(tracer.spanContext.status, it.status)
                    assertEquals(tracer.spanContext.sampled, it.sampled)
                }
            },
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `when transaction with tags set is finished, tags are set on the transaction but not on the trace context`() {
        val tracer = fixture.getSut()
        tracer.setTag("tag1", "val1")
        tracer.setTag("tag2", "val2")
        tracer.finish()
        verify(fixture.hub).captureTransaction(
            check {
                assertEquals(mapOf("tag1" to "val1", "tag2" to "val2"), it.tags)
                assertNotNull(it.contexts.trace) {
                    assertEquals(emptyMap(), it.tags)
                }
            },
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `not sampled spans are filtered out`() {
        val tracer = fixture.getSut(sampled = true)
        tracer.startChild("op1")
        val span = tracer.startChild("op2")
        span.spanContext.sampled = false
        tracer.finish()
        verify(fixture.hub).captureTransaction(
            check {
                assertEquals(1, it.spans.size)
                assertEquals("op1", it.spans.first().op)
            },
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
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
        verify(fixture.hub).setSpanContext(ex, transaction.root, "name")
        verify(fixture.hub).captureTransaction(
            check {
                assertNotNull(it.contexts.trace) {
                    assertEquals(SpanStatus.OK, it.status)
                }
            },
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )

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

    @Test
    fun `when startTimestamp is given, use it as startTimestamp`() {
        val date = Date(0)
        val transaction = fixture.getSut(startTimestamp = date)

        assertSame(date, transaction.startTimestamp)
    }

    @Test
    fun `when startTimestamp is nullable, set it automatically`() {
        val transaction = fixture.getSut(startTimestamp = null)

        assertNotNull(transaction.startTimestamp)
    }

    @Test
    fun `when waiting for children, finishing transaction does not call hub if all children are not finished`() {
        val transaction = fixture.getSut(waitForChildren = true)
        transaction.startChild("op")
        transaction.finish()
        verify(fixture.hub, never()).captureTransaction(any(), any(), any())
    }

    @Test
    fun `when waiting for children, finishing transaction calls hub if all children are finished`() {
        val transaction = fixture.getSut(waitForChildren = true)
        val child = transaction.startChild("op")
        child.finish()
        transaction.finish()
        verify(fixture.hub).captureTransaction(any(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `when waiting for children, finishing transaction calls transactionListener`() {
        var transactionListenerCalled = false
        val transaction = fixture.getSut(waitForChildren = true, transactionFinishedCallback = {
            transactionListenerCalled = true
        })
        val child = transaction.startChild("op")
        child.finish()
        transaction.finish()

        assertTrue(transactionListenerCalled)
    }

    @Test
    fun `when waiting for children, hub is not called until transaction is finished`() {
        val transaction = fixture.getSut(waitForChildren = true)
        val child = transaction.startChild("op")
        child.finish()
        verify(fixture.hub, never()).captureTransaction(any(), any(), any())
    }

    @Test
    fun `when waiting for children, finishing last child calls hub if transaction is already finished`() {
        val transaction = fixture.getSut(waitForChildren = true)
        val child = transaction.startChild("op")
        transaction.finish(SpanStatus.INVALID_ARGUMENT)
        verify(fixture.hub, never()).captureTransaction(any(), any(), any())
        child.finish()
        verify(fixture.hub, times(1)).captureTransaction(
            check {
                assertEquals(SpanStatus.INVALID_ARGUMENT, it.status)
            },
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `finishing unfinished spans with the transaction timestamp`() {
        val transaction = fixture.getSut(sampled = true)
        transaction.startChild("op")
        transaction.startChild("op2")
        transaction.finish(SpanStatus.INVALID_ARGUMENT)
        verify(fixture.hub, times(1)).captureTransaction(
            check {
                val timestamp = it.timestamp
                assertEquals(2, it.spans.size)
                assertEquals(timestamp, it.spans[0].timestamp)
                assertEquals(SpanStatus.DEADLINE_EXCEEDED, it.spans[0].status)
                assertEquals(SpanStatus.DEADLINE_EXCEEDED, it.spans[1].status)
            },
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `returns trace state`() {
        val transaction = fixture.getSut({
            it.isTraceSampling = true
        })
        fixture.hub.setUser(
            User().apply {
                id = "user-id"
                others = mapOf("segment" to "pro")
            }
        )
        val trace = transaction.traceState()
        assertNotNull(trace) {
            assertEquals(transaction.spanContext.traceId, it.traceId)
            assertEquals("key", it.publicKey)
            assertEquals("environment", it.environment)
            assertEquals("release@3.0.0", it.release)
            assertEquals(transaction.name, it.transaction)
            assertNotNull(it.user) {
                assertEquals("user-id", it.id)
                assertEquals("pro", it.segment)
            }
        }
    }

    @Test
    fun `trace state does not change once computed`() {
        val transaction = fixture.getSut({
            it.isTraceSampling = true
        })
        val traceBeforeUserSet = transaction.traceState()
        fixture.hub.setUser(
            User().apply {
                id = "user-id"
            }
        )
        val traceAfterUserSet = transaction.traceState()
        assertNotNull(traceAfterUserSet) {
            assertEquals(it, traceBeforeUserSet)
            assertNull(it.user)
        }
    }

    @Test
    fun `returns trace state header`() {
        val transaction = fixture.getSut({
            it.isTraceSampling = true
        })
        val header = transaction.toTraceStateHeader()
        assertNotNull(header) {
            assertEquals("tracestate", it.name)
            assertNotNull(it.value)
        }
    }

    @Test
    fun `sets ITransaction data as extra in SentryTransaction`() {
        val transaction = fixture.getSut(sampled = true)
        transaction.setData("key", "val")
        transaction.finish()
        verify(fixture.hub).captureTransaction(
            check {
                assertEquals("val", it.getExtra("key"))
            },
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `sets Span data as data in SentrySpan`() {
        val transaction = fixture.getSut(sampled = true)
        val span = transaction.startChild("op")
        span.setData("key", "val")
        span.finish()
        transaction.finish()
        verify(fixture.hub).captureTransaction(
            check {
                assertNotNull(it.spans.first().data) {
                    assertEquals("val", it["key"])
                }
            },
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
    }
}
