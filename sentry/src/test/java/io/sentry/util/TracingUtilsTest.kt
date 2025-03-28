package io.sentry.util

import io.sentry.Baggage
import io.sentry.IScopes
import io.sentry.NoOpLogger
import io.sentry.NoOpSpan
import io.sentry.PropagationContext
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.Span
import io.sentry.SpanId
import io.sentry.SpanOptions
import io.sentry.TracesSamplingDecision
import io.sentry.TransactionContext
import io.sentry.protocol.SentryId
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TracingUtilsTest {

    class Fixture {
        val options = SentryOptions().apply {
            dsn = "https://key@sentry.io/proj"
        }
        val scopes = mock<IScopes>()
        val scope = Scope(options)
        lateinit var span: Span
        val preExistingBaggage = listOf("some-baggage-key=some-baggage-value")

        fun setup() {
            whenever(scopes.options).thenReturn(options)
            doAnswer { (it.arguments[0] as ScopeCallback).run(scope) }.whenever(scopes).configureScope(any())
            span = Span(
                TransactionContext("name", "op", TracesSamplingDecision(true)),
                SentryTracer(TransactionContext("name", "op", TracesSamplingDecision(true)), scopes),
                scopes,
                SpanOptions()
            )
        }
    }

    val fixture = Fixture()

    @Test
    fun `returns headers if allowed from scope without span`() {
        fixture.setup()

        val headers = TracingUtils.traceIfAllowed(fixture.scopes, "https://sentry.io/hello", fixture.preExistingBaggage, null)

        assertNotNull(headers)
        assertNotNull(headers.baggageHeader)
        assertEquals(fixture.scope.propagationContext.spanId, headers.sentryTraceHeader.spanId)
        assertEquals(fixture.scope.propagationContext.traceId, headers.sentryTraceHeader.traceId)
        assertEquals(fixture.scope.propagationContext.isSampled, headers.sentryTraceHeader.isSampled)
        assertTrue(headers.baggageHeader!!.value.contains("some-baggage-key=some-baggage-value"))
        assertTrue(headers.baggageHeader!!.value.contains("sentry-trace_id=${fixture.scope.propagationContext.traceId}"))
        assertFalse(fixture.scope.propagationContext.baggage!!.isMutable)
    }

    @Test
    fun `returns headers if allowed from scope if span is noop`() {
        fixture.setup()

        val headers = TracingUtils.traceIfAllowed(fixture.scopes, "https://sentry.io/hello", fixture.preExistingBaggage, NoOpSpan.getInstance())

        assertNotNull(headers)
        assertNotNull(headers.baggageHeader)
        assertEquals(fixture.scope.propagationContext.spanId, headers.sentryTraceHeader.spanId)
        assertEquals(fixture.scope.propagationContext.traceId, headers.sentryTraceHeader.traceId)
        assertEquals(fixture.scope.propagationContext.isSampled, headers.sentryTraceHeader.isSampled)
        assertTrue(headers.baggageHeader!!.value.contains("some-baggage-key=some-baggage-value"))
        assertFalse(fixture.scope.propagationContext.baggage!!.isMutable)
    }

    @Test
    fun `returns headers if allowed from scope if span is noop sampled=null`() {
        fixture.setup()
        fixture.scope.propagationContext.isSampled = null

        val headers = TracingUtils.traceIfAllowed(fixture.scopes, "https://sentry.io/hello", fixture.preExistingBaggage, NoOpSpan.getInstance())

        assertNotNull(headers)
        assertNotNull(headers.baggageHeader)
        assertEquals(fixture.scope.propagationContext.spanId, headers.sentryTraceHeader.spanId)
        assertEquals(fixture.scope.propagationContext.traceId, headers.sentryTraceHeader.traceId)
        assertEquals(fixture.scope.propagationContext.isSampled, headers.sentryTraceHeader.isSampled)
        assertTrue(headers.baggageHeader!!.value.contains("some-baggage-key=some-baggage-value"))
        assertFalse(fixture.scope.propagationContext.baggage!!.isMutable)
    }

    @Test
    fun `returns headers if allowed from scope if span is noop sampled=true`() {
        fixture.setup()
        fixture.scope.propagationContext.isSampled = true

        val headers = TracingUtils.traceIfAllowed(fixture.scopes, "https://sentry.io/hello", fixture.preExistingBaggage, NoOpSpan.getInstance())

        assertNotNull(headers)
        assertNotNull(headers.baggageHeader)
        assertEquals(fixture.scope.propagationContext.spanId, headers.sentryTraceHeader.spanId)
        assertEquals(fixture.scope.propagationContext.traceId, headers.sentryTraceHeader.traceId)
        assertEquals(fixture.scope.propagationContext.isSampled, headers.sentryTraceHeader.isSampled)
        assertTrue(headers.baggageHeader!!.value.contains("some-baggage-key=some-baggage-value"))
        assertFalse(fixture.scope.propagationContext.baggage!!.isMutable)
    }

    @Test
    fun `returns headers if allowed from scope if span is noop sampled=false`() {
        fixture.setup()
        fixture.scope.propagationContext.isSampled = false

        val headers = TracingUtils.traceIfAllowed(fixture.scopes, "https://sentry.io/hello", fixture.preExistingBaggage, NoOpSpan.getInstance())

        assertNotNull(headers)
        assertNotNull(headers.baggageHeader)
        assertEquals(fixture.scope.propagationContext.spanId, headers.sentryTraceHeader.spanId)
        assertEquals(fixture.scope.propagationContext.traceId, headers.sentryTraceHeader.traceId)
        assertEquals(fixture.scope.propagationContext.isSampled, headers.sentryTraceHeader.isSampled)
        assertTrue(headers.baggageHeader!!.value.contains("some-baggage-key=some-baggage-value"))
        assertFalse(fixture.scope.propagationContext.baggage!!.isMutable)
    }

    @Test
    fun `returns headers if allowed from scope without span leaving frozen baggage alone`() {
        fixture.scope.propagationContext = PropagationContext(SentryId(), SpanId(), null, Baggage.fromHeader("sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rate=1,sentry-trace_id=2722d9f6ec019ade60c776169d9a8904,sentry-transaction=HTTP%20GET").also { it.freeze() }, true)
        fixture.setup()

        val headers = TracingUtils.traceIfAllowed(fixture.scopes, "https://sentry.io/hello", fixture.preExistingBaggage, null)

        assertNotNull(headers)
        assertNotNull(headers.baggageHeader)
        assertEquals(fixture.scope.propagationContext.spanId, headers.sentryTraceHeader.spanId)
        assertEquals(fixture.scope.propagationContext.traceId, headers.sentryTraceHeader.traceId)
        assertTrue(headers.baggageHeader!!.value.contains("some-baggage-key=some-baggage-value"))
        assertTrue(headers.baggageHeader!!.value.contains("sentry-trace_id=2722d9f6ec019ade60c776169d9a8904"))
        assertFalse(headers.baggageHeader!!.value.contains("sentry-trace_id=${fixture.scope.propagationContext.traceId}"))
    }

    @Test
    fun `returns headers if allowed from span`() {
        fixture.setup()

        val headers = TracingUtils.traceIfAllowed(fixture.scopes, "https://sentry.io/hello", fixture.preExistingBaggage, fixture.span)

        assertNotNull(headers)
        assertNotNull(headers.baggageHeader)
        assertEquals(fixture.span.spanId, headers.sentryTraceHeader.spanId)
        assertEquals(fixture.span.traceId, headers.sentryTraceHeader.traceId)
        assertTrue(headers.baggageHeader!!.value.contains("some-baggage-key=some-baggage-value"))
    }

    @Test
    fun `does not return headers if not trace sampling without span`() {
        fixture.options.isTraceSampling = false
        fixture.setup()

        val headers = TracingUtils.traceIfAllowed(fixture.scopes, "https://sentry.io/hello", fixture.preExistingBaggage, null)

        assertNull(headers)
    }

    @Test
    fun `does not return headers if not trace sampling from span`() {
        fixture.options.isTraceSampling = false
        fixture.setup()

        val headers = TracingUtils.traceIfAllowed(fixture.scopes, "https://sentry.io/hello", fixture.preExistingBaggage, fixture.span)

        assertNull(headers)
    }

    @Test
    fun `does not return headers if host is disallowed without span`() {
        fixture.options.setTracePropagationTargets(listOf("some-host-that-does-not-exist"))
        fixture.setup()

        val headers = TracingUtils.traceIfAllowed(fixture.scopes, "https://sentry.io/hello", fixture.preExistingBaggage, null)

        assertNull(headers)
    }

    @Test
    fun `does not return headers if host is disallowed from span`() {
        fixture.options.setTracePropagationTargets(listOf("some-host-that-does-not-exist"))
        fixture.setup()

        val headers = TracingUtils.traceIfAllowed(fixture.scopes, "https://sentry.io/hello", fixture.preExistingBaggage, fixture.span)

        assertNull(headers)
    }

    @Test
    fun `start new trace sets propagation context on scope`() {
        fixture.setup()

        val propagationContextBefore = fixture.scope.propagationContext

        TracingUtils.startNewTrace(fixture.scopes)

        assertNotEquals(propagationContextBefore.traceId, fixture.scope.propagationContext.traceId)
        assertNotEquals(propagationContextBefore.spanId, fixture.scope.propagationContext.spanId)
    }

    @Test
    fun `updates mutable baggage`() {
        fixture.setup()
        // not frozen because it doesn't contain sentry-* keys
        fixture.scope.propagationContext = PropagationContext(SentryId(), SpanId(), null, Baggage.fromHeader(fixture.preExistingBaggage), true)

        TracingUtils.maybeUpdateBaggage(fixture.scope, fixture.options)

        assertEquals(fixture.scope.propagationContext.traceId.toString(), fixture.scope.propagationContext.baggage!!.traceId)
        assertFalse(fixture.scope.propagationContext.baggage!!.isMutable)
    }

    @Test
    fun `does not change frozen baggage`() {
        fixture.setup()
        // frozen automatically because it contains sentry-* keys
        fixture.scope.propagationContext = PropagationContext(SentryId(), SpanId(), null, Baggage.fromHeader("sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rate=1,sentry-trace_id=2722d9f6ec019ade60c776169d9a8904,sentry-transaction=HTTP%20GET"), true)

        TracingUtils.maybeUpdateBaggage(fixture.scope, fixture.options)

        assertEquals("2722d9f6ec019ade60c776169d9a8904", fixture.scope.propagationContext.baggage!!.traceId)
        assertFalse(fixture.scope.propagationContext.baggage!!.isMutable)
    }

    @Test
    fun `returns baggage if passed in`() {
        val incomingBaggage = Baggage(NoOpLogger.getInstance())
        val baggage = TracingUtils.ensureBaggage(
            incomingBaggage,
            null as? TracesSamplingDecision?
        )
        assertSame(incomingBaggage, baggage)
    }

    @Test
    fun `crates new baggage if null passed in that has sampleRand set and is mutable`() {
        val baggage = TracingUtils.ensureBaggage(
            null,
            null as? TracesSamplingDecision?
        )
        assertNotNull(baggage)
        assertNotNull(baggage.sampleRand)
        assertTrue(baggage.isMutable)
        assertFalse(baggage.isShouldFreeze)
    }

    @Test
    fun `backfills sampleRand on passed in baggage if missing`() {
        val incomingBaggage = Baggage(NoOpLogger.getInstance())
        val baggage = TracingUtils.ensureBaggage(
            incomingBaggage,
            null as? TracesSamplingDecision?
        )
        assertSame(incomingBaggage, baggage)
        assertNotNull(baggage.sampleRand)
        assertTrue(baggage.isMutable)
    }

    @Test
    fun `keeps sampleRand on passed in baggage if present`() {
        val incomingBaggage = Baggage(NoOpLogger.getInstance())
        incomingBaggage.sampleRand = 0.3
        val baggage = TracingUtils.ensureBaggage(
            incomingBaggage,
            null as? TracesSamplingDecision?
        )
        assertSame(incomingBaggage, baggage)
        assertEquals(0.3, baggage.sampleRand!!, 0.0001)
        assertTrue(baggage.isMutable)
    }

    @Test
    fun `does not backfill sampleRand on passed in baggage if frozen`() {
        val incomingBaggage = Baggage(NoOpLogger.getInstance())
        incomingBaggage.freeze()
        val baggage = TracingUtils.ensureBaggage(
            incomingBaggage,
            null as? TracesSamplingDecision?
        )
        assertSame(incomingBaggage, baggage)
        assertNull(baggage.sampleRand)
        assertFalse(baggage.isMutable)
    }

    @Test
    fun `freezes passed in baggage if should be frozen`() {
        // markes as shouldFreeze=true due to sentry values being present in header
        val incomingBaggage = Baggage.fromHeader("sentry-trace_id=a,sentry-transaction=sentryTransaction")
        val baggage = TracingUtils.ensureBaggage(
            incomingBaggage,
            null as? TracesSamplingDecision?
        )
        assertSame(incomingBaggage, baggage)
        assertNotNull(baggage.sampleRand)
        assertFalse(baggage.isMutable)
    }

    @Test
    fun `does not freeze passed in baggage if should not be frozen`() {
        // markes as shouldFreeze=false due to no sentry values being present in header
        val incomingBaggage = Baggage.fromHeader("a=b,c=d")
        val baggage = TracingUtils.ensureBaggage(
            incomingBaggage,
            null as? TracesSamplingDecision?
        )
        assertSame(incomingBaggage, baggage)
        assertNotNull(baggage.sampleRand)
        assertTrue(baggage.isMutable)
    }

    @Test
    fun `uses sample rand if passed in`() {
        val incomingBaggage = Baggage(NoOpLogger.getInstance())
        val baggage = TracingUtils.ensureBaggage(
            incomingBaggage,
            TracesSamplingDecision(true, null, 0.123)
        )
        assertSame(incomingBaggage, baggage)
        assertEquals(0.123, baggage.sampleRand!!, 0.0001)
    }

    @Test
    fun `uses sample rate and sampled flag true if passed in`() {
        val incomingBaggage = Baggage(NoOpLogger.getInstance())
        val baggage = TracingUtils.ensureBaggage(
            incomingBaggage,
            TracesSamplingDecision(true, 0.0001, null)
        )
        assertSame(incomingBaggage, baggage)
        val sampleRand = baggage.sampleRand
        assertNotNull(sampleRand)
        assertTrue(sampleRand < 0.0001)
        assertTrue(sampleRand >= 0.0)
    }

    @Test
    fun `uses sample rate and sampled flag false if passed in`() {
        val incomingBaggage = Baggage(NoOpLogger.getInstance())
        val baggage = TracingUtils.ensureBaggage(
            incomingBaggage,
            TracesSamplingDecision(false, 0.9999, null)
        )
        assertSame(incomingBaggage, baggage)
        val sampleRand = baggage.sampleRand
        assertNotNull(sampleRand)
        assertTrue(sampleRand < 1.0)
        assertTrue(sampleRand >= 0.9999)
    }
}
