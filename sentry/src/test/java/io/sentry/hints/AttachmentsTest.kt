package io.sentry.hints

import io.sentry.Attachment
import kotlin.test.Test
import kotlin.test.assertEquals

class AttachmentsTest {

    @Test
    fun `can add an attachment`() {
        val container = Attachments()
        val attachment = newAttachment("test1")
        container.add(attachment)

        assertEquals(listOf(attachment), container.all)
    }

    @Test
    fun `can add multiple attachments`() {
        val container = Attachments()
        val attachment1 = newAttachment("test1")
        val attachment2 = newAttachment("test2")
        container.add(attachment1)
        container.add(attachment2)

        assertEquals(listOf(attachment1, attachment2), container.all)
    }

    @Test
    fun `after reset list is empty`() {
        val container = Attachments()
        val attachment1 = newAttachment("test1")
        val attachment2 = newAttachment("test2")
        container.add(attachment1)
        container.add(attachment2)

        container.clear()

        assertEquals(emptyList(), container.all)
    }

    @Test
    fun `after replace list contains only new item`() {
        val container = Attachments()
        val attachment1 = newAttachment("test1")
        val attachment2 = newAttachment("test2")
        val attachment3 = newAttachment("test2")
        val attachment4 = newAttachment("test2")
        container.add(attachment1)
        container.add(attachment2)

        container.replaceAll(listOf(attachment3, attachment4))

        assertEquals(listOf(attachment3, attachment4), container.all)
    }

    companion object {
        fun newAttachment(content: String) = Attachment(content.toByteArray(), "$content.txt")
    }
}
