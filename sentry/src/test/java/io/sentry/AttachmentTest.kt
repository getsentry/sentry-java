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
        val pathname = "path/to/$filename"
    }

    private val fixture = Fixture()

    @Test
    fun `init with bytes sets default content type`() {
        val attachment = Attachment(fixture.bytes, fixture.filename)

        assertEquals(fixture.bytes, attachment.bytes)
        assertNull(attachment.pathname)
        assertEquals(fixture.filename, attachment.filename)
        assertEquals(fixture.defaultContentType, attachment.contentType)
    }

    @Test
    fun `init with file with pathname`() {
        val attachment = Attachment(fixture.pathname)

        assertEquals(fixture.pathname, attachment.pathname)
        assertNull(attachment.bytes)
        assertEquals(fixture.filename, attachment.filename)
        assertEquals(fixture.defaultContentType, attachment.contentType)
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
        assertEquals(fixture.defaultContentType, attachment.contentType)
    }

    @Test
    fun `set content type`() {
        val attachment = Attachment(fixture.pathname)
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
        assertNull(clone.pathname)
    }

    @Test
    fun `clone with pathname`() {
        val attachment = Attachment(fixture.pathname, fixture.filename)

        val clone = attachment.clone() as Attachment
        attachment.contentType = "application/json"

        assertEquals(fixture.defaultContentType, clone.contentType)
        assertEquals(fixture.pathname, clone.pathname)
        assertEquals(fixture.filename, clone.filename)
        assertNull(clone.bytes)
    }
}
