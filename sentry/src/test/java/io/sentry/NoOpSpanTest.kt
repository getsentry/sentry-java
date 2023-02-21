package io.sentry

import org.mockito.kotlin.mock
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class NoOpSpanTest {

    private val span = NoOpSpan.getInstance()

    @Test
    fun `startChild does not return null`() {
        assertNotNull(span.startChild("op"))
        assertNotNull(span.startChild("op", "desc"))
    }

    @Test
    fun `getSpanContext does not return null`() {
        assertNotNull(span.spanContext)
    }

    @Test
    fun `getOperation does not return null`() {
        assertNotNull(span.operation)
    }

    @Test
    fun `updateEndDate return false`() {
        assertFalse(span.updateEndDate(mock()))
    }
}
