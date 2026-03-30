package io.sentry.opentelemetry.otlp

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.context.propagation.TextMapSetter
import io.sentry.Baggage
import io.sentry.Sentry
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenTelemetryOtlpPropagatorTest {

  @BeforeTest
  fun setup() {
    Sentry.init("https://key@sentry.io/proj")
  }

  @AfterTest
  fun teardown() {
    Sentry.close()
    Context.root().makeCurrent()
  }

  @Test
  fun `propagator registers for sentry-trace and baggage`() {
    val propagator = OpenTelemetryOtlpPropagator()
    assertEquals(listOf("sentry-trace", "baggage"), propagator.fields())
  }

  @Test
  fun `invalid sentry trace header returns context without modification`() {
    val propagator = OpenTelemetryOtlpPropagator()
    val carrier: Map<String, String> =
      mapOf(
        "sentry-trace" to "wrong",
        "baggage" to
          "sentry-environment=production,sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rand=0.456789,sentry-sample_rate=0.5,sentry-sampled=true,sentry-trace_id=df71f5972f754b4c85af13ff5c07017d",
      )
    val scopeInContext = Sentry.forkedRootScopes("test")

    val newContext = propagator.extract(Context.root(), carrier, MapGetter())

    val baggage = newContext.get(OpenTelemetryOtlpPropagator.SENTRY_BAGGAGE_KEY)
    assertNull(baggage)
  }

  @Test
  fun `ignores incoming headers when strict continuation rejects org id`() {
    Sentry.init { options ->
      options.dsn = "https://key@o2.ingest.sentry.io/123"
      options.isStrictTraceContinuation = true
    }
    val propagator = OpenTelemetryOtlpPropagator()
    val carrier: Map<String, String> =
      mapOf(
        "sentry-trace" to "f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1",
        "baggage" to "sentry-trace_id=f9118105af4a2d42b4124532cd1065ff,sentry-org_id=1",
      )

    val newContext = propagator.extract(Context.root(), carrier, MapGetter())

    assertFalse(Span.fromContext(newContext).spanContext.isValid)
    assertNull(newContext.get(OpenTelemetryOtlpPropagator.SENTRY_BAGGAGE_KEY))
  }

  @Test
  fun `uses incoming headers`() {
    val propagator = OpenTelemetryOtlpPropagator()
    val carrier: Map<String, String> =
      mapOf(
        "sentry-trace" to "f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1",
        "baggage" to
          "sentry-environment=production,sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rand=0.456789,sentry-sample_rate=0.5,sentry-sampled=true,sentry-trace_id=df71f5972f754b4c85af13ff5c07017d",
      )

    val newContext = propagator.extract(Context.root(), carrier, MapGetter())

    val span = Span.fromContext(newContext)
    assertTrue(span.spanContext.isValid)
    assertEquals("f9118105af4a2d42b4124532cd1065ff", span.spanContext.traceId)
    assertEquals("424cffc8f94feeee", span.spanContext.spanId)

    val baggage = newContext.get(OpenTelemetryOtlpPropagator.SENTRY_BAGGAGE_KEY)
    assertEquals("production", baggage?.environment)
  }

  @Test
  fun `extract sets sampled trace flag when sentry-trace has sampled=0`() {
    val propagator = OpenTelemetryOtlpPropagator()
    val carrier: Map<String, String> =
      mapOf(
        "sentry-trace" to "f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-0",
        "baggage" to
          "sentry-environment=production,sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rand=0.456789,sentry-sample_rate=0.5,sentry-sampled=false,sentry-trace_id=df71f5972f754b4c85af13ff5c07017d",
      )

    val newContext = propagator.extract(Context.root(), carrier, MapGetter())

    val span = Span.fromContext(newContext)
    assertTrue(span.spanContext.isValid)
    assertFalse(span.spanContext.traceFlags.isSampled)
  }

  @Test
  fun `extract sets sampled trace flag when sentry-trace has no sampling decision`() {
    val propagator = OpenTelemetryOtlpPropagator()
    val carrier: Map<String, String> =
      mapOf(
        "sentry-trace" to "f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee",
        "baggage" to
          "sentry-environment=production,sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rand=0.456789,sentry-sample_rate=0.5,sentry-trace_id=df71f5972f754b4c85af13ff5c07017d",
      )

    val newContext = propagator.extract(Context.root(), carrier, MapGetter())

    val span = Span.fromContext(newContext)
    assertTrue(span.spanContext.isValid)
    assertTrue(span.spanContext.traceFlags.isSampled)
  }

  @Test
  fun `extract does not store baggage in context when baggage header is missing`() {
    val propagator = OpenTelemetryOtlpPropagator()
    val carrier: Map<String, String> =
      mapOf("sentry-trace" to "f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1")

    val newContext = propagator.extract(Context.root(), carrier, MapGetter())

    val span = Span.fromContext(newContext)
    assertTrue(span.spanContext.isValid)

    val baggage = newContext.get(OpenTelemetryOtlpPropagator.SENTRY_BAGGAGE_KEY)
    assertNull(baggage)
  }

  @Test
  fun `injects headers`() {
    val propagator = OpenTelemetryOtlpPropagator()
    val carrier = mutableMapOf<String, String>()

    val otelSpanContext =
      SpanContext.create(
        "f9118105af4a2d42b4124532cd1065ff",
        "424cffc8f94feeee",
        TraceFlags.getSampled(),
        TraceState.getDefault(),
      )
    val otelSpan = Span.wrap(otelSpanContext)
    val baggage =
      Baggage.fromHeader(
        "sentry-environment=production,sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rand=0.456789,sentry-sample_rate=0.5,sentry-sampled=true,sentry-trace_id=df71f5972f754b4c85af13ff5c07017d"
      )
    val context =
      Context.root().with(otelSpan).with(OpenTelemetryOtlpPropagator.SENTRY_BAGGAGE_KEY, baggage)

    propagator.inject(context, carrier, MapSetter())

    assertEquals("f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1", carrier["sentry-trace"])
    assertEquals(
      "sentry-environment=production,sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rand=0.456789,sentry-sample_rate=0.5,sentry-sampled=true,sentry-trace_id=df71f5972f754b4c85af13ff5c07017d",
      carrier["baggage"],
    )
  }

  @Test
  fun `does not inject headers if span is invalid`() {
    val propagator = OpenTelemetryOtlpPropagator()
    val carrier = mutableMapOf<String, String>()

    propagator.inject(Context.root(), carrier, MapSetter())

    assertNull(carrier["sentry-trace"])
    assertNull(carrier["baggage"])
  }

  @Test
  fun `does not inject baggage header when baggage is missing from context`() {
    val propagator = OpenTelemetryOtlpPropagator()
    val carrier = mutableMapOf<String, String>()

    val otelSpanContext =
      SpanContext.create(
        "f9118105af4a2d42b4124532cd1065ff",
        "424cffc8f94feeee",
        TraceFlags.getSampled(),
        TraceState.getDefault(),
      )
    val otelSpan = Span.wrap(otelSpanContext)

    propagator.inject(Context.root().with(otelSpan), carrier, MapSetter())

    assertEquals("f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1", carrier["sentry-trace"])
    assertNull(carrier["baggage"])
  }
}

class MapGetter : TextMapGetter<Map<String, String>> {
  override fun keys(carrier: Map<String, String>): MutableIterable<String> {
    return carrier.keys.toMutableList()
  }

  override fun get(carrier: Map<String, String>?, key: String): String? {
    return carrier?.get(key)
  }
}

class MapSetter : TextMapSetter<MutableMap<String, String>> {
  override fun set(carrier: MutableMap<String, String>?, key: String, value: String) {
    carrier?.put(key, value)
  }
}
