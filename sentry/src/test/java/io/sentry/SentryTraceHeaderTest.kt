package io.sentry

import io.sentry.protocol.SentryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SentryTraceHeaderTest {

    @Test
    fun `when sentry-trace header is incorrect throws exception`() {
        val sentryId = SentryId()
        val ex = assertFailsWith<InvalidSentryTraceHeaderException> { SentryTraceHeader("$sentryId") }
        assertEquals("sentry-trace header does not conform to expected format: $sentryId", ex.message)
    }
}
