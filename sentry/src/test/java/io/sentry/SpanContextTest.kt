package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

    @Test
    fun `null tag`() {
        val trace = SpanContext("op")
        trace.setTag("k", "v")
        trace.setTag("k", null)
        trace.setTag(null, null)
        assertTrue(trace.tags.isEmpty())
    }

    @Test
    fun `null data`() {
        val trace = SpanContext("op")
        trace.setData("k", "v")
        trace.setData("k", null)
        trace.setData(null, null)
        assertTrue(trace.data.isEmpty())
    }
}
