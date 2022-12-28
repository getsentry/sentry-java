package io.sentry

import io.sentry.protocol.User
import org.awaitility.kotlin.await
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SentryTracerTest {

    private class Fixture {
        val options = SentryOptions()
        val hub: Hub
        val transactionPerformanceCollector: TransactionPerformanceCollector

        init {
            options.dsn = "https://key@sentry.io/proj"
            options.environment = "environment"
            options.release = "release@3.0.0"
            hub = spy(Hub(options))
            transactionPerformanceCollector = spy(TransactionPerformanceCollector(options))
            hub.bindClient(mock())
        }

        fun getSut(
            optionsConfiguration: Sentry.OptionsConfiguration<SentryOptions> = Sentry.OptionsConfiguration {},
            startTimestamp: Date? = null,
            waitForChildren: Boolean = false,
            idleTimeout: Long? = null,
            trimEnd: Boolean = false,
            transactionFinishedCallback: TransactionFinishedCallback? = null,
            samplingDecision: TracesSamplingDecision? = null
        ): SentryTracer {
            optionsConfiguration.configure(options)
            return SentryTracer(TransactionContext("name", "op", samplingDecision), hub, startTimestamp, waitForChildren, idleTimeout, trimEnd, transactionFinishedCallback, transactionPerformanceCollector)
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
    fun `when transaction is created, by default its profile is not sampled`() {
        val tracer = fixture.getSut()
        assertNull(tracer.isProfileSampled)
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
    fun `when transaction is finished with status and timestamp, timestamp and status are set`() {
        val tracer = fixture.getSut()
        val date = Date.from(LocalDateTime.of(2022, 12, 24, 23, 59, 58, 0).toInstant(ZoneOffset.UTC))
        tracer.finish(SpanStatus.ABORTED, date)
        assertEquals(tracer.timestamp, DateUtils.dateToSeconds(date))
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
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `when transaction is finished and profiling is disabled, transactionProfiler is not called`() {
        val transactionProfiler = mock<ITransactionProfiler>()
        val tracer = fixture.getSut(optionsConfiguration = {
            it.profilesSampleRate = 0.0
            it.setTransactionProfiler(transactionProfiler)
        })
        tracer.finish()
        verify(transactionProfiler, never()).onTransactionFinish(any())
    }

    @Test
    fun `when transaction is finished and sampled and profiling is enabled, transactionProfiler is called`() {
        val transactionProfiler = mock<ITransactionProfiler>()
        val tracer = fixture.getSut(optionsConfiguration = {
            it.profilesSampleRate = 1.0
            it.setTransactionProfiler(transactionProfiler)
        }, samplingDecision = TracesSamplingDecision(true, null, true, null))
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
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `not sampled spans are filtered out`() {
        val tracer = fixture.getSut(samplingDecision = TracesSamplingDecision(true))
        tracer.startChild("op1")
        val span = tracer.startChild("op2")
        span.spanContext.sampled = false
        tracer.finish()
        verify(fixture.hub).captureTransaction(
            check {
                assertEquals(1, it.spans.size)
                assertEquals("op1", it.spans.first().op)
            },
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `when transaction is finished, context is set`() {
        val tracer = fixture.getSut()
        val otelContext = mapOf(
            "attributes" to mapOf(
                "db.connection_string" to "hsqldb:mem:",
                "db.statement" to "CREATE TABLE person ( id INTEGER IDENTITY PRIMARY KEY, firstName VARCHAR(?) NOT NULL, lastName VARCHAR(?) NOT NULL )"
            ),
            "resource" to mapOf(
                "process.runtime.version" to "17.0.4.1+1",
                "telemetry.auto.version" to "sentry-6.7.0-otel-1.19.2"
            )
        )
        tracer.setContext("otel", otelContext)
        tracer.finish()

        verify(fixture.hub).captureTransaction(
            check {
                assertEquals(otelContext, it.contexts["otel"])
            },
            anyOrNull<TraceContext>(),
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
            anyOrNull<TraceContext>(),
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
    fun `when span is finished, do nothing`() {
        val transaction = fixture.getSut()
        transaction.description = "desc"
        transaction.setTag("myTag", "myValue")
        transaction.setData("myData", "myValue")
        transaction.setMeasurement("myMetric", 1.0f)
        val ex = RuntimeException()
        transaction.throwable = ex

        transaction.finish(SpanStatus.OK)
        assertTrue(transaction.isFinished)

        assertEquals(NoOpSpan.getInstance(), transaction.startChild("op", "desc"))

        transaction.finish(SpanStatus.UNKNOWN_ERROR)
        transaction.operation = "newOp"
        transaction.description = "newDesc"
        transaction.status = SpanStatus.ABORTED
        transaction.setTag("myTag", "myNewValue")
        transaction.throwable = RuntimeException()
        transaction.setData("myData", "myNewValue")
        transaction.name = "newName"
        transaction.setMeasurement("myMetric", 2.0f)

        assertEquals(SpanStatus.OK, transaction.status)
        assertEquals("op", transaction.operation)
        assertEquals("desc", transaction.description)
        assertEquals("myValue", transaction.getTag("myTag"))
        assertEquals("myValue", transaction.getData("myData"))
        assertEquals(1.0f, transaction.measurements["myMetric"]!!.value)
        assertEquals("name", transaction.name)
        assertEquals(ex, transaction.throwable)
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
        verify(fixture.hub, never()).captureTransaction(any(), any<TraceContext>(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `when waiting for children, finishing transaction calls hub if all children are finished`() {
        val transaction = fixture.getSut(waitForChildren = true)
        val child = transaction.startChild("op")
        child.finish()
        transaction.finish()
        verify(fixture.hub).captureTransaction(any(), anyOrNull<TraceContext>(), anyOrNull(), anyOrNull())
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
        verify(fixture.hub, never()).captureTransaction(any(), any<TraceContext>(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `when waiting for children, finishing last child calls hub if transaction is already finished`() {
        val transaction = fixture.getSut(waitForChildren = true)
        val child = transaction.startChild("op")
        transaction.finish(SpanStatus.INVALID_ARGUMENT)
        verify(fixture.hub, never()).captureTransaction(any(), any<TraceContext>(), anyOrNull(), anyOrNull())
        child.finish()
        verify(fixture.hub, times(1)).captureTransaction(
            check {
                assertEquals(SpanStatus.INVALID_ARGUMENT, it.status)
            },
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `finishing unfinished spans with the transaction timestamp`() {
        val transaction = fixture.getSut(samplingDecision = TracesSamplingDecision(true))
        val span = transaction.startChild("op") as Span
        transaction.startChild("op2")
        transaction.finish(SpanStatus.INVALID_ARGUMENT)

        verify(fixture.hub, times(1)).captureTransaction(
            check {
                assertEquals(2, it.spans.size)
                assertEquals(transaction.root.endNanos, span.endNanos)
                assertEquals(SpanStatus.DEADLINE_EXCEEDED, it.spans[0].status)
                assertEquals(SpanStatus.DEADLINE_EXCEEDED, it.spans[1].status)
            },
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `returns trace state`() {
        val transaction = fixture.getSut({
            it.isTraceSampling = true
            it.isSendDefaultPii = true
        })
        fixture.hub.setUser(
            User().apply {
                id = "user-id"
                others = mapOf("segment" to "pro")
            }
        )
        val trace = transaction.traceContext()
        assertNotNull(trace) {
            assertEquals(transaction.spanContext.traceId, it.traceId)
            assertEquals("key", it.publicKey)
            assertEquals("environment", it.environment)
            assertEquals("release@3.0.0", it.release)
            assertEquals(transaction.name, it.transaction)
            // assertEquals("user-id", it.userId)
            assertEquals("pro", it.userSegment)
        }
    }

    @Test
    fun `returns trace state without userId if not send pii`() {
        val transaction = fixture.getSut({
            it.isTraceSampling = true
        })
        fixture.hub.setUser(
            User().apply {
                id = "user-id"
                others = mapOf("segment" to "pro")
            }
        )
        val trace = transaction.traceContext()
        assertNotNull(trace) {
            assertEquals(transaction.spanContext.traceId, it.traceId)
            assertEquals("key", it.publicKey)
            assertEquals("environment", it.environment)
            assertEquals("release@3.0.0", it.release)
            assertEquals(transaction.name, it.transaction)
            assertNull(it.userId)
            assertEquals("pro", it.userSegment)
        }
    }

    @Test
    fun `trace state does not change once computed`() {
        val transaction = fixture.getSut({
            it.isTraceSampling = true
        })
        val traceBeforeUserSet = transaction.traceContext()
        fixture.hub.setUser(
            User().apply {
                id = "user-id"
            }
        )
        val traceAfterUserSet = transaction.traceContext()
        assertNotNull(traceAfterUserSet) {
            assertEquals(it.traceId, traceBeforeUserSet?.traceId)
            assertEquals(it.transaction, traceBeforeUserSet?.transaction)
            assertEquals(it.environment, traceBeforeUserSet?.environment)
            assertEquals(it.release, traceBeforeUserSet?.release)
            assertEquals(it.publicKey, traceBeforeUserSet?.publicKey)
            assertEquals(it.sampleRate, traceBeforeUserSet?.sampleRate)
            assertEquals(it.userId, traceBeforeUserSet?.userId)
            assertEquals(it.userSegment, traceBeforeUserSet?.userSegment)

            assertNull(it.userId)
            assertNull(it.userSegment)
        }
    }

    @Test
    fun `returns baggage header`() {
        val transaction = fixture.getSut({
            it.isTraceSampling = true
            it.environment = "production"
            it.release = "1.0.99-rc.7"
            it.isSendDefaultPii = true
        })

        fixture.hub.setUser(
            User().apply {
                id = "userId12345"
                others = mapOf("segment" to "pro")
            }
        )

        val header = transaction.toBaggageHeader(null)
        assertNotNull(header) {
            assertEquals("baggage", it.name)
            assertNotNull(it.value)
            println(it.value)
            assertTrue(it.value.contains("sentry-trace_id=[^,]+".toRegex()))
            assertTrue(it.value.contains("sentry-public_key=key,"))
            assertTrue(it.value.contains("sentry-release=1.0.99-rc.7,"))
            assertTrue(it.value.contains("sentry-environment=production,"))
            assertTrue(it.value.contains("sentry-transaction=name,"))
            // assertTrue(it.value.contains("sentry-user_id=userId12345,"))
            assertTrue(it.value.contains("sentry-user_segment=pro$".toRegex()))
        }
    }

    @Test
    fun `returns baggage header without userId if not send pii`() {
        val transaction = fixture.getSut({
            it.isTraceSampling = true
            it.environment = "production"
            it.release = "1.0.99-rc.7"
        })

        fixture.hub.setUser(
            User().apply {
                id = "userId12345"
                others = mapOf("segment" to "pro")
            }
        )

        val header = transaction.toBaggageHeader(null)
        assertNotNull(header) {
            assertEquals("baggage", it.name)
            assertNotNull(it.value)
            println(it.value)
            assertTrue(it.value.contains("sentry-trace_id=[^,]+".toRegex()))
            assertTrue(it.value.contains("sentry-public_key=key,"))
            assertTrue(it.value.contains("sentry-release=1.0.99-rc.7,"))
            assertTrue(it.value.contains("sentry-environment=production,"))
            assertTrue(it.value.contains("sentry-transaction=name,"))
            assertFalse(it.value.contains("sentry-user_id"))
            assertTrue(it.value.contains("sentry-user_segment=pro$".toRegex()))
        }
    }

    @Test
    fun `returns baggage header without userId if send pii and null user`() {
        val transaction = fixture.getSut({
            it.isTraceSampling = true
            it.environment = "production"
            it.release = "1.0.99-rc.7"
            it.isSendDefaultPii = true
        })

        fixture.hub.setUser(null)

        val header = transaction.toBaggageHeader(null)
        assertNotNull(header) {
            assertEquals("baggage", it.name)
            assertNotNull(it.value)
            println(it.value)
            assertTrue(it.value.contains("sentry-trace_id=[^,]+".toRegex()))
            assertTrue(it.value.contains("sentry-public_key=key,"))
            assertTrue(it.value.contains("sentry-release=1.0.99-rc.7,"))
            assertTrue(it.value.contains("sentry-environment=production,"))
            assertTrue(it.value.contains("sentry-transaction=name"))
            assertFalse(it.value.contains("sentry-user_id"))
            assertFalse(it.value.contains("sentry-user_segment"))
        }
    }

    @Test
    fun `sets ITransaction data as extra in SentryTransaction`() {
        val transaction = fixture.getSut(samplingDecision = TracesSamplingDecision(true))
        transaction.setData("key", "val")
        transaction.finish()
        verify(fixture.hub).captureTransaction(
            check {
                assertEquals("val", it.getExtra("key"))
            },
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `sets Span data as data in SentrySpan`() {
        val transaction = fixture.getSut(samplingDecision = TracesSamplingDecision(true))
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
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `when initialized without idleTimeout, does not schedule finish timer`() {
        val transaction = fixture.getSut()

        assertNull(transaction.timerTask)
    }

    @Test
    fun `when initialized with idleTimeout, schedules finish timer`() {
        val transaction = fixture.getSut(idleTimeout = 50)

        assertNotNull(transaction.timerTask)
    }

    @Test
    fun `when no children and the transaction is idle, drops the transaction`() {
        val transaction = fixture.getSut(idleTimeout = 10)

        await.untilFalse(transaction.isFinishTimerRunning)

        verify(fixture.hub, never()).captureTransaction(
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `when idle transaction with children, finishes the transaction after the idle timeout`() {
        val transaction = fixture.getSut(waitForChildren = true, idleTimeout = 1000)

        val span = transaction.startChild("op")
        span.finish()

        await.untilFalse(transaction.isFinishTimerRunning)

        verify(fixture.hub).captureTransaction(
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `when a new child is added to the idle transaction, cancels the timer`() {
        val transaction = fixture.getSut(waitForChildren = true, idleTimeout = 3000)

        transaction.startChild("op")

        assertNull(transaction.timerTask)
    }

    @Test
    fun `when a child is finished and the transaction is idle, resets the timer`() {
        val transaction = fixture.getSut(waitForChildren = true, idleTimeout = 3000)

        val initialTime = transaction.timerTask!!.scheduledExecutionTime()

        val span = transaction.startChild("op")
        Thread.sleep(1)
        span.finish()

        val timerAfterFinishingChild = transaction.timerTask!!.scheduledExecutionTime()

        assertTrue { timerAfterFinishingChild > initialTime }
    }

    @Test
    fun `when idle transaction has still unfinished children, does not reset the timer`() {
        val transaction = fixture.getSut(waitForChildren = true, idleTimeout = 3000)

        val span = transaction.startChild("op")
        val span2 = transaction.startChild("op2")
        Thread.sleep(1)
        span.finish()

        assertNull(transaction.timerTask)
    }

    @Test
    fun `when trimEnd, trims idle transaction time to the latest child timestamp`() {
        val transaction = fixture.getSut(waitForChildren = true, idleTimeout = 50, trimEnd = true, samplingDecision = TracesSamplingDecision(true))

        val span = transaction.startChild("op")
        span.finish()

        // just a small sleep to make sure the 2nd span finishes later than the 1st one
        Thread.sleep(1)

        val span2 = transaction.startChild("op2") as Span
        span2.finish()

        await.untilFalse(transaction.isFinishTimerRunning)

        verify(fixture.hub).captureTransaction(
            check {
                assertEquals(2, it.spans.size)
                assertEquals(transaction.root.endNanos, span2.endNanos)
            },
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `timer is created if idle timeout is set`() {
        val transaction = fixture.getSut(waitForChildren = true, idleTimeout = 50, trimEnd = true, samplingDecision = TracesSamplingDecision(true))
        assertNotNull(transaction.timer)
    }

    @Test
    fun `timer is not created if idle timeout is not set`() {
        val transaction = fixture.getSut(waitForChildren = true, idleTimeout = null, trimEnd = true, samplingDecision = TracesSamplingDecision(true))
        assertNull(transaction.timer)
    }

    @Test
    fun `timer is cancelled on finish`() {
        val transaction = fixture.getSut(waitForChildren = true, idleTimeout = 50, trimEnd = true, samplingDecision = TracesSamplingDecision(true))
        assertNotNull(transaction.timer)
        transaction.finish(SpanStatus.OK)
        assertNull(transaction.timer)
    }

    @Test
    fun `when tracer is finished, puts custom measurements into underlying transaction`() {
        val transaction = fixture.getSut()
        transaction.setMeasurement("metric1", 1.0f)
        transaction.setMeasurement("days", 2, MeasurementUnit.Duration.DAY)
        transaction.finish()

        verify(fixture.hub).captureTransaction(
            check {
                assertEquals(1.0f, it.measurements["metric1"]!!.value)
                assertEquals(null, it.measurements["metric1"]!!.unit)

                assertEquals(2, it.measurements["days"]!!.value)
                assertEquals("day", it.measurements["days"]!!.unit)
            },
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `setting the same measurement multiple times only keeps latest value`() {
        val transaction = fixture.getSut()
        transaction.setMeasurement("metric1", 1.0f)
        transaction.setMeasurement("metric1", 2, MeasurementUnit.Duration.DAY)
        transaction.finish()

        verify(fixture.hub).captureTransaction(
            check {
                assertEquals(2, it.measurements["metric1"]!!.value)
                assertEquals("day", it.measurements["metric1"]!!.unit)
            },
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `when transaction is created, transactionPerformanceCollector is started`() {
        val transaction = fixture.getSut()
        verify(fixture.transactionPerformanceCollector).start(check { assertEquals(transaction, it) })
    }

    @Test
    fun `when transaction is created, transactionPerformanceCollector is stopped`() {
        val transaction = fixture.getSut()
        transaction.finish()
        verify(fixture.transactionPerformanceCollector).stop(check { assertEquals(transaction, it) })
    }
}
