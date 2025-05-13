package io.sentry.protocol

import io.sentry.ILogger
import org.mockito.kotlin.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

class FeedbackTest {

    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() = Feedback("message").apply {
            name = "name"
            contactEmail = "contact@email.com"
            url = "url"
            setReplayId(SentryId("00000000-0000-0000-0000-000000000001"))
            setAssociatedEventId(SentryId("00000000-0000-0000-0000-000000000002"))
            unknown = mapOf(Pair("unknown", "unknown"))
        }
    }
    private val fixture = Fixture()

    @Test
    fun `copying feedback wont have the same references`() {
        val feedback = fixture.getSut()

        val clone = Feedback(feedback)

        assertNotNull(clone)
        assertNotSame(feedback, clone)
        assertNotSame(feedback.unknown, clone.unknown)
    }

    @Test
    fun `copying feedback will have the same values`() {
        val feedback = fixture.getSut()

        val clone = Feedback(feedback)
        assertEquals("message", clone.message)
        assertEquals("name", clone.name)
        assertEquals("contact@email.com", clone.contactEmail)
        assertEquals("url", clone.url)
        assertEquals("00000000000000000000000000000001", clone.replayId.toString())
        assertEquals("00000000000000000000000000000002", clone.associatedEventId.toString())
        assertNotNull(clone.unknown) {
            assertEquals("unknown", it["unknown"])
        }
    }

    @Test
    fun `setting a message longer than 4096 characters truncates the message`() {
        val feedback = fixture.getSut()
        feedback.message = "X".repeat(4095) + "Y" + "Z"
        val expectedMessage = "X".repeat(4095) + "Y"
        assertEquals(expectedMessage, feedback.message)
        assertEquals(4096, feedback.message.length)
    }
}
