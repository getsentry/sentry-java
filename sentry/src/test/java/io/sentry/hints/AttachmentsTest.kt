package io.sentry.hints

import io.sentry.Attachment
import kotlin.test.Test
import kotlin.test.assertEquals

class AttachmentsTest {

    @Test
    fun `can add an attachment`() {
        val hints = Hints()
        val attachment = newAttachment("test1")
        hints.addAttachment(attachment)

        assertEquals(listOf(attachment), hints.attachments)
    }

    @Test
    fun `can add multiple attachments`() {
        val hints = Hints()
        val attachment1 = newAttachment("test1")
        val attachment2 = newAttachment("test2")
        hints.addAttachment(attachment1)
        hints.addAttachment(attachment2)

        assertEquals(listOf(attachment1, attachment2), hints.attachments)
    }

    @Test
    fun `after reset list is empty`() {
        val hints = Hints()
        val attachment1 = newAttachment("test1")
        val attachment2 = newAttachment("test2")
        hints.addAttachment(attachment1)
        hints.addAttachment(attachment2)

        hints.clear()

        assertEquals(emptyList(), hints.attachments)
    }

    @Test
    fun `after replace list contains only new item`() {
        val hints = Hints()
        val attachment1 = newAttachment("test1")
        val attachment2 = newAttachment("test2")
        val attachment3 = newAttachment("test2")
        val attachment4 = newAttachment("test2")
        hints.addAttachment(attachment1)
        hints.addAttachment(attachment2)

        hints.replaceAttachments(listOf(attachment3, attachment4))

        assertEquals(listOf(attachment3, attachment4), hints.attachments)
    }

    companion object {
        fun newAttachment(content: String) = Attachment(content.toByteArray(), "$content.txt")
    }
}
