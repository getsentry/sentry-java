package io.sentry.core

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import java.io.InputStream
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SentryEnvelopeTest {
    private val UTF_8 = Charset.forName("UTF-8")

    @Test
    fun `deserialize sample envelope with event and two attachments`() {
        val envelopeReader = EnvelopeReader()
        val testFile = this::class.java.classLoader.getResource("envelope-event-attachment.txt")
        val stream = testFile!!.openStream()
        val envelope = envelopeReader.read(stream)
        assertNotNull(envelope)
        assertEquals("9ec79c33ec9942ab8353589fcb2e04dc", envelope.header.eventId.toString())
        assertEquals(3, envelope.items.count())
        val firstItem = envelope.items.elementAt(0)
        assertEquals("event", firstItem.header.type)
        assertEquals("application/json", firstItem.header.contentType)
        assertEquals(107, firstItem.header.length)
        assertEquals(107, firstItem.data.size)
        assertNull(firstItem.header.fileName)
        val secondItem = envelope.items.elementAt(1)
        assertEquals("attachment", secondItem.header.type)
        assertEquals("text/plain", secondItem.header.contentType)
        assertEquals(61, secondItem.header.length)
        assertEquals(61, secondItem.data.size)
        assertEquals("attachment.txt", secondItem.header.fileName)
        val thirdItem = envelope.items.elementAt(2)
        assertEquals("attachment", thirdItem.header.type)
        assertEquals("text/plain", thirdItem.header.contentType)
        assertEquals(29, thirdItem.header.length)
        assertEquals(29, thirdItem.data.size)
        assertEquals("log.txt", thirdItem.header.fileName)
    }

    @Test
    fun `when envelope is empty, reader throws illegal argument`() {
        val envelopeReader = EnvelopeReader()
        var stream = mock<InputStream>()
        whenever(stream.read(any())).thenReturn(-1)
        val exception = assertFailsWith<IllegalArgumentException> { envelopeReader.read(stream) }
        assertEquals("Empty stream.", exception.message)
    }

    @Test
    fun `when envelope has no line break, reader throws illegal argument`() {
        val envelopeReader = EnvelopeReader()
        val stream = "{}".toInputStream()
        val exception = assertFailsWith<IllegalArgumentException> { envelopeReader.read(stream) }
        assertEquals("Envelope contains no header.", exception.message)
    }

    @Test
    fun `when envelope header has no event_id, reader throws illegal argument`() {
        val envelopeReader = EnvelopeReader()
        val stream = "{}\n{\"item_header\":\"value\",\"length\":\"2\"}\n{}".toInputStream()
        val exception = assertFailsWith<IllegalArgumentException> { envelopeReader.read(stream) }
        assertEquals("Envelope header is missing required 'event_id'.", exception.message)
    }

    @Test
    fun `when envelope item length is bigger than the rest of the payload, reader throws illegal argument`() {
        val envelopeReader = EnvelopeReader()
        val stream = "{\"event_id\":\"9ec79c33ec9942ab8353589fcb2e04dc\"}\n{\"length\":\"3\"}\n{}".toInputStream()
        val exception = assertFailsWith<IllegalArgumentException> { envelopeReader.read(stream) }
        assertEquals("Invalid length for item at index '0'. Item is '66' bytes. There are '65' in the buffer.", exception.message)
    }

    @Test
    fun `when envelope has only a header without line break, reader throws illegal argument`() {
        val envelopeReader = EnvelopeReader()
        val stream = "{\"event_id\":\"9ec79c33ec9942ab8353589fcb2e04dc\"}".toInputStream()
        val exception = assertFailsWith<IllegalArgumentException> { envelopeReader.read(stream) }
        assertEquals("Envelope contains no header.", exception.message)
    }

    @Test
    fun `when envelope has only a header and line break, reader throws illegal argument`() {
        val envelopeReader = EnvelopeReader()
        val stream = "{\"event_id\":\"9ec79c33ec9942ab8353589fcb2e04dc\"}\n".toInputStream()
        val exception = assertFailsWith<IllegalArgumentException> { envelopeReader.read(stream) }
        assertEquals("Invalid envelope. Item at index '0'. has no header delimiter.", exception.message)
    }

    @Test
    fun `when envelope has the first item missing length, reader throws illegal argument`() {
        val envelopeReader = EnvelopeReader()
        val stream = """{"event_id":"9ec79c33ec9942ab8353589fcb2e04dc"}
{"content_type":"application/json","type":"event"}
{}""".toInputStream()
        val exception = assertFailsWith<IllegalArgumentException> { envelopeReader.read(stream) }
        assertEquals("Item header at index '0' has an invalid value: '0'.", exception.message)
    }

    @Test
    fun `when envelope two items, returns envelope with items`() {
        val envelopeReader = EnvelopeReader()
        val stream = """{"event_id":"9ec79c33ec9942ab8353589fcb2e04dc"}
{"type":"event","length":"2"}
{}
{"content_type":"application/octet-stream","type":"attachment","length":"10","filename":"null.bin"}
abcdefghij""".toInputStream()
        val envelope = envelopeReader.read(stream)

        assertNotNull(envelope)
        assertEquals("9ec79c33ec9942ab8353589fcb2e04dc", envelope.header.eventId.toString())
        assertEquals(2, envelope.items.count())
        val firstItem = envelope.items.first()
        assertEquals("event", firstItem.header.type)
        assertNull(firstItem.header.contentType)
        assertEquals(2, firstItem.header.length)
        assertEquals(2, firstItem.data.size)
        assertNull(firstItem.header.fileName)
        val secondItem = envelope.items.last()
        assertEquals("attachment", secondItem.header.type)
        assertEquals("application/octet-stream", secondItem.header.contentType)
        assertEquals("null.bin", secondItem.header.fileName)
        assertEquals(10, secondItem.header.length)
        assertEquals(10, secondItem.data.size)
    }
}
