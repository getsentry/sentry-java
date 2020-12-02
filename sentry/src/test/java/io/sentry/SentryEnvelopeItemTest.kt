package io.sentry

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.sentry.protocol.User
import java.io.File
import java.io.FileNotFoundException
import java.io.FilePermission
import java.security.Permission
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentryEnvelopeItemTest {

    private class Fixture {
        val filename = "hello.txt"
        val bytes = "hello".toByteArray()
    }

    private val fixture = Fixture()

    @AfterTest
    fun afterTest() {
        val file = File(fixture.filename)
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
        val bytes = "hello".toByteArray()
        val attachment = Attachment(bytes, fixture.filename)

        val item = SentryEnvelopeItem.fromAttachment(mock(), attachment)

        assertAttachment(attachment, bytes, item)
    }

    @Test
    fun `fromAttachment with file`() {
        val file = File(fixture.filename)
        file.writeBytes(fixture.bytes)
        val attachment = Attachment(file.absolutePath)

        val item = SentryEnvelopeItem.fromAttachment(mock(), attachment)

        assertAttachment(attachment, fixture.bytes, item)
    }

    @Test
    fun `fromAttachment with 2MB file`() {
        val file = File(fixture.filename)
        val twoMB = ByteArray(1024 * 1024 * 2) { 1 }
        file.writeBytes(twoMB)
        val attachment = Attachment(file.absolutePath)

        val item = SentryEnvelopeItem.fromAttachment(mock(), attachment)

        assertAttachment(attachment, twoMB, item)
    }

    @Test
    fun `fromAttachment with non existent file`() {
        val logger = mock<ILogger>()
        val attachment = Attachment("I don't exist", "file.txt")

        val item = SentryEnvelopeItem.fromAttachment(logger, attachment)

        assertAttachment(attachment, byteArrayOf(), item)
        verifyLogException<FileNotFoundException>(logger, attachment.filename)
    }

    @Test
    fun `fromAttachment with file permission denied`() {
        val file = File(fixture.filename)
        file.writeBytes(fixture.bytes)

        // On CI it can happen that we don't have the permission to the file permission to read only
        val changedFileReadPermission = file.setReadable(false)
        if (changedFileReadPermission) {
            val logger = mock<ILogger>()
            val attachment = Attachment(file.absolutePath, "file.txt")

            val item = SentryEnvelopeItem.fromAttachment(logger, attachment)

            assertAttachment(attachment, byteArrayOf(), item)
            verifyLogException<FileNotFoundException>(logger, attachment.filename)
        } else {
            println("Was not able to change file access permission. Skipping test.")
        }
    }

    @Test
    fun `fromAttachment with file SecurityManager denies read access`() {
        val file = File(fixture.filename)
        file.writeBytes(fixture.bytes)

        val logger = mock<ILogger>()
        val attachment = Attachment(file.absolutePath, "file.txt")

        val securityManager = DenyReadFileSecurityManager(fixture.filename)
        System.setSecurityManager(securityManager)

        val item = SentryEnvelopeItem.fromAttachment(logger, attachment)

        assertAttachment(attachment, byteArrayOf(), item)
        verifyLogException<SecurityException>(logger, attachment.filename)

        System.setSecurityManager(null)
    }

    @Test
    fun `fromAttachment with both bytes and path null`() {
        val attachment = Attachment("")
        // Annotations prevent creating attachments with both bytes and path null.
        // If someone ignores the annotations in Java and passes null for path
        // or bytes, we still want our code to work properly. Instead of creating
        // an extra test class in Java and ignoring the warnings we just use
        // reflection instead.
        attachment.injectForField("path", null)

        val item = SentryEnvelopeItem.fromAttachment(mock(), attachment)

        assertAttachment(attachment, byteArrayOf(), item)
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
        assertTrue(
            expectedBytes.contentEquals(actualItem.data),
            "${String(expectedBytes)} is not equal to ${String(actualItem.data)}"
        )
    }

    private inline fun <reified T : Exception> verifyLogException(logger: ILogger, filename: String) {
        verify(logger)
            .log(eq(SentryLevel.ERROR), any<T>(),
                eq("Serializing attachment %s failed."), eq(filename))
    }

    private inline fun <reified T : Any> T.injectForField(name: String, value: Any?) {
        T::class.java.getDeclaredField(name)
            .apply { isAccessible = true }
            .set(this, value)
    }
}

private class DenyReadFileSecurityManager(private val filename: String) : SecurityManager() {
    override fun checkPermission(permission: Permission?) {
        if (permission is FilePermission &&
            permission.name.contains(filename) &&
            permission.actions.contains("read")
        ) {
            super.checkPermission(permission)
        }
    }
}
