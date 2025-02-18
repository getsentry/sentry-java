package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SpanContextTest {

    @Test
    fun `when created with default constructor, generates trace id and span id`() {
        val trace = SpanContext("op")
        assertNotNull(trace.traceId)
        assertNotNull(trace.spanId)
    }

    @Test
    fun `sets tag`() {
        val trace = SpanContext("op")
        trace.setTag("tagName", "tagValue")
        assertEquals("tagValue", trace.tags["tagName"])
    }

    @Test
    fun `updates sampling decision on baggage`() {
        val trace = SpanContext("op")
        trace.baggage = Baggage.fromHeader("a=b")
        trace.samplingDecision = TracesSamplingDecision(true, 0.1, 0.2)

        assertEquals("true", trace.baggage?.sampled)
        assertEquals("0.1", trace.baggage?.sampleRate)
        assertEquals("0.2", trace.baggage?.sampleRand)
    }
}
