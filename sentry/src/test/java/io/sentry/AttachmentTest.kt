package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AttachmentTest {

    private class Fixture {
        val defaultContentType = "application/octet-stream"
        val contentType = "application/json"
        val filename = "logs.txt"
        val bytes = "content".toByteArray()
        val path = "path/to/${filename}"
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
    fun `set content path`() {
        val attachment = Attachment(fixture.path)
        attachment.contentType = fixture.contentType
        assertEquals(fixture.contentType, attachment.contentType)
    }
}
