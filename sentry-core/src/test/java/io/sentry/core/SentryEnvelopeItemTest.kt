package io.sentry.core

import com.nhaarman.mockitokotlin2.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SentryEnvelopeItemTest {

    @Test
    fun `fromSession creates an envelope with a session item`() {
        val envelope = SentryEnvelope.fromSession(mock(), Session())
        envelope.items.forEach {
            assertEquals("application/json", it.header.contentType)
            assertEquals("session", it.header.type)
            assertNull(it.header.fileName)
            assertNotNull(it.data)
        }
    }
}
