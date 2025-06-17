package io.sentry.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.TraceId
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.context.propagation.TextMapSetter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.UrlAttributes
import io.sentry.Baggage
import io.sentry.BaggageHeader
import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.ITransaction
import io.sentry.Instrumenter
import io.sentry.SentryDate
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.SentryTraceHeader
import io.sentry.SpanOptions
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.protocol.TransactionNameSource
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.net.http.HttpHeaders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentrySpanProcessorTest {

    companion object {
        val SENTRY_TRACE_HEADER_STRING = "2722d9f6ec019ade60c776169d9a8904-cedf5b7571cb4972-1"
        val BAGGAGE_HEADER_STRING = "sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rate=1,sentry-trace_id=2722d9f6ec019ade60c776169d9a8904,sentry-transaction=HTTP%20GET"
    }

    private class Fixture {

        val options = SentryOptions().also {
            it.dsn = "https://key@sentry.io/proj"
            it.instrumenter = Instrumenter.OTEL
        }
        val scopes = mock<IScopes>()
        val transaction = mock<ITransaction>()
        val span = mock<ISpan>()
        val spanContext = mock<io.sentry.SpanContext>()
        lateinit var openTelemetry: OpenTelemetry
        lateinit var tracer: Tracer
        val sentryTrace = SentryTraceHeader(SENTRY_TRACE_HEADER_STRING)
        val baggage = Baggage.fromHeader(BAGGAGE_HEADER_STRING)

        fun setup() {
            whenever(scopes.isEnabled).thenReturn(true)
            whenever(scopes.options).thenReturn(options)
            whenever(scopes.startTransaction(any<TransactionContext>(), any<TransactionOptions>())).thenReturn(transaction)

            whenever(spanContext.operation).thenReturn("spanContextOp")
            whenever(spanContext.parentSpanId).thenReturn(io.sentry.SpanId("cedf5b7571cb4972"))

            whenever(transaction.spanContext).thenReturn(spanContext)
            whenever(span.spanContext).thenReturn(spanContext)
            whenever(span.toSentryTrace()).thenReturn(sentryTrace)
            whenever(transaction.toSentryTrace()).thenReturn(sentryTrace)

            val baggageHeader = BaggageHeader.fromBaggageAndOutgoingHeader(baggage, null)
            whenever(span.toBaggageHeader(any())).thenReturn(baggageHeader)
            whenever(transaction.toBaggageHeader(any())).thenReturn(baggageHeader)

            whenever(transaction.startChild(any<String>(), anyOrNull<String>(), anyOrNull<SentryDate>(), eq(Instrumenter.OTEL), any<SpanOptions>())).thenReturn(span)

            val sdkTracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SentrySpanProcessor(scopes))
                .build()

            openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(SentryPropagator()))
                .build()

            tracer = openTelemetry.getTracer("sentry-test")
        }
    }

    private val fixture = Fixture()

    @Test
    fun `requires start`() {
        val processor = SentrySpanProcessor()
        assertTrue(processor.isStartRequired)
    }

    @Test
    fun `requires end`() {
        val processor = SentrySpanProcessor()
        assertTrue(processor.isEndRequired)
    }

    @Test
    fun `ignores sentry client request`() {
        fixture.setup()
        givenSpanBuilder(SpanKind.CLIENT)
            .setAttribute(UrlAttributes.URL_FULL, "https://key@sentry.io/proj/some-api")
            .startSpan()

        thenNoTransactionIsStarted()
    }

    @Test
    fun `ignores sentry internal request`() {
        fixture.setup()
        givenSpanBuilder(SpanKind.CLIENT)
            .setAttribute(UrlAttributes.URL_FULL, "https://key@sentry.io/proj/some-api")
            .startSpan()

        thenNoTransactionIsStarted()
    }

    @Test
    fun `does nothing on start if Sentry has not been initialized`() {
        fixture.setup()
        val context = mock<Context>()
        val span = mock<ReadWriteSpan>()

        whenever(fixture.scopes.isEnabled).thenReturn(false)

        SentrySpanProcessor(fixture.scopes).onStart(context, span)

        verify(fixture.scopes).isEnabled
        verify(fixture.scopes).options
        verifyNoMoreInteractions(fixture.scopes)
        verifyNoInteractions(context, span)
    }

    @Test
    fun `does nothing on end if Sentry has not been initialized`() {
        fixture.setup()
        val span = mock<ReadableSpan>()

        whenever(fixture.scopes.isEnabled).thenReturn(false)

        SentrySpanProcessor(fixture.scopes).onEnd(span)

        verify(fixture.scopes).isEnabled
        verify(fixture.scopes).options
        verifyNoMoreInteractions(fixture.scopes)
        verifyNoInteractions(span)
    }

    @Test
    fun `does not start transaction for invalid SpanId`() {
        fixture.setup()
        val mockSpan = mock<ReadWriteSpan>()
        val mockSpanContext = mock<SpanContext>()
        whenever(mockSpanContext.spanId).thenReturn(SpanId.getInvalid())
        whenever(mockSpan.spanContext).thenReturn(mockSpanContext)
        SentrySpanProcessor(fixture.scopes).onStart(Context.current(), mockSpan)
        thenNoTransactionIsStarted()
    }

    @Test
    fun `does not start transaction for invalid TraceId`() {
        fixture.setup()
        val mockSpan = mock<ReadWriteSpan>()
        val mockSpanContext = mock<SpanContext>()
        whenever(mockSpanContext.spanId).thenReturn(SpanId.fromBytes("seed".toByteArray()))
        whenever(mockSpanContext.traceId).thenReturn(TraceId.getInvalid())
        whenever(mockSpan.spanContext).thenReturn(mockSpanContext)
        SentrySpanProcessor(fixture.scopes).onStart(Context.current(), mockSpan)
        thenNoTransactionIsStarted()
    }

    @Test
    fun `creates transaction for first otel span and span for second`() {
        fixture.setup()
        val otelSpan = givenSpanBuilder().startSpan()
        thenTransactionIsStarted(otelSpan, isContinued = false)

        val otelChildSpan = givenSpanBuilder(SpanKind.CLIENT, parentSpan = otelSpan)
            .startSpan()
        thenChildSpanIsStarted()

        otelChildSpan.end()
        thenChildSpanIsFinished()

        otelSpan.end()
        thenTransactionIsFinished()
    }

    private fun whenExtractingHeaders(sentryTrace: Boolean = true, baggage: Boolean = true): Context {
        val headers = givenHeaders(sentryTrace, baggage)
        return fixture.openTelemetry.propagators.textMapPropagator.extract(Context.current(), headers, HeaderGetter())
    }

    @Test
    fun `propagator can extract and result is used for transaction and attached on inject`() {
        fixture.setup()
        val extractedContext = whenExtractingHeaders()

        extractedContext.makeCurrent().use { _ ->
            val otelSpan = givenSpanBuilder().startSpan()
            thenTraceIdIsUsed(otelSpan)
            thenTransactionIsStarted(otelSpan, isContinued = true)

            val otelChildSpan = givenSpanBuilder(SpanKind.CLIENT, parentSpan = otelSpan)
                .startSpan()
            thenChildSpanIsStarted()

            val map = mutableMapOf<String, Any?>()
            fixture.openTelemetry.propagators.textMapPropagator.inject(Context.current().with(otelSpan), map, TestSetter())

            assertTrue(map.isNotEmpty())
            assertEquals(SENTRY_TRACE_HEADER_STRING, map["sentry-trace"])
            assertEquals(BAGGAGE_HEADER_STRING, map["baggage"])

            otelChildSpan.end()
            thenChildSpanIsFinished()

            otelSpan.end()
            thenTransactionIsFinished()
        }
    }

    @Test
    fun `incoming baggage without sentry-trace is ignored`() {
        fixture.setup()
        val extractedContext = whenExtractingHeaders(sentryTrace = false, baggage = true)

        extractedContext.makeCurrent().use { _ ->
            val otelSpan = givenSpanBuilder()
                .startSpan()
            thenTraceIdIsNotUsed(otelSpan)
            thenTransactionIsStarted(otelSpan, isContinued = false)

            val otelChildSpan = givenSpanBuilder(SpanKind.CLIENT, parentSpan = otelSpan)
                .startSpan()
            thenChildSpanIsStarted()

            otelChildSpan.end()
            thenChildSpanIsFinished()

            otelSpan.end()
            thenTransactionIsFinished()
        }
    }

    @Test
    fun `sentry-trace without baggage continues trace`() {
        fixture.setup()
        val extractedContext = whenExtractingHeaders(sentryTrace = true, baggage = false)

        extractedContext.makeCurrent().use { _ ->
            val otelSpan = givenSpanBuilder()
                .startSpan()

            thenTraceIdIsUsed(otelSpan)
            thenTransactionIsStarted(otelSpan, isContinued = true, continuesWithFilledBaggage = false)

            val otelChildSpan = givenSpanBuilder(SpanKind.CLIENT, parentSpan = otelSpan)
                .startSpan()
            thenChildSpanIsStarted()

            otelChildSpan.end()
            thenChildSpanIsFinished()

            otelSpan.end()
            thenTransactionIsFinished()
        }
    }

    @Test
    fun `sets status for errored span`() {
        fixture.setup()
        val otelSpan = givenSpanBuilder().startSpan()
        thenTransactionIsStarted(otelSpan, isContinued = false)

        val otelChildSpan = givenSpanBuilder(SpanKind.CLIENT, parentSpan = otelSpan)
            .startSpan()
        thenChildSpanIsStarted()

        otelChildSpan.setStatus(StatusCode.ERROR)
        otelChildSpan.setAttribute(UrlAttributes.URL_FULL, "http://github.com/getsentry/sentry-java")
        otelChildSpan.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 404L)

        otelChildSpan.end()
        thenChildSpanIsFinished(SpanStatus.NOT_FOUND)

        otelSpan.end()
        thenTransactionIsFinished()
    }

    @Test
    fun `sets status for errored span if not http`() {
        fixture.setup()
        val otelSpan = givenSpanBuilder().startSpan()
        thenTransactionIsStarted(otelSpan, isContinued = false)

        val otelChildSpan = givenSpanBuilder(SpanKind.CLIENT, parentSpan = otelSpan)
            .startSpan()
        thenChildSpanIsStarted()

        otelChildSpan.setStatus(StatusCode.ERROR)

        otelChildSpan.end()
        thenChildSpanIsFinished(SpanStatus.UNKNOWN_ERROR)

        otelSpan.end()
        thenTransactionIsFinished()
    }

    @Test
    fun `links error to OTEL transaction`() {
        fixture.setup()
        val extractedContext = whenExtractingHeaders()

        extractedContext.makeCurrent().use { _ ->
            val otelSpan = givenSpanBuilder().startSpan()
            thenTransactionIsStarted(otelSpan, isContinued = true)

            otelSpan.makeCurrent().use { _ ->
                val processedEvent = OpenTelemetryLinkErrorEventProcessor(fixture.scopes).process(SentryEvent(), Hint())
                val traceContext = processedEvent!!.contexts.trace!!

                assertEquals("2722d9f6ec019ade60c776169d9a8904", traceContext.traceId.toString())
                assertEquals(otelSpan.spanContext.spanId, traceContext.spanId.toString())
                assertEquals("cedf5b7571cb4972", traceContext.parentSpanId.toString())
                assertEquals("spanContextOp", traceContext.operation)
            }

            otelSpan.end()
            thenTransactionIsFinished()
        }
    }

    @Test
    fun `does not link error to OTEL transaction if instrumenter does not match`() {
        fixture.options.instrumenter = Instrumenter.SENTRY
        fixture.setup()

        val processedEvent = OpenTelemetryLinkErrorEventProcessor(fixture.scopes).process(SentryEvent(), Hint())

        thenNoTraceContextHasBeenAddedToEvent(processedEvent)
    }

    private fun givenSpanBuilder(spanKind: SpanKind = SpanKind.SERVER, parentSpan: Span? = null): SpanBuilder {
        val spanName = if (parentSpan == null) "testspan" else "childspan"
        val spanBuilder = fixture.tracer
            .spanBuilder(spanName)
            .setAttribute("some-attribute", "some-value")
            .setSpanKind(spanKind)

        parentSpan?.let { spanBuilder.setParent(Context.current().with(parentSpan)) }

        return spanBuilder
    }

    private fun givenHeaders(sentryTrace: Boolean = true, baggage: Boolean = true): HttpHeaders? {
        val headerMap = mutableMapOf<String, List<String>>().also {
            if (sentryTrace) {
                it.put("sentry-trace", listOf(SENTRY_TRACE_HEADER_STRING))
            }
            if (baggage) {
                it.put("baggage", listOf(BAGGAGE_HEADER_STRING))
            }
        }

        return HttpHeaders.of(headerMap) { _, _ -> true }
    }

    private fun thenTransactionIsStarted(otelSpan: Span, isContinued: Boolean = false, continuesWithFilledBaggage: Boolean = true) {
        if (isContinued) {
            verify(fixture.scopes).startTransaction(
                check<TransactionContext> {
                    assertEquals("testspan", it.name)
                    assertEquals(TransactionNameSource.CUSTOM, it.transactionNameSource)
                    assertEquals("testspan", it.operation)
                    assertEquals(otelSpan.spanContext.spanId, it.spanId.toString())
                    assertEquals("2722d9f6ec019ade60c776169d9a8904", it.traceId.toString())
                    assertEquals("cedf5b7571cb4972", it.parentSpanId?.toString())
                    assertTrue(it.parentSamplingDecision!!.sampled)
                    if (continuesWithFilledBaggage) {
                        assertEquals("2722d9f6ec019ade60c776169d9a8904", it.baggage?.traceId)
                        assertEquals(1.0, it.baggage?.sampleRate)
                        assertEquals("HTTP GET", it.baggage?.transaction)
                        assertEquals("502f25099c204a2fbf4cb16edc5975d1", it.baggage?.publicKey)
                        assertFalse(it.baggage!!.isMutable)
                    } else {
                        assertNotNull(it.baggage)
                        assertNull(it.baggage?.traceId)
                        assertNull(it.baggage?.sampleRate)
                        assertNull(it.baggage?.transaction)
                        assertNull(it.baggage?.publicKey)
                        assertTrue(it.baggage!!.isMutable)
                    }
                },
                check<TransactionOptions> {
                    assertNotNull(it.startTimestamp)
                    assertFalse(it.isBindToScope)
                }
            )
        } else {
            verify(fixture.scopes).startTransaction(
                check<TransactionContext> {
                    assertEquals("testspan", it.name)
                    assertEquals(TransactionNameSource.CUSTOM, it.transactionNameSource)
                    assertEquals("testspan", it.operation)
                    assertEquals(otelSpan.spanContext.spanId, it.spanId.toString())
                    assertEquals(otelSpan.spanContext.traceId, it.traceId.toString())
                    assertNull(it.parentSpanId)
                    assertNull(it.parentSamplingDecision)
                    assertNotNull(it.baggage)
                },
                check<TransactionOptions> {
                    assertNotNull(it.startTimestamp)
                    assertFalse(it.isBindToScope)
                }
            )
        }
    }

    private fun thenTraceIdIsUsed(otelSpan: Span) {
        assertEquals("2722d9f6ec019ade60c776169d9a8904", otelSpan.spanContext.traceId)
    }

    private fun thenTraceIdIsNotUsed(otelSpan: Span) {
        assertNotEquals("2722d9f6ec019ade60c776169d9a8904", otelSpan.spanContext.traceId)
    }

    private fun thenNoTransactionIsStarted() {
        verify(fixture.scopes, never()).startTransaction(
            any<TransactionContext>(),
            any<TransactionOptions>()
        )
    }

    private fun thenChildSpanIsStarted() {
        verify(fixture.transaction).startChild(
            eq("childspan"),
            eq("childspan"),
            any<SentryDate>(),
            eq(Instrumenter.OTEL),
            any<SpanOptions>()
        )
    }

    private fun thenChildSpanIsFinished(status: SpanStatus = SpanStatus.OK) {
        verify(fixture.span).finish(eq(status), any<SentryDate>())
    }

    private fun thenTransactionIsFinished() {
        verify(fixture.transaction).setContext(eq("otel"), any())
        verify(fixture.transaction).finish(eq(SpanStatus.OK), any<SentryDate>())
    }

    private fun thenNoTraceContextHasBeenAddedToEvent(event: SentryEvent?) {
        assertNotNull(event)
        assertNull(event.contexts.trace)
    }
}

class HeaderGetter : TextMapGetter<HttpHeaders> {
    override fun keys(headers: HttpHeaders): MutableIterable<String> {
        return headers.map().map { it.key }.toMutableList()
    }

    override fun get(headers: HttpHeaders?, key: String): String? {
        return headers?.firstValue(key)?.orElse(null)
    }
}

class TestSetter : TextMapSetter<MutableMap<String, Any?>> {
    override fun set(values: MutableMap<String, Any?>?, key: String, value: String) {
        values?.put(key, value)
    }
}
