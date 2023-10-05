package io.sentry.util

import io.sentry.Baggage
import io.sentry.IHub
import io.sentry.NoOpSpan
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.Span
import io.sentry.SpanOptions
import io.sentry.TracesSamplingDecision
import io.sentry.TransactionContext
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
import kotlin.test.assertTrue

class TracingUtilsTest {

    class Fixture {
        val options = SentryOptions().apply {
            dsn = "https://key@sentry.io/proj"
        }
        val hub = mock<IHub>()
        val scope = Scope(options)
        lateinit var span: Span
        val preExistingBaggage = listOf("some-baggage-key=some-baggage-value")

        fun setup() {
            whenever(hub.options).thenReturn(options)
            doAnswer { (it.arguments[0] as ScopeCallback).run(scope) }.whenever(hub).configureScope(any())
            span = Span(
                TransactionContext("name", "op", TracesSamplingDecision(true)),
                SentryTracer(TransactionContext("name", "op", TracesSamplingDecision(true)), hub),
                hub,
                null,
                SpanOptions()
            )
        }
    }

    val fixture = Fixture()

    @Test
    fun `returns headers if allowed from scope without span`() {
        fixture.setup()

        val headers = TracingUtils.traceIfAllowed(fixture.hub, "https://sentry.io/hello", fixture.preExistingBaggage, null)

        assertNotNull(headers)
        assertNotNull(headers.baggageHeader)
        assertEquals(fixture.scope.propagationContext.spanId, headers.sentryTraceHeader.spanId)
        assertEquals(fixture.scope.propagationContext.traceId, headers.sentryTraceHeader.traceId)
        assertTrue(headers.baggageHeader!!.value.contains("some-baggage-key=some-baggage-value"))
        assertTrue(headers.baggageHeader!!.value.contains("sentry-trace_id=${fixture.scope.propagationContext.traceId}"))
        assertFalse(fixture.scope.propagationContext.baggage!!.isMutable)
    }

    @Test
    fun `returns headers if allowed from scope if span is noop`() {
        fixture.setup()

        val headers = TracingUtils.traceIfAllowed(fixture.hub, "https://sentry.io/hello", fixture.preExistingBaggage, NoOpSpan.getInstance())

        assertNotNull(headers)
        assertNotNull(headers.baggageHeader)
        assertEquals(fixture.scope.propagationContext.spanId, headers.sentryTraceHeader.spanId)
        assertEquals(fixture.scope.propagationContext.traceId, headers.sentryTraceHeader.traceId)
        assertTrue(headers.baggageHeader!!.value.contains("some-baggage-key=some-baggage-value"))
        assertFalse(fixture.scope.propagationContext.baggage!!.isMutable)
    }

    @Test
    fun `returns headers if allowed from scope without span leaving frozen baggage alone`() {
        fixture.scope.propagationContext.baggage = Baggage.fromHeader("sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rate=1,sentry-trace_id=2722d9f6ec019ade60c776169d9a8904,sentry-transaction=HTTP%20GET").also { it.freeze() }
        fixture.setup()

        val headers = TracingUtils.traceIfAllowed(fixture.hub, "https://sentry.io/hello", fixture.preExistingBaggage, null)

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

        val headers = TracingUtils.traceIfAllowed(fixture.hub, "https://sentry.io/hello", fixture.preExistingBaggage, fixture.span)

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

        val headers = TracingUtils.traceIfAllowed(fixture.hub, "https://sentry.io/hello", fixture.preExistingBaggage, null)

        assertNull(headers)
    }

    @Test
    fun `does not return headers if not trace sampling from span`() {
        fixture.options.isTraceSampling = false
        fixture.setup()

        val headers = TracingUtils.traceIfAllowed(fixture.hub, "https://sentry.io/hello", fixture.preExistingBaggage, fixture.span)

        assertNull(headers)
    }

    @Test
    fun `does not return headers if host is disallowed without span`() {
        fixture.options.setTracePropagationTargets(listOf("some-host-that-does-not-exist"))
        fixture.setup()

        val headers = TracingUtils.traceIfAllowed(fixture.hub, "https://sentry.io/hello", fixture.preExistingBaggage, null)

        assertNull(headers)
    }

    @Test
    fun `does not return headers if host is disallowed from span`() {
        fixture.options.setTracePropagationTargets(listOf("some-host-that-does-not-exist"))
        fixture.setup()

        val headers = TracingUtils.traceIfAllowed(fixture.hub, "https://sentry.io/hello", fixture.preExistingBaggage, fixture.span)

        assertNull(headers)
    }

    @Test
    fun `start new trace sets propagation context on scope`() {
        fixture.setup()

        val propagationContextBefore = fixture.scope.propagationContext

        TracingUtils.startNewTrace(fixture.hub)

        assertNotEquals(propagationContextBefore.traceId, fixture.scope.propagationContext.traceId)
        assertNotEquals(propagationContextBefore.spanId, fixture.scope.propagationContext.spanId)
    }

    @Test
    fun `creates new baggage if none present`() {
        fixture.setup()
        assertNull(fixture.scope.propagationContext.baggage)

        TracingUtils.maybeUpdateBaggage(fixture.scope, fixture.options)

        assertNotNull(fixture.scope.propagationContext.baggage)
        assertEquals(fixture.scope.propagationContext.traceId.toString(), fixture.scope.propagationContext.baggage!!.traceId)
        assertFalse(fixture.scope.propagationContext.baggage!!.isMutable)
    }

    @Test
    fun `updates mutable baggage`() {
        fixture.setup()
        // not frozen because it doesn't contain sentry-* keys
        fixture.scope.propagationContext.baggage = Baggage.fromHeader(fixture.preExistingBaggage)

        TracingUtils.maybeUpdateBaggage(fixture.scope, fixture.options)

        assertEquals(fixture.scope.propagationContext.traceId.toString(), fixture.scope.propagationContext.baggage!!.traceId)
        assertFalse(fixture.scope.propagationContext.baggage!!.isMutable)
    }

    @Test
    fun `does not change frozen baggage`() {
        fixture.setup()
        // frozen automatically because it contains sentry-* keys
        fixture.scope.propagationContext.baggage = Baggage.fromHeader("sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rate=1,sentry-trace_id=2722d9f6ec019ade60c776169d9a8904,sentry-transaction=HTTP%20GET")

        TracingUtils.maybeUpdateBaggage(fixture.scope, fixture.options)

        assertEquals("2722d9f6ec019ade60c776169d9a8904", fixture.scope.propagationContext.baggage!!.traceId)
        assertFalse(fixture.scope.propagationContext.baggage!!.isMutable)
    }
}
