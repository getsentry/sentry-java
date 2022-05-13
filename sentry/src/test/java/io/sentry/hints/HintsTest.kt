package io.sentry.hints

import io.sentry.hints.AttachmentContainerTest.Companion.newAttachment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HintsTest {
    @Test
    fun `getting as wrong class returns null`() {
        val hints = Hints()
        hints.set("hint1", "not a number")

        assertNull(hints.getAs("hint1", Int::class.java))
    }

    @Test
    fun `getting as correct class returns it`() {
        val hints = Hints()
        hints.set("hint1", "some string")

        assertEquals("some string", hints.getAs("hint1", String::class.java))
    }

    @Test
    fun `getting casted returns null if not contained`() {
        val hints = Hints()
        assertNull(hints.getAs("hint-does-not-exist", Int::class.java))
    }

    @Test
    fun `getting returns null if not contained`() {
        val hints = Hints()
        assertNull(hints.get("hint-does-not-exist"))
    }

    @Test
    fun `kotlin java interop for primitives does not work yet`() {
        val hints = Hints()

        hints.set("hint1", 1718)

        assertNull(hints.getAs("hint1", Long::class.java))
    }

    @Test
    fun `setting twice only keeps second value`() {
        val hints = Hints()

        hints.set("hint1", "some string")
        hints.set("hint1", "a different string")

        assertEquals("a different string", hints.getAs("hint1", String::class.java))
    }

    @Test
    fun `after removing the value is gone`() {
        val hints = Hints()

        hints.set("hint1", "some string")
        assertEquals("some string", hints.getAs("hint1", String::class.java))

        hints.remove("hint1")
        assertNull(hints.get("hint1"))
    }

    @Test
    fun `removing leaves other values`() {
        val hints = Hints()

        hints.set("hint1", "some string")
        assertEquals("some string", hints.getAs("hint1", String::class.java))
        hints.set("hint2", "another string")

        hints.remove("hint1")
        assertNull(hints.get("hint1"))
        assertEquals("another string", hints.getAs("hint2", String::class.java))
    }

    @Test
    fun `can retrieve AttachmentContainer`() {
        val hints = Hints()
        assertNotNull(hints.attachmentContainer)
    }

    @Test
    fun `can create hints with attachment`() {
        val attachment = newAttachment("test1")
        val hints = Hints.withAttachment(attachment)
        assertEquals(listOf(attachment), hints.attachmentContainer.all)
    }

    @Test
    fun `can create hints with attachments`() {
        val attachment1 = newAttachment("test1")
        val attachment2 = newAttachment("test1")
        val hints = Hints.withAttachments(listOf(attachment1, attachment2))
        assertEquals(listOf(attachment1, attachment2), hints.attachmentContainer.all)
    }
}
