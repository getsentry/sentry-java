package io.sentry

import io.sentry.protocol.ViewHierarchy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttachmentTest {
  private class Fixture {
    val contentType = "application/json"
    val filename = "logs.txt"
    val bytes = "content".toByteArray()
    val pathname = "path/to/$filename"
  }

  private val fixture = Fixture()

  @Test
  fun `init with bytes sets default content type`() {
    val attachment = Attachment(fixture.bytes, fixture.filename)

    assertEquals(fixture.bytes, attachment.bytes)
    assertNull(attachment.pathname)
    assertEquals(fixture.filename, attachment.filename)
  }

  @Test
  fun `init with file with pathname`() {
    val attachment = Attachment(fixture.pathname)

    assertEquals(fixture.pathname, attachment.pathname)
    assertNull(attachment.bytes)
    assertEquals(fixture.filename, attachment.filename)
  }

  @Test
  fun `init with file with empty pathname`() {
    val attachment = Attachment("")

    assertEquals("", attachment.pathname)
    assertEquals("", attachment.filename)
  }

  @Test
  fun `init with file with filename as pathname`() {
    val attachment = Attachment(fixture.filename)

    assertEquals(fixture.filename, attachment.pathname)
    assertEquals(fixture.filename, attachment.filename)
  }

  @Test
  fun `init with file with pathname and filename`() {
    val otherFileName = "input.json"
    val attachment = Attachment(fixture.pathname, otherFileName)

    assertEquals(fixture.pathname, attachment.pathname)
    assertNull(attachment.bytes)
    assertEquals(otherFileName, attachment.filename)
  }

  @Test
  fun `set content type`() {
    val fileAttachment = Attachment(fixture.pathname, fixture.filename, fixture.contentType)
    assertEquals(fixture.contentType, fileAttachment.contentType)

    val byteAttachment = Attachment(fixture.bytes, fixture.filename, fixture.contentType)
    assertEquals(fixture.contentType, byteAttachment.contentType)
  }

  @Test
  fun `default of addToTransactions is false`() {
    val fileAttachment = Attachment(fixture.filename)
    assertFalse(fileAttachment.isAddToTransactions)

    val byteAttachment = Attachment(fixture.bytes, fixture.filename)
    assertFalse(byteAttachment.isAddToTransactions)
  }

  @Test
  fun `set addToTransactions`() {
    val fileAttachment = Attachment(fixture.pathname, fixture.filename, fixture.contentType, true)
    assertTrue(fileAttachment.isAddToTransactions)

    val byteAttachment = Attachment(fixture.bytes, fixture.filename, fixture.contentType, true)
    assertTrue(byteAttachment.isAddToTransactions)
  }

  @Test
  fun `set attachmentType`() {
    val fileAttachment =
      Attachment(fixture.pathname, fixture.filename, fixture.contentType, true, "type")

    assertEquals("type", fileAttachment.attachmentType)
  }

  @Test
  fun `init with default attachmentType`() {
    val fileAttachment = Attachment(fixture.pathname)

    assertEquals("event.attachment", fileAttachment.attachmentType)
  }

  @Test
  fun `creates attachment from screenshot`() {
    val bytes = byteArrayOf()
    val attachment = Attachment.fromScreenshot(bytes)

    assertEquals("screenshot.png", attachment.filename)
    assertEquals("image/png", attachment.contentType)
    assertEquals(false, attachment.isAddToTransactions)
    assertEquals(bytes, attachment.bytes)
  }

  @Test
  fun `creates attachment from view hierarchy`() {
    val hierarchy = ViewHierarchy("android", emptyList())
    val attachment = Attachment.fromViewHierarchy(hierarchy)

    assertEquals("view-hierarchy.json", attachment.filename)
    assertEquals("application/json", attachment.contentType)
    assertEquals(false, attachment.isAddToTransactions)
    assertEquals("event.view_hierarchy", attachment.attachmentType)
    assertNull(attachment.bytes)
    assertEquals(hierarchy, attachment.serializable)
  }
}
