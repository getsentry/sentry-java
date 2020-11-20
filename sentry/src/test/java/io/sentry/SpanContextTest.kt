package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SpanContextTest {

    @Test
    fun `when created with default constructor, generates trace id and span id`() {
        val trace = SpanContext()
        assertNotNull(trace.traceId)
        assertNotNull(trace.spanId)
    }

    @Test
    fun `sets tag`() {
        val trace = SpanContext()
        trace.setTag("tagName", "tagValue")
        assertEquals("tagValue", trace.tags!!["tagName"])
    }
}
