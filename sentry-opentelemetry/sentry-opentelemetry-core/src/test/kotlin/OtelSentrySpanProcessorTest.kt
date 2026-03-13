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
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.UrlAttributes
import io.sentry.IScopes
import io.sentry.SentryOptions
import io.sentry.SpanStatus
import org.junit.Assert.assertTrue
import org.mockito.kotlin.mock
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

class OtelSentrySpanProcessorTest {

    companion object {
        val SENTRY_TRACE_ID = "2722d9f6ec019ade60c776169d9a8904"
        val SENTRY_TRACE_HEADER_STRING = "$SENTRY_TRACE_ID-cedf5b7571cb4972-1"
        val BAGGAGE_HEADER_STRING = "sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rate=1,sentry-trace_id=2722d9f6ec019ade60c776169d9a8904,sentry-transaction=HTTP%20GET"
    }

    private class Fixture {

        val options = SentryOptions().also {
            it.dsn = "https://key@sentry.io/proj"
            it.spanFactory = OtelSpanFactory()
            it.sampleRate = 1.0
            it.tracesSampleRate = 1.0
        }
        val scopes = mock<IScopes>()
        lateinit var openTelemetry: OpenTelemetry
        lateinit var tracer: Tracer

        fun setup() {
            whenever(scopes.isEnabled).thenReturn(true)
            whenever(scopes.options).thenReturn(options)

            val sdkTracerProvider = SdkTracerProvider.builder()
                .setSampler(SentrySampler(scopes))
                .addSpanProcessor(OtelSentrySpanProcessor(scopes))
                .addSpanProcessor(BatchSpanProcessor.builder(SentrySpanExporter()).build())
                .build()

            openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(OtelSentryPropagator()))
                .build()

            tracer = openTelemetry.getTracer("sentry-test")
        }
    }

    private val fixture = Fixture()

    @Test
    fun `requires start`() {
        val processor = OtelSentrySpanProcessor()
        assertTrue(processor.isStartRequired)
    }

    @Test
    fun `requires end`() {
        val processor = OtelSentrySpanProcessor()
        assertTrue(processor.isEndRequired)
    }

    @Test
    fun `ignores sentry client request`() {
        fixture.setup()
        val otelSpan = givenSpanBuilder(SpanKind.CLIENT)
            .setAttribute(UrlAttributes.URL_FULL, "https://key@sentry.io/proj/some-api")
            .startSpan()

        thenNoSpanIsCreated(otelSpan)
    }

    @Test
    fun `ignores sentry internal request`() {
        fixture.setup()
        val otelSpan = givenSpanBuilder(SpanKind.CLIENT)
            .setAttribute(UrlAttributes.URL_FULL, "https://key@sentry.io/proj/some-api")
            .startSpan()

        thenNoSpanIsCreated(otelSpan)
    }

    @Test
    fun `does nothing on start if Sentry has not been initialized`() {
        fixture.setup()
        val context = mock<Context>()
        val span = mock<ReadWriteSpan>()

        whenever(fixture.scopes.isEnabled).thenReturn(false)

        OtelSentrySpanProcessor(fixture.scopes).onStart(context, span)

        verify(fixture.scopes).isEnabled
        verify(fixture.scopes).options
        verifyNoMoreInteractions(fixture.scopes)
        verifyNoInteractions(context, span)
    }

    @Test
    fun `does not start transaction for invalid SpanId`() {
        fixture.setup()
        val mockSpan = mock<ReadWriteSpan>()
        val mockSpanContext = mock<SpanContext>()
        whenever(mockSpanContext.spanId).thenReturn(SpanId.getInvalid())
        whenever(mockSpan.spanContext).thenReturn(mockSpanContext)
        OtelSentrySpanProcessor(fixture.scopes).onStart(Context.current(), mockSpan)
        thenNoSpanIsCreated(mockSpan)
    }

    @Test
    fun `does not start transaction for invalid TraceId`() {
        fixture.setup()
        val mockSpan = mock<ReadWriteSpan>()
        val mockSpanContext = mock<SpanContext>()
        whenever(mockSpanContext.spanId).thenReturn(SpanId.fromBytes("seed".toByteArray()))
        whenever(mockSpanContext.traceId).thenReturn(TraceId.getInvalid())
        whenever(mockSpan.spanContext).thenReturn(mockSpanContext)
        OtelSentrySpanProcessor(fixture.scopes).onStart(Context.current(), mockSpan)
        thenNoSpanIsCreated(mockSpan)
    }

    @Test
    fun `creates transaction for first otel span and span for second`() {
        fixture.setup()
        val otelSpan = givenSpanBuilder().startSpan()
        thenSentrySpanIsCreated(otelSpan, isContinued = false)

        val otelChildSpan = givenSpanBuilder(SpanKind.CLIENT, parentSpan = otelSpan)
            .startSpan()
        thenChildSpanIsCreated(otelSpan, otelChildSpan)

        endSpanWithStatus(otelChildSpan)
        thenSpanIsFinished(otelChildSpan)

        endSpanWithStatus(otelSpan)
        thenSpanIsFinished(otelSpan)
    }

    private fun whenExtractingHeaders(sentryTrace: Boolean = true, baggage: Boolean = true): Context {
        val headers = givenHeaders(sentryTrace, baggage)
        return fixture.openTelemetry.propagators.textMapPropagator.extract(Context.current(), headers, OtelHeaderGetter())
    }

    @Test
    fun `propagator can extract and result is used for transaction and attached on inject`() {
        fixture.setup()
        val extractedContext = whenExtractingHeaders()

        extractedContext.makeCurrent().use { _ ->
            val otelSpan = givenSpanBuilder().startSpan()
            thenTraceIdIsUsed(otelSpan)
            thenSentrySpanIsCreated(otelSpan, isContinued = true)

            val otelChildSpan = givenSpanBuilder(SpanKind.CLIENT, parentSpan = otelSpan)
                .startSpan()
            thenChildSpanIsCreated(otelSpan, otelChildSpan)

            val map = mutableMapOf<String, Any?>()
            fixture.openTelemetry.propagators.textMapPropagator.inject(Context.current().with(otelSpan), map, OtelTestSetter())

            assertTrue(map.isNotEmpty())

            assertEquals("$SENTRY_TRACE_ID-${otelSpan.spanContext.spanId}-1", map["sentry-trace"])
            assertEquals(BAGGAGE_HEADER_STRING, map["baggage"])

            endSpanWithStatus(otelChildSpan)
            thenSpanIsFinished(otelChildSpan)

            endSpanWithStatus(otelSpan)
            thenSpanIsFinished(otelSpan)
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
            thenSentrySpanIsCreated(otelSpan, isContinued = false)

            val otelChildSpan = givenSpanBuilder(SpanKind.CLIENT, parentSpan = otelSpan)
                .startSpan()
            thenChildSpanIsCreated(otelSpan, otelChildSpan)

            endSpanWithStatus(otelChildSpan)
            thenSpanIsFinished(otelChildSpan)

            endSpanWithStatus(otelSpan)
            thenSpanIsFinished(otelSpan)
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
            thenSentrySpanIsCreated(otelSpan, isContinued = true, continuesWithFilledBaggage = false)

            val otelChildSpan = givenSpanBuilder(SpanKind.CLIENT, parentSpan = otelSpan)
                .startSpan()
            thenChildSpanIsCreated(otelSpan, otelChildSpan)

            endSpanWithStatus(otelChildSpan)
            thenSpanIsFinished(otelChildSpan)

            endSpanWithStatus(otelSpan)
            thenSpanIsFinished(otelSpan)
        }
    }

    @Test
    fun `sets status for errored span`() {
        fixture.setup()
        val otelSpan = givenSpanBuilder().startSpan()
        thenSentrySpanIsCreated(otelSpan, isContinued = false)

        val otelChildSpan = givenSpanBuilder(SpanKind.CLIENT, parentSpan = otelSpan)
            .startSpan()
        thenChildSpanIsCreated(otelSpan, otelChildSpan)

        otelChildSpan.setStatus(StatusCode.ERROR, "NOT_FOUND")
        otelChildSpan.setAttribute(UrlAttributes.URL_FULL, "http://github.com/getsentry/sentry-java")
        otelChildSpan.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 404L)

        otelChildSpan.end()
        thenSpanIsFinished(otelChildSpan, SpanStatus.NOT_FOUND)

        endSpanWithStatus(otelSpan, StatusCode.OK)
        thenSpanIsFinished(otelSpan)
    }

    @Test
    fun `sets status for errored span if not http`() {
        fixture.setup()
        val otelSpan = givenSpanBuilder().startSpan()
        thenSentrySpanIsCreated(otelSpan, isContinued = false)

        val otelChildSpan = givenSpanBuilder(SpanKind.CLIENT, parentSpan = otelSpan)
            .startSpan()
        thenChildSpanIsCreated(otelSpan, otelChildSpan)

        otelChildSpan.setStatus(StatusCode.ERROR)

        otelChildSpan.end()
        thenSpanIsFinished(otelChildSpan, SpanStatus.UNKNOWN_ERROR)

        endSpanWithStatus(otelSpan, StatusCode.OK)
        thenSpanIsFinished(otelSpan)
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

    private fun endSpanWithStatus(span: Span, status: StatusCode = StatusCode.OK) {
        span.setStatus(status)
        span.end()
    }

    private fun thenSentrySpanIsCreated(otelSpan: Span, isContinued: Boolean = false, continuesWithFilledBaggage: Boolean = true) {
        val sentrySpan = SentryWeakSpanStorage.getInstance().getSentrySpan(otelSpan.spanContext)
        assertNotNull(sentrySpan)
        val spanContext = sentrySpan.spanContext

        if (isContinued) {
            assertNull(spanContext.description)
            assertEquals("testspan", spanContext.operation)
            assertEquals(otelSpan.spanContext.spanId, spanContext.spanId.toString())
            assertEquals(SENTRY_TRACE_ID, spanContext.traceId.toString())
            assertEquals("cedf5b7571cb4972", spanContext.parentSpanId?.toString())
            assertEquals(true, spanContext.sampled)

            if (continuesWithFilledBaggage) {
                val baggage = spanContext.baggage
                assertNotNull(baggage)
                assertEquals(SENTRY_TRACE_ID, baggage.traceId)
                assertEquals(1.0, baggage.sampleRate)
                assertEquals("HTTP GET", baggage.transaction)
                assertEquals("502f25099c204a2fbf4cb16edc5975d1", baggage.publicKey)
                assertFalse(baggage.isMutable)
            } else {
                assertNotNull(spanContext.baggage)
                assertNull(spanContext.baggage?.traceId)
                assertNull(spanContext.baggage?.sampleRate)
                assertNull(spanContext.baggage?.transaction)
                assertNull(spanContext.baggage?.publicKey)
            }

            assertNotNull(sentrySpan.startDate)
        } else {
            assertNull(sentrySpan.description)
            assertEquals("testspan", sentrySpan.operation)
            assertEquals(otelSpan.spanContext.spanId, spanContext.spanId.toString())
            assertEquals(otelSpan.spanContext.traceId, spanContext.traceId.toString())
            assertNull(spanContext.parentSpanId)
            assertNotNull(sentrySpan.startDate)
        }
    }

    private fun thenTraceIdIsUsed(otelSpan: Span) {
        assertEquals(SENTRY_TRACE_ID, otelSpan.spanContext.traceId)
    }

    private fun thenTraceIdIsNotUsed(otelSpan: Span) {
        assertNotEquals(SENTRY_TRACE_ID, otelSpan.spanContext.traceId)
    }

    private fun thenNoSpanIsCreated(otelSpan: Span) {
        val sentrySpan = SentryWeakSpanStorage.getInstance().getSentrySpan(otelSpan.spanContext)
        assertNull(sentrySpan)
    }

    private fun thenChildSpanIsCreated(otelParentSpan: Span, otelChildSpan: Span) {
        val sentryParentSpan = SentryWeakSpanStorage.getInstance().getSentrySpan(otelParentSpan.spanContext)
        val sentryChildSpan = SentryWeakSpanStorage.getInstance().getSentrySpan(otelChildSpan.spanContext)
        assertNotNull(sentryParentSpan)
        assertNotNull(sentryChildSpan)
        assertEquals(sentryParentSpan.spanContext.spanId, sentryChildSpan.spanContext.parentSpanId)
        assertEquals(sentryChildSpan.operation, "childspan")
    }

    private fun thenSpanIsFinished(otelSpan: Span, status: SpanStatus = SpanStatus.OK) {
        val sentrySpan = SentryWeakSpanStorage.getInstance().getSentrySpan(otelSpan.spanContext)
        assertNotNull(sentrySpan)
        assertTrue(sentrySpan.isFinished)
        assertEquals(status, sentrySpan.status)
    }
}

class OtelHeaderGetter : TextMapGetter<HttpHeaders> {
    override fun keys(headers: HttpHeaders): MutableIterable<String> {
        return headers.map().map { it.key }.toMutableList()
    }

    override fun get(headers: HttpHeaders?, key: String): String? {
        return headers?.firstValue(key)?.orElse(null)
    }
}

class OtelTestSetter : TextMapSetter<MutableMap<String, Any?>> {
    override fun set(values: MutableMap<String, Any?>?, key: String, value: String) {
        values?.put(key, value)
    }
}
