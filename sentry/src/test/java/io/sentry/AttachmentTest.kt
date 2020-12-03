package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Assert.assertArrayEquals

class AttachmentTest {

    private class Fixture {
        val defaultContentType = "application/octet-stream"
        val contentType = "application/json"
        val filename = "logs.txt"
        val bytes = "content".toByteArray()
        val path = "path/to/$filename"
    }

    private val fixture = Fixture()

    @Test
    fun `init with bytes sets default content type`() {
        val attachment = Attachment(fixture.bytes, fixture.filename)

        assertEquals(fixture.bytes, attachment.bytes)
        assertNull(attachment.path)
        assertEquals(fixture.filename, attachment.filename)
        assertEquals(fixture.defaultContentType, attachment.contentType)
    }

    @Test
    fun `init with file with path`() {
        val attachment = Attachment(fixture.path)

        assertEquals(fixture.path, attachment.path)
        assertNull(attachment.bytes)
        assertEquals(fixture.filename, attachment.filename)
        assertEquals(fixture.defaultContentType, attachment.contentType)
    }

    @Test
    fun `init with file with empty path`() {
        val attachment = Attachment("")

        assertEquals("", attachment.path)
        assertEquals("", attachment.filename)
    }

    @Test
    fun `init with file with filename as path `() {
        val attachment = Attachment(fixture.filename)

        assertEquals(fixture.filename, attachment.path)
        assertEquals(fixture.filename, attachment.filename)
    }

    @Test
    fun `init with file with path and filename`() {
        val otherFileName = "input.json"
        val attachment = Attachment(fixture.path, otherFileName)

        assertEquals(fixture.path, attachment.path)
        assertNull(attachment.bytes)
        assertEquals(otherFileName, attachment.filename)
        assertEquals(fixture.defaultContentType, attachment.contentType)
    }

    @Test
    fun `set content type`() {
        val attachment = Attachment(fixture.path)
        attachment.contentType = fixture.contentType
        assertEquals(fixture.contentType, attachment.contentType)
    }

    @Test
    fun `clone with bytes`() {
        val bytes = fixture.bytes.copyOf()
        val attachment = Attachment(bytes, fixture.filename)

        val clone = attachment.clone() as Attachment
        attachment.contentType = "application/json"
        bytes[0] = 'a'.toByte()

        // make sure the byte array of the attachment is actually changed
        assertArrayEquals(bytes, attachment.bytes!!)
        assertArrayEquals(fixture.bytes, clone.bytes!!)

        assertEquals(fixture.defaultContentType, clone.contentType)
        assertEquals(fixture.filename, clone.filename)
        assertNull(clone.path)
    }

    @Test
    fun `clone with path`() {
        val attachment = Attachment(fixture.path, fixture.filename)

        val clone = attachment.clone() as Attachment
        attachment.contentType = "application/json"

        assertEquals(fixture.defaultContentType, clone.contentType)
        assertEquals(fixture.path, clone.path)
        assertEquals(fixture.filename, clone.filename)
        assertNull(clone.bytes)
    }
}
