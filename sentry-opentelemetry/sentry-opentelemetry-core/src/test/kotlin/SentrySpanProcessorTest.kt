package io.sentry.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.sentry.IHub
import io.sentry.ISpan
import io.sentry.ITransaction
import io.sentry.Instrumenter
import io.sentry.SentryOptions
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
import org.mockito.kotlin.whenever
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentrySpanProcessorTest {

    private class Fixture {

        lateinit var options: SentryOptions
        lateinit var hub: IHub
        lateinit var transaction: ITransaction
        lateinit var span: ISpan

        fun getSut(): Tracer {
            options = SentryOptions().also {
                it.dsn = "https://key@sentry.io/proj"
                it.instrumenter = Instrumenter.OTEL
            }
            hub = mock<IHub>()
            transaction = mock<ITransaction>()
            span = mock<ISpan>()

            whenever(hub.isEnabled).thenReturn(true)
            whenever(hub.options).thenReturn(options)
            whenever(hub.startTransaction(any<TransactionContext>(), any<TransactionOptions>())).thenReturn(transaction)
            whenever(transaction.startChild(any<String>(), anyOrNull<String>(), anyOrNull<Date>(), eq(Instrumenter.OTEL))).thenReturn(span)

            val sdkTracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SentrySpanProcessor(hub))
                .build()

            val openTelemetry: OpenTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(SentryPropagator()))
                .build()

            return openTelemetry.getTracer("sentry-test")
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
        val tracer = fixture.getSut()
        tracer.spanBuilder("testspan")
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(SemanticAttributes.HTTP_URL, "https://key@sentry.io/proj/some-api")
            .startSpan()

        verify(fixture.hub, never()).startTransaction(any<TransactionContext>(), any<TransactionOptions>())
    }

    @Test
    fun `ignores sentry internal request`() {
        val tracer = fixture.getSut()
        tracer.spanBuilder("testspan")
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(SemanticAttributes.HTTP_URL, "https://key@sentry.io/proj/some-api")
            .startSpan()

        verify(fixture.hub, never()).startTransaction(any<TransactionContext>(), any<TransactionOptions>())
    }

    @Test
    fun `creates transaction for first otel span and span for second`() {
        val tracer = fixture.getSut()
        val otelSpan = tracer.spanBuilder("testspan")
            .setSpanKind(SpanKind.SERVER)
            .startSpan()

        verify(fixture.hub).startTransaction(
            check<TransactionContext> {
                assertEquals("testspan", it.name)
                assertEquals(TransactionNameSource.CUSTOM, it.transactionNameSource)
                assertEquals("testspan", it.operation)
                assertEquals(otelSpan.spanContext.spanId, it.spanId.toString())
                assertEquals(otelSpan.spanContext.traceId, it.traceId.toString())
                assertNull(it.parentSpanId)
                assertNull(it.parentSamplingDecision)
                assertNull(it.baggage)
            },
            check<TransactionOptions> {
                assertNotNull(it.startTimestamp)
                assertFalse(it.isBindToScope)
            }
        )

        val otelChildSpan = tracer.spanBuilder("childspan")
            .setSpanKind(SpanKind.CLIENT)
            .setParent(Context.current().with(otelSpan))
            .startSpan()

        verify(fixture.transaction).startChild(eq("childspan"), eq("childspan"), any<Date>(), eq(Instrumenter.OTEL))

        otelSpan.end()

        verify(fixture.transaction).setContext(eq("otel"), any())
        verify(fixture.transaction).finish(eq(SpanStatus.OK), any<Date>())

        otelChildSpan.end()

        verify(fixture.span).finish(eq(SpanStatus.OK), any<Date>())
    }
}
