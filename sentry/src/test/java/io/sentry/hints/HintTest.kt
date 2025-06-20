package io.sentry.hints

import io.sentry.Attachment
import io.sentry.CachedEvent
import io.sentry.Hint
import io.sentry.TypeCheckHint.SENTRY_TYPE_CHECK_HINT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HintTest {
  @Test
  fun `getting as wrong class returns null`() {
    val hint = Hint()
    hint.set("hint1", "not a number")

    assertNull(hint.getAs("hint1", Int::class.java))
  }

  @Test
  fun `getting as correct class returns it`() {
    val hint = Hint()
    hint.set("hint1", "some string")

    assertEquals("some string", hint.getAs("hint1", String::class.java))
  }

  @Test
  fun `getting casted returns null if not contained`() {
    val hint = Hint()
    assertNull(hint.getAs("hint-does-not-exist", Int::class.java))
  }

  @Test
  fun `getting returns null if not contained`() {
    val hint = Hint()
    assertNull(hint.get("hint-does-not-exist"))
  }

  @Test
  fun `kotlin java interop for primitives works for float`() {
    val hint = Hint()
    hint.set("hint1", 1.3f)
    assertEquals(1.3f, hint.getAs("hint1", Float::class.java))
  }

  @Test
  fun `kotlin java interop for primitives works for double`() {
    val hint = Hint()
    hint.set("hint1", 1.4)
    assertEquals(1.4, hint.getAs("hint1", Double::class.java))
  }

  @Test
  fun `kotlin java interop for primitives works for long`() {
    val hint = Hint()
    hint.set("hint1", 1718L)
    assertEquals(1718L, hint.getAs("hint1", Long::class.java))
  }

  @Test
  fun `kotlin java interop for primitives works for int`() {
    val hint = Hint()
    hint.set("hint1", 123)
    assertEquals(123, hint.getAs("hint1", Int::class.java))
  }

  @Test
  fun `kotlin java interop for primitives works for short`() {
    val hint = Hint()
    val s: Short = 123
    hint.set("hint1", s)
    assertEquals(s, hint.getAs("hint1", Short::class.java))
  }

  @Test
  fun `kotlin java interop for primitives works for byte`() {
    val hint = Hint()
    val b: Byte = 1
    hint.set("hint1", b)
    assertEquals(b, hint.getAs("hint1", Byte::class.java))
  }

  @Test
  fun `kotlin java interop for primitives works for char`() {
    val hint = Hint()
    hint.set("hint1", 'a')
    assertEquals('a', hint.getAs("hint1", Char::class.java))
  }

  @Test
  fun `kotlin java interop for primitives works for boolean`() {
    val hint = Hint()
    hint.set("hint1", true)
    assertEquals(true, hint.getAs("hint1", Boolean::class.java))
  }

  @Test
  fun `setting twice only keeps second value`() {
    val hint = Hint()

    hint.set("hint1", "some string")
    hint.set("hint1", "a different string")

    assertEquals("a different string", hint.getAs("hint1", String::class.java))
  }

  @Test
  fun `after removing the value is gone`() {
    val hint = Hint()

    hint.set("hint1", "some string")
    assertEquals("some string", hint.getAs("hint1", String::class.java))

    hint.remove("hint1")
    assertNull(hint.get("hint1"))
  }

  @Test
  fun `removing leaves other values`() {
    val hint = Hint()

    hint.set("hint1", "some string")
    assertEquals("some string", hint.getAs("hint1", String::class.java))
    hint.set("hint2", "another string")

    hint.remove("hint1")
    assertNull(hint.get("hint1"))
    assertEquals("another string", hint.getAs("hint2", String::class.java))
  }

  @Test
  fun `can retrieve Attachments`() {
    val hint = Hint()
    assertNotNull(hint.attachments)
  }

  @Test
  fun `can create hints with attachment`() {
    val attachment = newAttachment("test1")
    val hint = Hint.withAttachment(attachment)
    assertEquals(listOf(attachment), hint.attachments)
  }

  @Test
  fun `can create hints with attachments`() {
    val attachment1 = newAttachment("test1")
    val attachment2 = newAttachment("test1")
    val hint = Hint.withAttachments(listOf(attachment1, attachment2))
    assertEquals(listOf(attachment1, attachment2), hint.attachments)
  }

  @Test
  fun `can add an attachment`() {
    val hint = Hint()
    val attachment = newAttachment("test1")
    hint.addAttachment(attachment)

    assertEquals(listOf(attachment), hint.attachments)
  }

  @Test
  fun `can add multiple attachments`() {
    val hint = Hint()
    val attachment1 = newAttachment("test1")
    val attachment2 = newAttachment("test2")
    hint.addAttachment(attachment1)
    hint.addAttachment(attachment2)

    assertEquals(listOf(attachment1, attachment2), hint.attachments)
  }

  @Test
  fun `after reset list is empty`() {
    val hint = Hint()
    val attachment1 = newAttachment("test1")
    val attachment2 = newAttachment("test2")
    hint.addAttachment(attachment1)
    hint.addAttachment(attachment2)

    hint.clearAttachments()

    assertEquals(emptyList(), hint.attachments)
  }

  @Test
  fun `after replace list contains only new item`() {
    val hint = Hint()
    val attachment1 = newAttachment("test1")
    val attachment2 = newAttachment("test2")
    val attachment3 = newAttachment("test2")
    val attachment4 = newAttachment("test2")
    hint.addAttachment(attachment1)
    hint.addAttachment(attachment2)

    hint.replaceAttachments(listOf(attachment3, attachment4))

    assertEquals(listOf(attachment3, attachment4), hint.attachments)
  }

  @Test
  fun `calling clear does not remove internal sentry attrs, attachments or screenshots`() {
    val userAttribute = "app_label"

    val hint = Hint()
    hint.set(SENTRY_TYPE_CHECK_HINT, CachedEvent())
    hint.set(userAttribute, "test label")
    hint.addAttachment(newAttachment("test attachment"))
    hint.screenshot = newAttachment("2")
    hint.viewHierarchy = newAttachment("3")
    hint.threadDump = newAttachment("4")

    hint.clear()

    assertNotNull(hint.get(SENTRY_TYPE_CHECK_HINT))
    assertNull(hint.get(userAttribute))
    assertEquals(1, hint.attachments.size)
    assertNotNull(hint.screenshot)
    assertNotNull(hint.viewHierarchy)
    assertNotNull(hint.threadDump)
  }

  @Test
  fun `can create hint with a screenshot`() {
    val hint = Hint()
    val attachment = newAttachment("test1")
    hint.screenshot = attachment

    assertNotNull(hint.screenshot)
  }

  @Test
  fun `can create hint with a view hierarchy`() {
    val hint = Hint()
    val attachment = newAttachment("test1")
    hint.viewHierarchy = attachment

    assertNotNull(hint.viewHierarchy)
  }

  @Test
  fun `can create hint with a thread dump`() {
    val hint = Hint()
    val attachment = newAttachment("thread-dump")
    hint.threadDump = attachment

    assertNotNull(hint.threadDump)
  }

  companion object {
    fun newAttachment(content: String) = Attachment(content.toByteArray(), "$content.txt")
  }
}
