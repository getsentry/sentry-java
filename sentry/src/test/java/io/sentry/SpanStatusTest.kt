package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpanStatusTest {

    @Test
    fun `converts http status code to SpanStatus when code matches range`() {
        assertEquals(SpanStatus.OK, SpanStatus.fromHttpStatusCode(202))
    }

    @Test
    fun `converts http status code to SpanStatus equals specific code`() {
        assertEquals(SpanStatus.DEADLINE_EXCEEDED, SpanStatus.fromHttpStatusCode(504))
    }

    @Test
    fun `converts http status code to first SpanStatus matching specific code`() {
        assertEquals(SpanStatus.UNKNOWN, SpanStatus.fromHttpStatusCode(500))
    }

    @Test
    fun `returns null when no SpanStatus matches specific code`() {
        assertNull(SpanStatus.fromHttpStatusCode(302))
    }
}
