package io.sentry

import io.sentry.protocol.SentryId
import io.sentry.protocol.TransactionNameSource
import io.sentry.protocol.User
import io.sentry.test.createTestScopes
import io.sentry.util.thread.IThreadChecker
import org.awaitility.kotlin.await
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.time.ZoneOffset
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
        val scopes: Scopes
        val compositePerformanceCollector: CompositePerformanceCollector

        init {
            options.dsn = "https://key@sentry.io/proj"
            options.environment = "environment"
            options.release = "release@3.0.0"
            scopes = spy(createTestScopes(options))
            compositePerformanceCollector = spy(
                DefaultCompositePerformanceCollector(
                    options
                )
            )
        }

        fun getSut(
            optionsConfiguration: Sentry.OptionsConfiguration<SentryOptions> = Sentry.OptionsConfiguration {},
            startTimestamp: SentryDate? = null,
            waitForChildren: Boolean = false,
            idleTimeout: Long? = null,
            deadlineTimeout: Long? = null,
            trimEnd: Boolean = false,
            transactionFinishedCallback: TransactionFinishedCallback? = null,
            samplingDecision: TracesSamplingDecision? = null,
            performanceCollector: CompositePerformanceCollector? = compositePerformanceCollector
        ): SentryTracer {
            optionsConfiguration.configure(options)

            val transactionOptions = TransactionOptions()
            transactionOptions.startTimestamp = startTimestamp
            transactionOptions.isWaitForChildren = waitForChildren
            transactionOptions.idleTimeout = idleTimeout
            transactionOptions.deadlineTimeout = deadlineTimeout
            transactionOptions.isTrimEnd = trimEnd
            transactionOptions.transactionFinishedCallback = transactionFinishedCallback
            return SentryTracer(TransactionContext("name", "op", samplingDecision), scopes, transactionOptions, performanceCollector)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `transfer origin from transaction options to transaction context`() {
        fixture.getSut()
        val transactionOptions = TransactionOptions().also {
            it.origin = "new-origin"
        }
        val transactionContext = TransactionContext("name", "op", null).also {
            it.origin = "old-origin"
        }

        val transaction = SentryTracer(transactionContext, fixture.scopes, transactionOptions, null)
        assertEquals("new-origin", transaction.spanContext.origin)
    }

    @Test
    fun `does not create child span if origin is ignored`() {
        val tracer = fixture.getSut({
            it.setDebug(true)
            it.setLogger(SystemOutLogger())
            it.ignoredSpanOrigins = listOf("ignored")
        })
        tracer.startChild("child1", null, SpanOptions().also { it.origin = "ignored" })
        tracer.startChild("child2")
        assertEquals(1, tracer.children.size)
    }

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
        assertNotNull(tracer.startDate)
    }

    @Test
    fun `when transaction is created, timestamp is not set`() {
        val tracer = fixture.getSut()
        assertNull(tracer.finishDate)
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
        assertNotNull(tracer.finishDate)
    }

    @Test
    fun `when transaction is finished with status, timestamp and status are set`() {
        val tracer = fixture.getSut()
        tracer.finish(SpanStatus.ABORTED)
        assertNotNull(tracer.finishDate)
        assertEquals(SpanStatus.ABORTED, tracer.status)
    }

    @Test
    fun `when transaction is finished with status and timestamp, timestamp and status are set`() {
        val tracer = fixture.getSut()
        val date = SentryNanotimeDate(Date.from(LocalDateTime.of(2022, 12, 24, 23, 59, 58, 0).toInstant(ZoneOffset.UTC)), 0)
        tracer.finish(SpanStatus.ABORTED, date)
        assertEquals(tracer.finishDate!!.nanoTimestamp(), date.nanoTimestamp())
        assertEquals(SpanStatus.ABORTED, tracer.status)
    }

    @Test
    fun `when transaction is finished, transaction is captured`() {
        val tracer = fixture.getSut()
        tracer.finish()
        verify(fixture.scopes).captureTransaction(
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
        verify(transactionProfiler, never()).onTransactionFinish(any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `when transaction is finished and sampled and profiling is enabled, transactionProfiler is called`() {
        val transactionProfiler = mock<ITransactionProfiler>()
        val tracer = fixture.getSut(optionsConfiguration = {
            it.profilesSampleRate = 1.0
            it.setTransactionProfiler(transactionProfiler)
        }, samplingDecision = TracesSamplingDecision(true, null, true, null))
        tracer.finish()
        verify(transactionProfiler).onTransactionFinish(any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `when continuous profiler is running, profile context is set`() {
        val continuousProfiler = mock<IContinuousProfiler>()
        val profilerId = SentryId()
        whenever(continuousProfiler.profilerId).thenReturn(profilerId)
        val tracer = fixture.getSut(optionsConfiguration = {
            it.setContinuousProfiler(continuousProfiler)
        })
        tracer.finish()
        verify(fixture.scopes).captureTransaction(
            check {
                assertNotNull(it.contexts.profile) {
                    assertEquals(profilerId, it.profilerId)
                }
            },
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `when continuous profiler is not running, profile context is not set`() {
        val tracer = fixture.getSut(optionsConfiguration = {
            it.setContinuousProfiler(NoOpContinuousProfiler.getInstance())
        })
        tracer.finish()
        verify(fixture.scopes).captureTransaction(
            check {
                assertNull(it.contexts.profile)
            },
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `when continuous profiler is running, profiler id is set in span data`() {
        val profilerId = SentryId()
        val profiler = mock<IContinuousProfiler>()
        whenever(profiler.profilerId).thenReturn(profilerId)

        val tracer = fixture.getSut(optionsConfiguration = { options ->
            options.setContinuousProfiler(profiler)
        })
        val span = tracer.startChild("span.op")
        assertEquals(profilerId.toString(), span.getData(SpanDataConvention.PROFILER_ID))
    }

    @Test
    fun `when continuous profiler is not running, profiler id is not set in span data`() {
        val tracer = fixture.getSut(optionsConfiguration = { options ->
            options.setContinuousProfiler(NoOpContinuousProfiler.getInstance())
        })
        val span = tracer.startChild("span.op")
        assertNull(span.getData(SpanDataConvention.PROFILER_ID))
    }

    @Test
    fun `when transaction is finished, transaction is cleared from the scope`() {
        val tracer = fixture.getSut()
        fixture.scopes.configureScope { it.transaction = tracer }
        assertNotNull(fixture.scopes.span)
        tracer.finish()
        assertNull(fixture.scopes.span)
    }

    @Test
    fun `when transaction with throwable set is finished, span context is associated with throwable`() {
        val tracer = fixture.getSut()
        val ex = RuntimeException()
        tracer.throwable = ex
        tracer.finish()
        verify(fixture.scopes).setSpanContext(ex, tracer.root, "name")
    }

    @Test
    fun `when transaction with tags set is finished, tags are set on the transaction but not on the trace context`() {
        val tracer = fixture.getSut()
        tracer.setTag("tag1", "val1")
        tracer.setTag("tag2", "val2")
        tracer.finish()
        verify(fixture.scopes).captureTransaction(
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
        verify(fixture.scopes).captureTransaction(
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

        verify(fixture.scopes).captureTransaction(
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
        assertNotNull(span.startDate)
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
        assertNotNull(span.startDate)
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
    fun `starting child with operation, description and timestamp creates a new span`() {
        val tracer = fixture.getSut()
        val sentryDate = SentryNanotimeDate()
        val span = tracer.startChild("op", "description", sentryDate) as Span
        assertNotNull(span)
        assertNotNull(span.spanId)
        assertNotNull(span.startDate)
        assertEquals("op", span.operation)
        assertEquals("description", span.description)
        assertEquals(sentryDate, span.startDate)
    }

    @Test
    fun `sstarting child with operation, description and timestamp adds a span to transaction`() {
        val tracer = fixture.getSut()
        val span = tracer.startChild("op", "description", SentryNanotimeDate())
        assertEquals(1, tracer.children.size)
        assertEquals(span, tracer.children.first())
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
        val timestamp = transaction.finishDate

        transaction.finish(SpanStatus.UNKNOWN_ERROR)

        // call only once
        verify(fixture.scopes).setSpanContext(ex, transaction.root, "name")
        verify(fixture.scopes).captureTransaction(
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
        assertEquals(timestamp, transaction.finishDate)
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
        assertEquals(1.0f, transaction.root.measurements["myMetric"]!!.value)
        assertEquals("name", transaction.name)
        assertEquals(ex, transaction.throwable)
    }

    @Test
    fun `when startTimestamp is given, use it as startTimestamp`() {
        val date = SentryNanotimeDate(Date(0), 0)
        val transaction = fixture.getSut(startTimestamp = date)

        assertSame(date, transaction.startDate)
    }

    @Test
    fun `when startTimestamp is nullable, set it automatically`() {
        val transaction = fixture.getSut(startTimestamp = null)

        assertNotNull(transaction.startDate)
    }

    @Test
    fun `when waiting for children, finishing transaction does not call scopes if all children are not finished`() {
        val transaction = fixture.getSut(waitForChildren = true)
        transaction.startChild("op")
        transaction.finish()
        verify(fixture.scopes, never()).captureTransaction(any(), any<TraceContext>(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `when waiting for children, finishing transaction calls scopes if all children are finished`() {
        val transaction = fixture.getSut(waitForChildren = true)
        val child = transaction.startChild("op")
        child.finish()
        transaction.finish()
        verify(fixture.scopes).captureTransaction(any(), anyOrNull<TraceContext>(), anyOrNull(), anyOrNull())
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
    fun `when waiting for children, scopes is not called until transaction is finished`() {
        val transaction = fixture.getSut(waitForChildren = true)
        val child = transaction.startChild("op")
        child.finish()
        verify(fixture.scopes, never()).captureTransaction(any(), any<TraceContext>(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `when waiting for children, finishing last child calls scopes if transaction is already finished`() {
        val transaction = fixture.getSut(waitForChildren = true)
        val child = transaction.startChild("op")
        transaction.finish(SpanStatus.INVALID_ARGUMENT)
        verify(fixture.scopes, never()).captureTransaction(any(), any<TraceContext>(), anyOrNull(), anyOrNull())
        child.finish()
        verify(fixture.scopes, times(1)).captureTransaction(
            check {
                assertEquals(SpanStatus.INVALID_ARGUMENT, it.status)
            },
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `when finishing, unfinished spans won't have any automatic end-date or status`() {
        val transaction = fixture.getSut(samplingDecision = TracesSamplingDecision(true))
        // span with no status set
        val span = transaction.startChild("op") as Span
        span.finish(SpanStatus.INVALID_ARGUMENT)

        // span with a status
        val span1 = transaction.startChild("op2")

        transaction.finish(SpanStatus.INVALID_ARGUMENT)

        verify(fixture.scopes, times(1)).captureTransaction(
            check {
                assertEquals(2, it.spans.size)
                // span status/timestamp is retained
                assertNotNull(it.spans[0].status)
                assertNotNull(it.spans[0].timestamp)

                // span status/timestamp remains untouched
                assertNull(it.spans[1].status)
                assertNull(it.spans[1].timestamp)
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
        fixture.scopes.setUser(
            User().apply {
                id = "user-id"
            }
        )
        val replayId = SentryId()
        fixture.scopes.configureScope { it.replayId = replayId }
        val trace = transaction.traceContext()
        assertNotNull(trace) {
            assertEquals(transaction.spanContext.traceId, it.traceId)
            assertEquals("key", it.publicKey)
            assertEquals("environment", it.environment)
            assertEquals("release@3.0.0", it.release)
            assertEquals(transaction.name, it.transaction)
            assertEquals(replayId, it.replayId)
        }
    }

    @Test
    fun `returns trace state without userId if not send pii`() {
        val transaction = fixture.getSut({
            it.isTraceSampling = true
        })
        fixture.scopes.setUser(
            User().apply {
                id = "user-id"
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
        }
    }

    @Test
    fun `trace state does not change once computed`() {
        val transaction = fixture.getSut({
            it.isTraceSampling = true
        })
        val traceBeforeUserSet = transaction.traceContext()
        fixture.scopes.setUser(
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

            assertNull(it.userId)
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

        fixture.scopes.setUser(
            User().apply {
                id = "userId12345"
            }
        )
        val replayId = SentryId()
        fixture.scopes.configureScope { it.replayId = replayId }

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
            // assertTrue(it.value.contains("sentry-user_id=userId12345,"))
            assertTrue(it.value.contains("sentry-replay_id=$replayId"))
        }
    }

    @Test
    fun `returns baggage header without userId if not send pii`() {
        val transaction = fixture.getSut({
            it.isTraceSampling = true
            it.environment = "production"
            it.release = "1.0.99-rc.7"
        })

        fixture.scopes.setUser(
            User().apply {
                id = "userId12345"
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
            assertTrue(it.value.contains("sentry-transaction=name"))
            assertFalse(it.value.contains("sentry-user_id"))
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

        fixture.scopes.setUser(null)

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
        }
    }

    @Test
    fun `sets ITransaction data as tracecontext data in SentryTransaction`() {
        val transaction = fixture.getSut(samplingDecision = TracesSamplingDecision(true))
        transaction.setData("key", "val")
        transaction.finish()
        verify(fixture.scopes).captureTransaction(
            check {
                assertEquals("val", it.contexts.trace?.data?.get("key"))
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
        verify(fixture.scopes).captureTransaction(
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
    fun `when initialized without deadlineTimeout, does not schedule finish timer`() {
        val transaction = fixture.getSut()
        assertNull(transaction.deadlineTimeoutTask)
    }

    @Test
    fun `when initialized with deadlineTimeout, schedules finish timer`() {
        val transaction = fixture.getSut(deadlineTimeout = 50)

        assertTrue(transaction.isDeadlineTimerRunning.get())
        assertNotNull(transaction.deadlineTimeoutTask)
    }

    @Test
    fun `when deadline is reached transaction is finished`() {
        // when a transaction with a deadline timeout is created
        // and the tx and child keep on running
        val transaction = fixture.getSut(deadlineTimeout = 20)
        val span = transaction.startChild("op")

        // and the deadline is exceed
        await.untilFalse(transaction.isDeadlineTimerRunning)

        // then both tx + span should be force finished
        assertEquals(transaction.isFinished, true)
        assertEquals(SpanStatus.DEADLINE_EXCEEDED, transaction.status)
        assertEquals(SpanStatus.DEADLINE_EXCEEDED, span.status)
    }

    @Test
    fun `when transaction is finished before deadline is reached, deadline should not be running anymore`() {
        val transaction = fixture.getSut(deadlineTimeout = 1000)
        val span = transaction.startChild("op")

        span.finish(SpanStatus.OK)
        transaction.finish(SpanStatus.OK)

        assertEquals(transaction.isDeadlineTimerRunning.get(), false)
        assertNull(transaction.deadlineTimeoutTask)
        assertEquals(transaction.isFinished, true)
        assertEquals(SpanStatus.OK, transaction.status)
        assertEquals(SpanStatus.OK, span.status)
    }

    @Test
    fun `when initialized with idleTimeout it has no influence on deadline timeout`() {
        val transaction = fixture.getSut(idleTimeout = 3000, deadlineTimeout = 20)
        val deadlineTimeoutTask = transaction.deadlineTimeoutTask

        val span = transaction.startChild("op")
        // when the span finishes, it re-schedules the idle task
        span.finish()

        // but the deadline timeout task should not be re-scheduled
        assertEquals(deadlineTimeoutTask, transaction.deadlineTimeoutTask)
    }

    @Test
    fun `when initialized without idleTimeout, does not schedule finish timer`() {
        val transaction = fixture.getSut()
        assertNull(transaction.idleTimeoutTask)
    }

    @Test
    fun `when initialized with idleTimeout, schedules finish timer`() {
        val transaction = fixture.getSut(idleTimeout = 50)
        assertNotNull(transaction.idleTimeoutTask)
    }

    @Test
    fun `when no children and the transaction is idle, drops the transaction`() {
        val transaction = fixture.getSut(idleTimeout = 10)

        await.untilFalse(transaction.isFinishTimerRunning)

        verify(fixture.scopes, never()).captureTransaction(
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

        verify(fixture.scopes).captureTransaction(
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

        assertNull(transaction.idleTimeoutTask)
    }

    @Test
    fun `when a child is finished and the transaction is idle, resets the timer`() {
        val transaction = fixture.getSut(waitForChildren = true, idleTimeout = 3000)

        val initialTime = transaction.idleTimeoutTask!!.scheduledExecutionTime()

        val span = transaction.startChild("op")
        Thread.sleep(1)
        span.finish()

        val timerAfterFinishingChild = transaction.idleTimeoutTask!!.scheduledExecutionTime()

        assertTrue { timerAfterFinishingChild > initialTime }
    }

    @Test
    fun `when idle transaction has still unfinished children, does not reset the timer`() {
        val transaction = fixture.getSut(waitForChildren = true, idleTimeout = 3000)

        val span = transaction.startChild("op")
        val span2 = transaction.startChild("op2")
        Thread.sleep(1)
        span.finish()

        assertNull(transaction.idleTimeoutTask)
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

        verify(fixture.scopes).captureTransaction(
            check {
                assertEquals(2, it.spans.size)
                assertEquals(transaction.root.finishDate, span2.finishDate)
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

        verify(fixture.scopes).captureTransaction(
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

        verify(fixture.scopes).captureTransaction(
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
    fun `setting the same measurement multiple times from a child only keeps first value`() {
        val transaction = fixture.getSut()
        transaction.setMeasurementFromChild("metric1", 1.0f)
        transaction.setMeasurementFromChild("metric1", 2, MeasurementUnit.Duration.DAY)
        transaction.finish()

        verify(fixture.scopes).captureTransaction(
            check {
                assertEquals(1.0f, it.measurements["metric1"]!!.value)
                assertNull(it.measurements["metric1"]!!.unit)
            },
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `when transaction is created, but not profiled, compositePerformanceCollector is started anyway`() {
        val transaction = fixture.getSut()
        verify(fixture.compositePerformanceCollector).start(anyOrNull<ITransaction>())
    }

    @Test
    fun `when transaction is created and profiled compositePerformanceCollector is started`() {
        val transaction = fixture.getSut(optionsConfiguration = {
            it.profilesSampleRate = 1.0
        }, samplingDecision = TracesSamplingDecision(true, null, true, null))
        verify(fixture.compositePerformanceCollector).start(check<ITransaction> { assertEquals(transaction, it) })
    }

    @Test
    fun `when transaction is finished, compositePerformanceCollector is stopped`() {
        val transaction = fixture.getSut()
        transaction.finish()
        verify(fixture.compositePerformanceCollector).stop(check<ITransaction> { assertEquals(transaction, it) })
    }

    @Test
    fun `when a span is started and finished the compositePerformanceCollector gets notified`() {
        val transaction = fixture.getSut()

        val span = transaction.startChild("op.span")
        span.finish()

        verify(fixture.compositePerformanceCollector).onSpanStarted(check { assertEquals(span, it) })
        verify(fixture.compositePerformanceCollector).onSpanFinished(check { assertEquals(span, it) })
    }

    @Test
    fun `changing transaction name without source sets source to custom`() {
        val transaction = fixture.getSut()
        transaction.setName("new-name-1", TransactionNameSource.TASK)
        transaction.setName("new-name-2")

        assertEquals("new-name-2", transaction.name)
        assertEquals(TransactionNameSource.CUSTOM, transaction.transactionNameSource)
    }

    @Test
    fun `when spans have auto-finish enabled, finish them on transaction finish`() {
        val transaction = fixture.getSut(waitForChildren = true, idleTimeout = 50, trimEnd = true, samplingDecision = TracesSamplingDecision(true))

        // when a span is created with auto-finish
        val spanOptions = SpanOptions().apply {
            isIdle = true
        }
        val span = transaction.startChild("composition", null, spanOptions) as Span

        // and the transaction is finished
        transaction.finish()

        // then the span should be finished as well
        assertTrue(span.isFinished)

        // and the transaction should be captured
        verify(fixture.scopes).captureTransaction(
            check {
                assertEquals(1, it.spans.size)
                assertEquals(transaction.root.finishDate!!.nanoTimestamp(), span.finishDate!!.nanoTimestamp())
            },
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `when spans have auto-finish enabled, finish them on transaction finish using the transaction finish date`() {
        val transaction = fixture.getSut(waitForChildren = true, idleTimeout = 50, trimEnd = true, samplingDecision = TracesSamplingDecision(true))

        // when a span is created with auto-finish
        val spanOptions = SpanOptions().apply {
            isIdle = true
        }
        val span = transaction.startChild("composition", null, spanOptions) as Span

        // and the transaction is finished
        Thread.sleep(1)
        val transactionFinishDate = SentryAutoDateProvider().now()
        transaction.finish(SpanStatus.OK, transactionFinishDate)

        // then the span should be finished as well
        assertTrue(span.isFinished)

        // and the transaction should be captured
        verify(fixture.scopes).captureTransaction(
            check {
                assertEquals(1, it.spans.size)
                assertEquals(transactionFinishDate, span.finishDate)
            },
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `when spans have trim-start enabled, trim them on transaction finish`() {
        val transaction = fixture.getSut(waitForChildren = true, idleTimeout = 50, trimEnd = true, samplingDecision = TracesSamplingDecision(true))

        // when a parent span is created
        val spanOptions = SpanOptions().apply {
            isTrimStart = true
        }
        val parentSpan = transaction.startChild("composition", null, spanOptions) as Span

        // with a child which starts later
        Thread.sleep(5)
        val child1 = parentSpan.startChild("child1") as Span
        child1.finish()

        val child2 = parentSpan.startChild("child2") as Span
        Thread.sleep(5)
        child2.finish()

        parentSpan.finish()

        val expectedParentStartDate = child1.startDate
        val expectedParentEndDate = parentSpan.finishDate

        transaction.finish()

        assertTrue(parentSpan.isFinished)
        assertEquals(expectedParentStartDate, parentSpan.startDate)
        assertEquals(expectedParentEndDate, parentSpan.finishDate)

        verify(fixture.scopes).captureTransaction(
            check {
                assertEquals(3, it.spans.size)
            },
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `when spans have trim-end enabled, trim them on transaction finish`() {
        val transaction = fixture.getSut(waitForChildren = true, idleTimeout = 50, trimEnd = true, samplingDecision = TracesSamplingDecision(true))

        // when a parent span is created
        val spanOptions = SpanOptions().apply {
            isTrimEnd = true
        }
        val parentSpan = transaction.startChild("composition", null, spanOptions) as Span

        // with a child which starts later
        Thread.sleep(5)
        val child1 = parentSpan.startChild("child1") as Span
        child1.finish()

        val child2 = parentSpan.startChild("child2") as Span
        Thread.sleep(5)
        child2.finish()

        parentSpan.finish()

        val expectedParentStartDate = parentSpan.startDate
        val expectedParentEndDate = child2.finishDate

        transaction.finish()

        assertTrue(parentSpan.isFinished)
        assertEquals(expectedParentStartDate, parentSpan.startDate)
        assertEquals(expectedParentEndDate, parentSpan.finishDate)

        verify(fixture.scopes).captureTransaction(
            check {
                assertEquals(3, it.spans.size)
            },
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `when transaction is finished, collected performance data is cleared`() {
        val data = mutableListOf<PerformanceCollectionData>(mock(), mock())
        val mockPerformanceCollector = object : CompositePerformanceCollector {
            override fun start(transaction: ITransaction) {}
            override fun start(id: String) {}
            override fun onSpanStarted(span: ISpan) {}
            override fun onSpanFinished(span: ISpan) {}
            override fun stop(transaction: ITransaction): MutableList<PerformanceCollectionData> = data
            override fun stop(id: String): MutableList<PerformanceCollectionData> = data
            override fun close() {}
        }
        val transaction = fixture.getSut(optionsConfiguration = {
            it.profilesSampleRate = 1.0
        }, performanceCollector = mockPerformanceCollector)
        transaction.finish()
        assertTrue(data.isEmpty())
    }

    @Test
    fun `updateEndDate is ignored and returns false if span is not finished`() {
        val transaction = fixture.getSut()
        assertFalse(transaction.isFinished)
        assertNull(transaction.finishDate)
        assertFalse(transaction.updateEndDate(mock()))
        assertNull(transaction.finishDate)
    }

    @Test
    fun `updateEndDate updates finishDate and returns true if span is finished`() {
        val transaction = fixture.getSut()
        val endDate: SentryDate = mock()
        transaction.finish()
        assertTrue(transaction.isFinished)
        assertNotNull(transaction.finishDate)
        assertTrue(transaction.updateEndDate(endDate))
        assertEquals(endDate, transaction.finishDate)
    }

    @Test
    fun `when a finished transaction is force-finished it's a no-op`() {
        // when a transaction is created
        val transaction = fixture.getSut()

        // and it's finished
        transaction.finish(SpanStatus.OK)
        // but forceFinish is called as well
        transaction.forceFinish(SpanStatus.ABORTED, false, null)

        // then it should keep it's original status
        assertEquals(SpanStatus.OK, transaction.status)
    }

    @Test
    fun `when a transaction is force-finished all spans get finished as well`() {
        val transaction = fixture.getSut(
            waitForChildren = true,
            idleTimeout = 50,
            samplingDecision = TracesSamplingDecision(true)
        )

        // when two spans are created
        val spanOptions = SpanOptions()
        val span0 = transaction.startChild("load", null, spanOptions) as Span
        val span1 = transaction.startChild("load", null, spanOptions) as Span

        // and one span is finished but not the other, and the transaction is force-finished
        span0.finish(SpanStatus.OK)
        val span0FinishDate = span0.finishDate

        transaction.forceFinish(SpanStatus.ABORTED, false, null)

        // then the first span should keep it's status
        assertTrue(span0.isFinished)
        assertEquals(SpanStatus.OK, span0.status)
        assertEquals(span0FinishDate, span0.finishDate)

        // and the second span should have the same status as the transaction
        assertTrue(span1.isFinished)
        assertEquals(SpanStatus.ABORTED, span1.status)
        assertEquals(transaction.finishDate, span1.finishDate)

        // and the transaction should be captured with both spans
        verify(fixture.scopes).captureTransaction(
            check {
                assertEquals(2, it.spans.size)
            },
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `when a transaction with no childs is force-finished with dropIfNoChild set to false it should still be captured`() {
        // when a transaction is created
        val transaction = fixture.getSut(
            waitForChildren = true,
            idleTimeout = 50,
            samplingDecision = TracesSamplingDecision(true)
        )

        // and force-finished but dropping is disabled
        transaction.forceFinish(SpanStatus.ABORTED, false, null)

        // then a transaction should be captured with 0 spans
        verify(fixture.scopes).captureTransaction(
            check {
                assertEquals(0, it.spans.size)
            },
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `when a transaction with no childs is force-finished but dropIfNoChildren is true, it should be dropped`() {
        // when a transaction is created
        val transaction = fixture.getSut(
            waitForChildren = true,
            idleTimeout = 50,
            samplingDecision = TracesSamplingDecision(true)
        )

        // and force-finish with dropping enabled
        transaction.forceFinish(SpanStatus.ABORTED, true, null)

        // then the transaction should be captured with 0 spans
        verify(fixture.scopes, never()).captureTransaction(
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `when timer is cancelled, schedule finish does not crash`() {
        val tracer = fixture.getSut(idleTimeout = 50, deadlineTimeout = 100)
        tracer.timer!!.cancel()
        tracer.scheduleFinish()
    }

    @Test
    fun `when timer is cancelled, schedule finish finishes the transaction immediately`() {
        val tracer = fixture.getSut(idleTimeout = 50)
        tracer.startChild("load").finish()

        tracer.timer!!.cancel()
        tracer.scheduleFinish()

        assertTrue(tracer.isFinished)
        verify(fixture.scopes).captureTransaction(any(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `when a span is launched on the main thread, the thread info should be set correctly`() {
        val threadChecker = mock<IThreadChecker>()
        whenever(threadChecker.isMainThread).thenReturn(true)
        whenever(threadChecker.currentThreadName).thenReturn("main")

        val tracer = fixture.getSut(optionsConfiguration = { options ->
            options.threadChecker = threadChecker
        })
        val span = tracer.startChild("span.op")
        assertNotNull(span.getData(SpanDataConvention.THREAD_ID))
        assertEquals("main", span.getData(SpanDataConvention.THREAD_NAME))
    }

    @Test
    fun `when a span is launched on the background thread, the thread info should be set correctly`() {
        val threadChecker = mock<IThreadChecker>()
        whenever(threadChecker.isMainThread).thenReturn(false)
        whenever(threadChecker.currentThreadName).thenReturn("test")

        val tracer = fixture.getSut(optionsConfiguration = { options ->
            options.threadChecker = threadChecker
        })
        val span = tracer.startChild("span.op")
        assertNotNull(span.getData(SpanDataConvention.THREAD_ID))
        assertNotEquals("main", span.getData(SpanDataConvention.THREAD_NAME))
    }

    @Test
    fun `maxSpans is respected by nested child spans`() {
        val tracer = fixture.getSut(optionsConfiguration = { it.maxSpans = 5 })

        val nested = tracer.startChild("task", "parent span")
        repeat(10) {
            val child = nested.startChild("task", "span number $it")
            child.finish()
        }
        nested.finish()
        tracer.finish()

        assertEquals(5, tracer.children.size)
    }

    @Test
    fun `tracer is not finished when finishCallback is called`() {
        val transaction = fixture.getSut(transactionFinishedCallback = {
            assertFalse(it.isFinished)
            assertNotNull(it.finishDate)
        })
        assertFalse(transaction.isFinished)
        assertNull(transaction.finishDate)
        transaction.finish()
    }
}
