package io.sentry.hints

import io.sentry.hints.AttachmentsTest.Companion.newAttachment
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
    fun `kotlin java interop for primitives works for float`() {
        val hints = Hints()
        hints.set("hint1", 1.3f)
        assertEquals(1.3f, hints.getAs("hint1", Float::class.java))
    }

    @Test
    fun `kotlin java interop for primitives works for double`() {
        val hints = Hints()
        hints.set("hint1", 1.4)
        assertEquals(1.4, hints.getAs("hint1", Double::class.java))
    }

    @Test
    fun `kotlin java interop for primitives works for long`() {
        val hints = Hints()
        hints.set("hint1", 1718L)
        assertEquals(1718L, hints.getAs("hint1", Long::class.java))
    }

    @Test
    fun `kotlin java interop for primitives works for int`() {
        val hints = Hints()
        hints.set("hint1", 123)
        assertEquals(123, hints.getAs("hint1", Int::class.java))
    }

    @Test
    fun `kotlin java interop for primitives works for short`() {
        val hints = Hints()
        val s: Short = 123
        hints.set("hint1", s)
        assertEquals(s, hints.getAs("hint1", Short::class.java))
    }

    @Test
    fun `kotlin java interop for primitives works for byte`() {
        val hints = Hints()
        val b: Byte = 1
        hints.set("hint1", b)
        assertEquals(b, hints.getAs("hint1", Byte::class.java))
    }

    @Test
    fun `kotlin java interop for primitives works for char`() {
        val hints = Hints()
        hints.set("hint1", 'a')
        assertEquals('a', hints.getAs("hint1", Char::class.java))
    }

    @Test
    fun `kotlin java interop for primitives works for boolean`() {
        val hints = Hints()
        hints.set("hint1", true)
        assertEquals(true, hints.getAs("hint1", Boolean::class.java))
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
    fun `can retrieve Attachments`() {
        val hints = Hints()
        assertNotNull(hints.attachments)
    }

    @Test
    fun `can create hints with attachment`() {
        val attachment = newAttachment("test1")
        val hints = Hints.withAttachment(attachment)
        assertEquals(listOf(attachment), hints.attachments)
    }

    @Test
    fun `can create hints with attachments`() {
        val attachment1 = newAttachment("test1")
        val attachment2 = newAttachment("test1")
        val hints = Hints.withAttachments(listOf(attachment1, attachment2))
        assertEquals(listOf(attachment1, attachment2), hints.attachments)
    }
}
