package io.sentry

import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SentryEnvelopeTest {
  class Fixture {
    fun getEnvelopeReader() = EnvelopeReader(JsonSerializer(SentryOptions()))
  }

  val fixture = Fixture()

  @Test
  fun `deserialize sample envelope with event and two attachments`() {
    val envelope = readEnvelope("envelope-event-attachment.txt")

    assertNotNull(envelope)
    assertEquals("9ec79c33ec9942ab8353589fcb2e04dc", envelope.header.eventId.toString())
    assertEquals(3, envelope.items.count())
    val firstItem = envelope.items.elementAt(0)
    assertEquals(SentryItemType.Event, firstItem.header.type)
    assertEquals("application/json", firstItem.header.contentType)
    assertEquals(107, firstItem.header.length)
    assertEquals(107, firstItem.data.size)
    assertNull(firstItem.header.fileName)
    val secondItem = envelope.items.elementAt(1)
    assertEquals(SentryItemType.Attachment, secondItem.header.type)
    assertEquals("text/plain", secondItem.header.contentType)
    assertEquals(61, secondItem.header.length)
    assertEquals(61, secondItem.data.size)
    assertEquals("attachment.txt", secondItem.header.fileName)
    assertEquals("event.minidump", secondItem.header.attachmentType)
    val thirdItem = envelope.items.elementAt(2)
    assertEquals(SentryItemType.Attachment, thirdItem.header.type)
    assertEquals("text/plain", thirdItem.header.contentType)
    assertEquals(29, thirdItem.header.length)
    assertEquals(29, thirdItem.data.size)
    assertEquals("log.txt", thirdItem.header.fileName)
  }

  @Test
  fun `when envelope is empty, reader throws illegal argument`() {
    val envelopeReader = fixture.getEnvelopeReader()
    val stream = mock<InputStream>()
    whenever(stream.read(any())).thenReturn(-1)
    val exception = assertFailsWith<IllegalArgumentException> { envelopeReader.read(stream) }
    assertEquals("Empty stream.", exception.message)
  }

  @Test
  fun `when envelope has no line break, reader throws illegal argument`() {
    val envelopeReader = fixture.getEnvelopeReader()
    val stream = "{}".toInputStream()
    val exception = assertFailsWith<IllegalArgumentException> { envelopeReader.read(stream) }
    assertEquals("Envelope contains no header.", exception.message)
  }

  @Test
  fun `when envelope terminates with line break, envelope parsed correctly`() {
    val envelopeReader = fixture.getEnvelopeReader()
    val stream =
      "{\"event_id\":\"9ec79c33ec9942ab8353589fcb2e04dc\"}\n{\"length\":15,\"type\":\"event\"}\n{\"contexts\":{}}\n"
        .toInputStream()

    val envelope = envelopeReader.read(stream)

    assertNotNull(envelope)
    assertEquals("9ec79c33ec9942ab8353589fcb2e04dc", envelope.header.eventId.toString())
    assertEquals(1, envelope.items.count())
    val firstItem = envelope.items.first()
    assertEquals(SentryItemType.Event, firstItem.header.type)
    assertNull(firstItem.header.contentType)
    assertEquals(15, firstItem.header.length)
    assertEquals(15, firstItem.data.size)
    assertNull(firstItem.header.fileName)
  }

  @Test
  fun `when envelope item length is bigger than the rest of the payload, reader throws illegal argument`() {
    val envelopeReader = fixture.getEnvelopeReader()
    val stream =
      "{\"event_id\":\"9ec79c33ec9942ab8353589fcb2e04dc\"}\n{\"type\":\"envelope\",\"length\":\"3\"}\n{}"
        .toInputStream()
    val exception = assertFailsWith<IllegalArgumentException> { envelopeReader.read(stream) }
    assertEquals(
      "Invalid length for item at index '0'. Item is '84' bytes. There are '83' in the buffer.",
      exception.message,
    )
  }

  @Test
  fun `when envelope has only a header without line break, reader throws illegal argument`() {
    val envelopeReader = fixture.getEnvelopeReader()
    val stream = "{\"event_id\":\"9ec79c33ec9942ab8353589fcb2e04dc\"}".toInputStream()
    val exception = assertFailsWith<IllegalArgumentException> { envelopeReader.read(stream) }
    assertEquals("Envelope contains no header.", exception.message)
  }

  @Test
  fun `when envelope has only a header and line break, reader throws illegal argument`() {
    val envelopeReader = fixture.getEnvelopeReader()
    val stream = "{\"event_id\":\"9ec79c33ec9942ab8353589fcb2e04dc\"}\n".toInputStream()
    val exception = assertFailsWith<IllegalArgumentException> { envelopeReader.read(stream) }
    assertEquals("Invalid envelope. Item at index '0'. has no header delimiter.", exception.message)
  }

  @Test
  fun `when envelope has the first item missing length, reader throws illegal argument`() {
    val envelopeReader = fixture.getEnvelopeReader()
    val stream =
      """{"event_id":"9ec79c33ec9942ab8353589fcb2e04dc"}
{"content_type":"application/json","type":"event"}
{}"""
        .toInputStream()
    val exception = assertFailsWith<IllegalArgumentException> { envelopeReader.read(stream) }
    assertEquals("Item header at index '0' is null or empty.", exception.message)
  }

  @Test
  fun `when envelope two items, returns envelope with items`() {
    val envelopeReader = fixture.getEnvelopeReader()
    val stream =
      """{"event_id":"9ec79c33ec9942ab8353589fcb2e04dc"}
{"type":"event","length":"2"}
{}
{"content_type":"application/octet-stream","type":"attachment","length":"10","filename":"null.bin"}
abcdefghij"""
        .toInputStream()
    val envelope = envelopeReader.read(stream)

    assertNotNull(envelope)
    assertEquals("9ec79c33ec9942ab8353589fcb2e04dc", envelope.header.eventId.toString())
    assertEquals(2, envelope.items.count())
    val firstItem = envelope.items.first()
    assertEquals(SentryItemType.Event, firstItem.header.type)
    assertNull(firstItem.header.contentType)
    assertEquals(2, firstItem.header.length)
    assertEquals(2, firstItem.data.size)
    assertNull(firstItem.header.fileName)
    val secondItem = envelope.items.last()
    assertEquals(SentryItemType.Attachment, secondItem.header.type)
    assertEquals("application/octet-stream", secondItem.header.contentType)
    assertEquals("null.bin", secondItem.header.fileName)
    assertEquals(10, secondItem.header.length)
    assertEquals(10, secondItem.data.size)
  }

  @Test
  fun `deserializes an user feedback`() {
    val envelope = readEnvelope("envelope-feedback.txt")

    assertNotNull(envelope)
    assertEquals("bdd63725a2b84c1eabd761106e17d390", envelope.header.eventId.toString())
    assertEquals(1, envelope.items.count())
    val firstItem = envelope.items.elementAt(0)
    assertEquals(SentryItemType.UserFeedback, firstItem.header.type)
    assertEquals("application/json", firstItem.header.contentType)
    assertEquals(103, firstItem.header.length)
    assertEquals(103, firstItem.data.size)
  }

  private fun readEnvelope(fileName: String): SentryEnvelope? {
    val envelopeReader = fixture.getEnvelopeReader()
    val testFile = this::class.java.classLoader.getResource(fileName)
    val stream = testFile!!.openStream()
    return envelopeReader.read(stream)
  }
}
