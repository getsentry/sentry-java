package io.sentry

import io.sentry.protocol.SentryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransactionContextTest {

    @Test
    fun `when created using primary constructor, sampling decision and parent sampling are not set`() {
        val context = TransactionContext("name")
        assertNull(context.sampled)
        assertNull(context.parentSampled)
        assertEquals("name", context.name)
    }

    @Test
    fun `when context is created from trace header, parent sampling decision is set`() {
        val header = SentryTraceHeader(SentryId(), SpanId(), true)
        val context = TransactionContext.fromSentryTrace("name", header)
        assertNull(context.sampled)
        assertTrue(context.parentSampled!!)
    }
}
