package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TraceTest {

    @Test
    fun `when created with default constructor, generates trace id and span id`() {
        val trace = Trace()
        assertNotNull(trace.traceId)
        assertNotNull(trace.spanId)
    }

    @Test
    fun `sets tag`() {
        val trace = Trace()
        trace.setTag("tagName", "tagValue")
        assertEquals("tagValue", trace.tags["tagName"])
    }

    @Test
    fun `cloning replicates all properties`() {
        val trace = Trace()
        trace.status = SpanStatus.CANCELLED
        trace.description = "some description"
        trace.tags = mapOf("tag1" to "value1")
        trace.op = "http"
        val clone = trace.clone() as Trace
        assertEquals(trace.spanId, clone.spanId)
        assertEquals(trace.traceId, clone.traceId)
        assertEquals(trace.op, clone.op)
        assertEquals(trace.description, clone.description)
        assertEquals(trace.status, clone.status)
        assertEquals(trace.tags, clone.tags)
    }
}
