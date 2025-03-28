package io.sentry.opentelemetry

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.context.propagation.TextMapSetter
import io.opentelemetry.semconv.UrlAttributes
import io.sentry.BaggageHeader
import io.sentry.Sentry
import io.sentry.SentryTraceHeader
import io.sentry.opentelemetry.SentryOtelKeys.SENTRY_BAGGAGE_KEY
import io.sentry.opentelemetry.SentryOtelKeys.SENTRY_SCOPES_KEY
import io.sentry.opentelemetry.SentryOtelKeys.SENTRY_TRACE_KEY
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OtelSentryPropagatorTest {

    val spanStorage: SentryWeakSpanStorage = SentryWeakSpanStorage.getInstance()

    @BeforeTest
    fun setup() {
        Sentry.init("https://key@sentry.io/proj")
    }

    @AfterTest
    fun cleanup() {
        spanStorage.clear()
    }

    @Test
    fun `propagator registers for sentry-trace and baggage`() {
        val propagator = OtelSentryPropagator()
        assertEquals(listOf("sentry-trace", "baggage"), propagator.fields())
    }

    @Test
    fun `forks root scopes if none in context without headers`() {
        val propagator = OtelSentryPropagator()
        val carrier: Map<String, String> = mapOf()

        val newContext = propagator.extract(Context.root(), carrier, MapGetter())

        val scopes = newContext.get(SENTRY_SCOPES_KEY)
        assertNotNull(scopes)
        assertSame(Sentry.forkedRootScopes("test").parentScopes, scopes.parentScopes)
    }

    @Test
    fun `forks scopes from context if present without headers`() {
        val propagator = OtelSentryPropagator()
        val carrier: Map<String, String> = mapOf()
        val scopeInContext = Sentry.forkedRootScopes("test")

        val newContext = propagator.extract(Context.root().with(SENTRY_SCOPES_KEY, scopeInContext), carrier, MapGetter())

        val scopes = newContext.get(SENTRY_SCOPES_KEY)
        assertNotNull(scopes)
        assertSame(scopeInContext, scopes.parentScopes)
    }

    @Test
    fun `forks root scopes if none in context with headers`() {
        val propagator = OtelSentryPropagator()
        val carrier: Map<String, String> = mapOf(
            "sentry-trace" to "f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1",
            "baggage" to "sentry-environment=production,sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rand=0.456789,sentry-sample_rate=0.5,sentry-sampled=true,sentry-trace_id=df71f5972f754b4c85af13ff5c07017d"
        )

        val newContext = propagator.extract(Context.root(), carrier, MapGetter())

        val scopes = newContext.get(SENTRY_SCOPES_KEY)
        assertNotNull(scopes)
        assertSame(Sentry.forkedRootScopes("test").parentScopes, scopes.parentScopes)
    }

    @Test
    fun `forks scopes from context if present with headers`() {
        val propagator = OtelSentryPropagator()
        val carrier: Map<String, String> = mapOf(
            "sentry-trace" to "f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1",
            "baggage" to "sentry-environment=production,sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rand=0.456789,sentry-sample_rate=0.5,sentry-sampled=true,sentry-trace_id=df71f5972f754b4c85af13ff5c07017d"
        )
        val scopeInContext = Sentry.forkedRootScopes("test")

        val newContext = propagator.extract(Context.root().with(SENTRY_SCOPES_KEY, scopeInContext), carrier, MapGetter())

        val scopes = newContext.get(SENTRY_SCOPES_KEY)
        assertNotNull(scopes)
        assertSame(scopeInContext, scopes.parentScopes)
    }

    @Test
    fun `invalid sentry trace header returns context without modification`() {
        val propagator = OtelSentryPropagator()
        val carrier: Map<String, String> = mapOf(
            "sentry-trace" to "wrong",
            "baggage" to "sentry-environment=production,sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rand=0.456789,sentry-sample_rate=0.5,sentry-sampled=true,sentry-trace_id=df71f5972f754b4c85af13ff5c07017d"
        )
        val scopeInContext = Sentry.forkedRootScopes("test")

        val newContext = propagator.extract(Context.root().with(SENTRY_SCOPES_KEY, scopeInContext), carrier, MapGetter())

        val scopes = newContext.get(SENTRY_SCOPES_KEY)
        assertNotNull(scopes)
        assertSame(scopeInContext, scopes)
    }

    @Test
    fun `uses incoming headers`() {
        val propagator = OtelSentryPropagator()
        val carrier: Map<String, String> = mapOf(
            "sentry-trace" to "f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1",
            "baggage" to "sentry-environment=production,sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rand=0.456789,sentry-sample_rate=0.5,sentry-sampled=true,sentry-trace_id=df71f5972f754b4c85af13ff5c07017d"
        )
        val newContext = propagator.extract(Context.root(), carrier, MapGetter())

        val span = Span.fromContext(newContext)
        assertEquals("f9118105af4a2d42b4124532cd1065ff", span.spanContext.traceId)
        assertEquals("424cffc8f94feeee", span.spanContext.spanId)
        assertTrue(span.spanContext.isSampled)

        assertEquals("f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1", newContext.get(SENTRY_TRACE_KEY)?.value)
        assertEquals("sentry-environment=production,sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rand=0.456789,sentry-sample_rate=0.5,sentry-sampled=true,sentry-trace_id=df71f5972f754b4c85af13ff5c07017d", newContext.get(SENTRY_BAGGAGE_KEY)?.toHeaderString(null))
    }

    @Test
    fun `injects headers if no URL`() {
        val propagator = OtelSentryPropagator()
        val carrier = mutableMapOf<String, String>()

        val sentrySpan = mock<IOtelSpanWrapper>()
        whenever(sentrySpan.toSentryTrace()).thenReturn(SentryTraceHeader("f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1"))
        whenever(sentrySpan.toBaggageHeader(anyOrNull())).thenReturn(BaggageHeader("sentry-environment=production,sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rand=0.456789,sentry-sample_rate=0.5,sentry-sampled=true,sentry-trace_id=df71f5972f754b4c85af13ff5c07017d"))
        val otelSpanContext = SpanContext.create("f9118105af4a2d42b4124532cd1065ff", "424cffc8f94feeee", TraceFlags.getSampled(), TraceState.getDefault())
        val otelSpan = Span.wrap(otelSpanContext)
        spanStorage.storeSentrySpan(otelSpanContext, sentrySpan)

        propagator.inject(Context.root().with(otelSpan), carrier, MapSetter())

        assertEquals("f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1", carrier["sentry-trace"])
        assertEquals("sentry-environment=production,sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rand=0.456789,sentry-sample_rate=0.5,sentry-sampled=true,sentry-trace_id=df71f5972f754b4c85af13ff5c07017d", carrier["baggage"])
    }

    @Test
    fun `injects headers if URL in span attributes with default options`() {
        val propagator = OtelSentryPropagator()
        val carrier = mutableMapOf<String, String>()

        val otelAttributes = Attributes.of(UrlAttributes.URL_FULL, "https://sentry.io/some/path")
        val sentrySpan = mock<IOtelSpanWrapper>()
        whenever(sentrySpan.toSentryTrace()).thenReturn(SentryTraceHeader("f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1"))
        whenever(sentrySpan.toBaggageHeader(anyOrNull())).thenReturn(BaggageHeader("sentry-environment=production,sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rand=0.456789,sentry-sample_rate=0.5,sentry-sampled=true,sentry-trace_id=df71f5972f754b4c85af13ff5c07017d"))
        whenever(sentrySpan.openTelemetrySpanAttributes).thenReturn(otelAttributes)
        val otelSpanContext = SpanContext.create("f9118105af4a2d42b4124532cd1065ff", "424cffc8f94feeee", TraceFlags.getSampled(), TraceState.getDefault())
        val otelSpan = Span.wrap(otelSpanContext)
        spanStorage.storeSentrySpan(otelSpanContext, sentrySpan)

        propagator.inject(Context.root().with(otelSpan), carrier, MapSetter())

        assertEquals("f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1", carrier["sentry-trace"])
        assertEquals("sentry-environment=production,sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rand=0.456789,sentry-sample_rate=0.5,sentry-sampled=true,sentry-trace_id=df71f5972f754b4c85af13ff5c07017d", carrier["baggage"])
    }

    @Test
    fun `injects headers if URL in span attributes with tracePropagationTargets set to same url`() {
        Sentry.init { options ->
            options.dsn = "https://key@sentry.io/proj"
            options.setTracePropagationTargets(listOf("sentry.io"))
        }
        val propagator = OtelSentryPropagator()
        val carrier = mutableMapOf<String, String>()

        val otelAttributes = Attributes.of(UrlAttributes.URL_FULL, "https://sentry.io/some/path")
        val sentrySpan = mock<IOtelSpanWrapper>()
        whenever(sentrySpan.toSentryTrace()).thenReturn(SentryTraceHeader("f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1"))
        whenever(sentrySpan.toBaggageHeader(anyOrNull())).thenReturn(BaggageHeader("sentry-environment=production,sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rand=0.456789,sentry-sample_rate=0.5,sentry-sampled=true,sentry-trace_id=df71f5972f754b4c85af13ff5c07017d"))
        whenever(sentrySpan.openTelemetrySpanAttributes).thenReturn(otelAttributes)
        val otelSpanContext = SpanContext.create("f9118105af4a2d42b4124532cd1065ff", "424cffc8f94feeee", TraceFlags.getSampled(), TraceState.getDefault())
        val otelSpan = Span.wrap(otelSpanContext)
        spanStorage.storeSentrySpan(otelSpanContext, sentrySpan)

        propagator.inject(Context.root().with(otelSpan), carrier, MapSetter())

        assertEquals("f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1", carrier["sentry-trace"])
        assertEquals("sentry-environment=production,sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rand=0.456789,sentry-sample_rate=0.5,sentry-sampled=true,sentry-trace_id=df71f5972f754b4c85af13ff5c07017d", carrier["baggage"])
    }

    @Test
    fun `does not inject headers if URL in span attributes with tracePropagationTargets set to different url`() {
        Sentry.init { options ->
            options.dsn = "https://key@sentry.io/proj"
            options.setTracePropagationTargets(listOf("github.com"))
        }
        val propagator = OtelSentryPropagator()
        val carrier = mutableMapOf<String, String>()

        val otelAttributes = Attributes.of(UrlAttributes.URL_FULL, "https://sentry.io/some/path")
        val sentrySpan = mock<IOtelSpanWrapper>()
        whenever(sentrySpan.toSentryTrace()).thenReturn(SentryTraceHeader("f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1"))
        whenever(sentrySpan.toBaggageHeader(anyOrNull())).thenReturn(BaggageHeader("sentry-environment=production,sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rand=0.456789,sentry-sample_rate=0.5,sentry-sampled=true,sentry-trace_id=df71f5972f754b4c85af13ff5c07017d"))
        whenever(sentrySpan.openTelemetrySpanAttributes).thenReturn(otelAttributes)
        val otelSpanContext = SpanContext.create("f9118105af4a2d42b4124532cd1065ff", "424cffc8f94feeee", TraceFlags.getSampled(), TraceState.getDefault())
        val otelSpan = Span.wrap(otelSpanContext)
        spanStorage.storeSentrySpan(otelSpanContext, sentrySpan)

        propagator.inject(Context.root().with(otelSpan), carrier, MapSetter())

        assertNull(carrier["sentry-trace"])
        assertNull(carrier["baggage"])
    }

    @Test
    fun `does not inject headers if URL in span attributes with tracePropagationTargets set to same url but trace sampling disabled`() {
        Sentry.init { options ->
            options.dsn = "https://key@sentry.io/proj"
            options.setTracePropagationTargets(listOf("sentry.io"))
            options.isTraceSampling = false
        }
        val propagator = OtelSentryPropagator()
        val carrier = mutableMapOf<String, String>()

        val otelAttributes = Attributes.of(UrlAttributes.URL_FULL, "https://sentry.io/some/path")
        val sentrySpan = mock<IOtelSpanWrapper>()
        whenever(sentrySpan.toSentryTrace()).thenReturn(SentryTraceHeader("f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1"))
        whenever(sentrySpan.toBaggageHeader(anyOrNull())).thenReturn(BaggageHeader("sentry-environment=production,sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rand=0.456789,sentry-sample_rate=0.5,sentry-sampled=true,sentry-trace_id=df71f5972f754b4c85af13ff5c07017d"))
        whenever(sentrySpan.openTelemetrySpanAttributes).thenReturn(otelAttributes)
        val otelSpanContext = SpanContext.create("f9118105af4a2d42b4124532cd1065ff", "424cffc8f94feeee", TraceFlags.getSampled(), TraceState.getDefault())
        val otelSpan = Span.wrap(otelSpanContext)
        spanStorage.storeSentrySpan(otelSpanContext, sentrySpan)

        propagator.inject(Context.root().with(otelSpan), carrier, MapSetter())

        assertNull(carrier["sentry-trace"])
        assertNull(carrier["baggage"])
    }

    @Test
    fun `does not inject headers if sentry span missing`() {
        val propagator = OtelSentryPropagator()
        val carrier = mutableMapOf<String, String>()

        val otelSpanContext = SpanContext.create("f9118105af4a2d42b4124532cd1065ff", "424cffc8f94feeee", TraceFlags.getSampled(), TraceState.getDefault())
        val otelSpan = Span.wrap(otelSpanContext)

        propagator.inject(Context.root().with(otelSpan), carrier, MapSetter())

        assertNull(carrier["sentry-trace"])
        assertNull(carrier["baggage"])
    }

    @Test
    fun `does not inject headers if sentry span noop`() {
        val propagator = OtelSentryPropagator()
        val carrier = mutableMapOf<String, String>()

        val sentrySpan = mock<IOtelSpanWrapper>()
        whenever(sentrySpan.isNoOp).thenReturn(true)
        val otelSpanContext = SpanContext.create("f9118105af4a2d42b4124532cd1065ff", "424cffc8f94feeee", TraceFlags.getSampled(), TraceState.getDefault())
        val otelSpan = Span.wrap(otelSpanContext)
        spanStorage.storeSentrySpan(otelSpanContext, sentrySpan)

        propagator.inject(Context.root().with(otelSpan), carrier, MapSetter())

        assertNull(carrier["sentry-trace"])
        assertNull(carrier["baggage"])
    }

    @Test
    fun `does not inject headers if span is missing`() {
        val propagator = OtelSentryPropagator()
        val carrier = mutableMapOf<String, String>()

        propagator.inject(Context.root(), carrier, MapSetter())

        assertNull(carrier["sentry-trace"])
        assertNull(carrier["baggage"])
    }

    @Test
    fun `does not inject headers if span is invalid`() {
        val propagator = OtelSentryPropagator()
        val carrier = mutableMapOf<String, String>()

        propagator.inject(Context.root().with(Span.getInvalid()), carrier, MapSetter())

        assertNull(carrier["sentry-trace"])
        assertNull(carrier["baggage"])
    }
}

class MapGetter() : TextMapGetter<Map<String, String>> {

    override fun keys(carrier: Map<String, String>): MutableIterable<String> {
        return carrier.keys.toMutableList()
    }

    override fun get(carrier: Map<String, String>?, key: String): String? {
        return carrier?.get(key)
    }
}

class MapSetter() : TextMapSetter<MutableMap<String, String>> {
    override fun set(carrier: MutableMap<String, String>?, key: String, value: String) {
        carrier?.set(key, value)
    }
}
