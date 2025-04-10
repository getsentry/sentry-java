package io.sentry.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

class FeedbackTest {

    @Test
    fun `copying feedback wont have the same references`() {
        val feedback = Feedback("message")
        val unknown = mapOf(Pair("unknown", "unknown"))
        feedback.setUnknown(unknown)

        val clone = Feedback(feedback)

        assertNotNull(clone)
        assertNotSame(feedback, clone)
        assertNotSame(feedback.unknown, clone.unknown)
    }

    @Test
    fun `copying feedback will have the same values`() {
        val feedback = Feedback("message")
        feedback.name = "name"
        feedback.contactEmail = "contact@email.com"
        feedback.url = "url"
        feedback.setReplayId(SentryId("00000000-0000-0000-0000-000000000001"))
        feedback.setAssociatedEventId(SentryId("00000000-0000-0000-0000-000000000002"))
        feedback.unknown = mapOf(Pair("unknown", "unknown"))

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
}
