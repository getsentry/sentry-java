package io.sentry

import junit.framework.TestCase.assertEquals
import kotlin.test.Test

class SentryUUIDTest {

    @Test
    fun `generated SentryID is 32 characters long`() {
        val sentryId = SentryUUID.generateSentryId()
        assertEquals(32, sentryId.length)
    }

    @Test
    fun `generated SpanID is 16 characters long`() {
        val sentryId = SentryUUID.generateSpanId()
        assertEquals(16, sentryId.length)
    }
}
