package io.sentry

import com.nhaarman.mockitokotlin2.mock
import io.sentry.exception.SentryEnvelopeException
import io.sentry.protocol.User
import io.sentry.test.injectForField
import org.junit.Assert.assertArrayEquals
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SentryEnvelopeItemTest {

    private class Fixture {
        val pathname = "hello.txt"
        val filename = pathname
        val bytes = "hello".toByteArray()
        val maxAttachmentSize: Long = (5 * 1024 * 1024).toLong()

        val bytesAllowed = ByteArray(maxAttachmentSize.toInt()) { 0 }
        val bytesTooBig = ByteArray((maxAttachmentSize + 1).toInt()) { 0 }
    }

    private val fixture = Fixture()

    @AfterTest
    fun afterTest() {
        val file = File(fixture.pathname)
        file.delete()
    }

    @Test
    fun `fromSession creates an envelope with a session item`() {
        val envelope = SentryEnvelope.from(mock(), createSession(), null)
        envelope.items.forEach {
            assertEquals("application/json", it.header.contentType)
            assertEquals(SentryItemType.Session, it.header.type)
            assertNull(it.header.fileName)
            assertNotNull(it.data)
        }
    }

    @Test
    fun `fromAttachment with bytes`() {
        val attachment = Attachment(fixture.bytesAllowed, fixture.filename)

        val item = SentryEnvelopeItem.fromAttachment(attachment, fixture.maxAttachmentSize)

        assertAttachment(attachment, fixture.bytesAllowed, item)
    }

    @Test
    fun `fromAttachment with attachmentType`() {
        val attachment = Attachment(fixture.pathname, fixture.filename, "", true, "event.minidump")

        val item = SentryEnvelopeItem.fromAttachment(attachment, fixture.maxAttachmentSize)

        assertEquals("event.minidump", item.header.attachmentType)
    }

    @Test
    fun `fromAttachment with file`() {
        val file = File(fixture.pathname)
        file.writeBytes(fixture.bytesAllowed)
        val attachment = Attachment(file.path)

        val item = SentryEnvelopeItem.fromAttachment(attachment, fixture.maxAttachmentSize)

        assertAttachment(attachment, fixture.bytesAllowed, item)
    }

    @Test
    fun `fromAttachment with 2MB file`() {
        val file = File(fixture.pathname)
        val twoMB = ByteArray(1024 * 1024 * 2) { 1 }
        file.writeBytes(twoMB)
        val attachment = Attachment(file.absolutePath)

        val item = SentryEnvelopeItem.fromAttachment(attachment, fixture.maxAttachmentSize)

        assertAttachment(attachment, twoMB, item)
    }

    @Test
    fun `fromAttachment with non existent file`() {
        val attachment = Attachment("I don't exist", "file.txt")

        val item = SentryEnvelopeItem.fromAttachment(attachment, fixture.maxAttachmentSize)

        assertFailsWith<SentryEnvelopeException>(
            "Reading the attachment ${attachment.pathname} failed, because the file located at " +
                "the path is not a file."
        ) {
            item.data
        }
    }

    @Test
    fun `fromAttachment with file permission denied`() {
        val file = File(fixture.pathname)
        file.writeBytes(fixture.bytes)

        // On CI it can happen that we don't have the permission to the file permission to read only
        val changedFileReadPermission = file.setReadable(false)
        if (changedFileReadPermission) {
            val attachment = Attachment(file.path, "file.txt")

            val item = SentryEnvelopeItem.fromAttachment(attachment, fixture.maxAttachmentSize)

            assertFailsWith<SentryEnvelopeException>(
                "Reading the attachment ${attachment.pathname} failed, " +
                    "because can't read the file."
            ) {
                item.data
            }
        } else {
            println("Was not able to change file access permission. Skipping test.")
        }
    }

    @Test
    fun `fromAttachment with file SecurityManager denies read access`() {
        val file = File(fixture.pathname)
        file.writeBytes(fixture.bytes)

        val attachment = Attachment(file.path, fixture.filename)

        val securityManager = DenyReadFileSecurityManager(fixture.pathname)
        System.setSecurityManager(securityManager)

        val item = SentryEnvelopeItem.fromAttachment(attachment, fixture.maxAttachmentSize)

        assertFailsWith<SentryEnvelopeException>("Reading the attachment ${attachment.pathname} failed.") {
            item.data
        }

        System.setSecurityManager(null)
    }

    @Test
    fun `fromAttachment with both bytes and pathname null`() {
        val attachment = Attachment("")
        // Annotations prevent creating attachments with both bytes and path null.
        // If someone ignores the annotations in Java and passes null for path
        // or bytes, we still want our code to work properly. Instead of creating
        // an extra test class in Java and ignoring the warnings we just use
        // reflection instead.
        attachment.injectForField("pathname", null)

        val item = SentryEnvelopeItem.fromAttachment(attachment, fixture.maxAttachmentSize)

        assertFailsWith<SentryEnvelopeException>(
            "Couldn't attach the attachment ${attachment.filename}.\n" +
                "Please check that either bytes or a path is set."
        ) {
            item.data
        }
    }

    @Test
    fun `fromAttachment with image`() {
        val image = this::class.java.classLoader.getResource("Tongariro.jpg")!!
        val attachment = Attachment(image.path)

        val item = SentryEnvelopeItem.fromAttachment(attachment, fixture.maxAttachmentSize)
        assertAttachment(attachment, image.readBytes(), item)
    }

    @Test
    fun `fromAttachment with bytes too big`() {
        val attachment = Attachment(fixture.bytesTooBig, fixture.filename)
        val exception = assertFailsWith<SentryEnvelopeException> {
            SentryEnvelopeItem.fromAttachment(attachment, fixture.maxAttachmentSize).data
        }

        assertEquals(
            "Dropping attachment with filename '${fixture.filename}', because the " +
                "size of the passed bytes with ${fixture.bytesTooBig.size} bytes is bigger " +
                "than the maximum allowed attachment size of " +
                "${fixture.maxAttachmentSize} bytes.",
            exception.message
        )
    }

    @Test
    fun `fromAttachment with file too big`() {
        val file = File(fixture.pathname)
        file.writeBytes(fixture.bytesTooBig)
        val attachment = Attachment(file.path)

        val exception = assertFailsWith<SentryEnvelopeException> {
            SentryEnvelopeItem.fromAttachment(attachment, fixture.maxAttachmentSize).data
        }

        assertEquals(
            "Dropping attachment, because the size of the it located at " +
                "'${fixture.pathname}' with ${file.length()} bytes is bigger than the maximum " +
                "allowed attachment size of ${fixture.maxAttachmentSize} bytes.",
            exception.message
        )
    }

    private fun createSession(): Session {
        return Session("dis", User(), "env", "rel")
    }

    private fun assertAttachment(
        attachment: Attachment,
        expectedBytes: ByteArray,
        actualItem: SentryEnvelopeItem
    ) {
        assertEquals(attachment.contentType, actualItem.header.contentType)
        assertEquals(attachment.filename, actualItem.header.fileName)
        assertArrayEquals(expectedBytes, actualItem.data)
    }
}
