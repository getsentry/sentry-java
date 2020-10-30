package io.sentry

import com.nhaarman.mockitokotlin2.mock
import io.sentry.protocol.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SentryEnvelopeItemTest {

    @Test
    fun `fromSession creates an envelope with a session item`() {
        val envelope = SentryEnvelope.fromSession(mock(), createSession(), null)
        envelope.items.forEach {
            assertEquals("application/json", it.header.contentType)
            assertEquals(SentryItemType.Session, it.header.type)
            assertNull(it.header.fileName)
            assertNotNull(it.data)
        }
    }

    private fun createSession(): Session {
        return Session("dis", User(), "env", "rel")
    }
}
