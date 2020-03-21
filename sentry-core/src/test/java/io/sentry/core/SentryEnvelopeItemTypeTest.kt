package io.sentry.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SentryEnvelopeItemTypeTest {

    @Test
    fun `Session enum type has a session type string`() {
        assertEquals("session", SentryEnvelopeItemType.Session.type)
    }

    @Test
    fun `Event enum type has a event type string`() {
        assertEquals("event", SentryEnvelopeItemType.Event.type)
    }
}
