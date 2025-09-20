package io.sentry

import io.sentry.exception.SentryEnvelopeException
import io.sentry.protocol.ReplayRecordingSerializationTest
import io.sentry.protocol.SentryReplayEventSerializationTest
import io.sentry.protocol.User
import io.sentry.protocol.ViewHierarchy
import io.sentry.test.injectForField
import io.sentry.vendor.Base64
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.concurrent.Callable
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Assert.assertArrayEquals
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.msgpack.core.MessagePack

class SentryEnvelopeItemTest {
  @get:Rule val tmpDir = TemporaryFolder()

  private class Fixture {
    val options = SentryOptions()
    val serializer = JsonSerializer(options)
    val errorSerializer: JsonSerializer = mock {
      on(it.serialize(any<JsonSerializable>(), any())).then { throw Exception("Mocked exception.") }
    }
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

    val item =
      SentryEnvelopeItem.fromAttachment(
        fixture.serializer,
        fixture.options.logger,
        attachment,
        fixture.maxAttachmentSize,
      )

    assertAttachment(attachment, fixture.bytesAllowed, item)
  }

  @Test
  fun `fromAttachment with Serializable`() {
    val viewHierarchy = ViewHierarchy("android", emptyList())
    val viewHierarchySerialized = serialize(viewHierarchy)

    val attachment = Attachment(viewHierarchy, fixture.filename, "text/plain", null, false)

    val item =
      SentryEnvelopeItem.fromAttachment(
        fixture.serializer,
        fixture.options.logger,
        attachment,
        fixture.maxAttachmentSize,
      )

    assertAttachment(attachment, viewHierarchySerialized, item)
  }

  @Test
  fun `fromAttachment with byteProvider`() {
    val attachment =
      Attachment(
        object : Callable<ByteArray> {
          override fun call(): ByteArray? = byteArrayOf(0x1)
        },
        fixture.filename,
        "text/plain",
        "image/png",
        false,
      )

    val item =
      SentryEnvelopeItem.fromAttachment(
        fixture.serializer,
        fixture.options.logger,
        attachment,
        fixture.maxAttachmentSize,
      )

    assertAttachment(attachment, byteArrayOf(0x1), item)
  }

  @Test
  fun `fromAttachment with attachmentType`() {
    val attachment = Attachment(fixture.pathname, fixture.filename, "", true, "event.minidump")

    val item =
      SentryEnvelopeItem.fromAttachment(
        fixture.serializer,
        fixture.options.logger,
        attachment,
        fixture.maxAttachmentSize,
      )

    assertEquals("event.minidump", item.header.attachmentType)
  }

  @Test
  fun `fromAttachment with file`() {
    val file = File(fixture.pathname)
    file.writeBytes(fixture.bytesAllowed)
    val attachment = Attachment(file.path)

    val item =
      SentryEnvelopeItem.fromAttachment(
        fixture.serializer,
        fixture.options.logger,
        attachment,
        fixture.maxAttachmentSize,
      )

    assertAttachment(attachment, fixture.bytesAllowed, item)
  }

  @Test
  fun `fromAttachment with 2MB file`() {
    val file = File(fixture.pathname)
    val twoMB = ByteArray(1024 * 1024 * 2) { 1 }
    file.writeBytes(twoMB)
    val attachment = Attachment(file.absolutePath)

    val item =
      SentryEnvelopeItem.fromAttachment(
        fixture.serializer,
        fixture.options.logger,
        attachment,
        fixture.maxAttachmentSize,
      )

    assertAttachment(attachment, twoMB, item)
  }

  @Test
  fun `fromAttachment with non existent file`() {
    val attachment = Attachment("I don't exist", "file.txt")

    val item =
      SentryEnvelopeItem.fromAttachment(
        fixture.serializer,
        fixture.options.logger,
        attachment,
        fixture.maxAttachmentSize,
      )

    assertFailsWith<IOException>(
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

      val item =
        SentryEnvelopeItem.fromAttachment(
          fixture.serializer,
          fixture.options.logger,
          attachment,
          fixture.maxAttachmentSize,
        )

      assertFailsWith<IOException>(
        "Reading the attachment ${attachment.pathname} failed, " + "because can't read the file."
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

    val item =
      SentryEnvelopeItem.fromAttachment(
        fixture.serializer,
        fixture.options.logger,
        attachment,
        fixture.maxAttachmentSize,
      )

    assertFailsWith<SecurityException>("Reading the attachment ${attachment.pathname} failed.") {
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

    val item =
      SentryEnvelopeItem.fromAttachment(
        fixture.serializer,
        fixture.options.logger,
        attachment,
        fixture.maxAttachmentSize,
      )

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

    val item =
      SentryEnvelopeItem.fromAttachment(
        fixture.serializer,
        fixture.options.logger,
        attachment,
        fixture.maxAttachmentSize,
      )
    assertAttachment(attachment, image.readBytes(), item)
  }

  @Test
  fun `fromAttachment with bytes too big`() {
    val attachment = Attachment(fixture.bytesTooBig, fixture.filename)
    val exception =
      assertFailsWith<SentryEnvelopeException> {
        SentryEnvelopeItem.fromAttachment(
            fixture.serializer,
            fixture.options.logger,
            attachment,
            fixture.maxAttachmentSize,
          )
          .data
      }

    assertEquals(
      "Dropping attachment with filename '${fixture.filename}', because the " +
        "size of the passed bytes with ${fixture.bytesTooBig.size} bytes is bigger " +
        "than the maximum allowed attachment size of " +
        "${fixture.maxAttachmentSize} bytes.",
      exception.message,
    )
  }

  @Test
  fun `fromAttachment with serializable too big`() {
    val serializable = JsonSerializable { writer, _ ->
      writer.beginObject()
      writer.name("payload").value(String(fixture.bytesTooBig))
      writer.endObject()
    }
    val serializedBytes = serialize(serializable)

    val attachment = Attachment(serializable, fixture.filename, "text/plain", null, false)
    val exception =
      assertFailsWith<SentryEnvelopeException> {
        SentryEnvelopeItem.fromAttachment(
            fixture.serializer,
            fixture.options.logger,
            attachment,
            fixture.maxAttachmentSize,
          )
          .data
      }

    assertEquals(
      "Dropping attachment with filename '${fixture.filename}', because the " +
        "size of the passed bytes with ${serializedBytes.size} bytes is bigger " +
        "than the maximum allowed attachment size of " +
        "${fixture.maxAttachmentSize} bytes.",
      exception.message,
    )
  }

  @Test
  fun `fromAttachment with file too big`() {
    val file = File(fixture.pathname)
    file.writeBytes(fixture.bytesTooBig)
    val attachment = Attachment(file.path)

    val exception =
      assertFailsWith<IOException> {
        SentryEnvelopeItem.fromAttachment(
            fixture.serializer,
            fixture.options.logger,
            attachment,
            fixture.maxAttachmentSize,
          )
          .data
      }

    assertEquals(
      "Reading file failed, because size located at " +
        "'${fixture.pathname}' with ${file.length()} bytes is bigger than the maximum " +
        "allowed size of ${fixture.maxAttachmentSize} bytes.",
      exception.message,
    )
  }

  @Test
  fun `fromAttachment with bytesFrom serializable are null`() {
    val attachment = Attachment(mock<JsonSerializable>(), "mock-file-name", null, null, false)

    val item =
      SentryEnvelopeItem.fromAttachment(
        fixture.errorSerializer,
        fixture.options.logger,
        attachment,
        fixture.maxAttachmentSize,
      )

    assertFailsWith<SentryEnvelopeException>(
      "Couldn't attach the attachment ${attachment.filename}.\n" +
        "Please check that either bytes or a path is set."
    ) {
      item.data
    }
  }

  @Test
  fun `fromProfilingTrace saves file as Base64`() {
    val file = File(fixture.pathname)
    val profilingTraceData = mock<ProfilingTraceData> { whenever(it.traceFile).thenReturn(file) }

    file.writeBytes(fixture.bytes)
    SentryEnvelopeItem.fromProfilingTrace(
        profilingTraceData,
        fixture.maxAttachmentSize,
        fixture.serializer,
      )
      .data
    verify(profilingTraceData).sampledProfile =
      Base64.encodeToString(fixture.bytes, Base64.NO_WRAP or Base64.NO_PADDING)
  }

  @Test
  fun `fromProfilingTrace deletes file only after reading data`() {
    val file = File(fixture.pathname)
    val profilingTraceData = mock<ProfilingTraceData> { whenever(it.traceFile).thenReturn(file) }

    file.writeBytes(fixture.bytes)
    assert(file.exists())
    val traceData =
      SentryEnvelopeItem.fromProfilingTrace(profilingTraceData, fixture.maxAttachmentSize, mock())
    assert(file.exists())
    traceData.data
    assertFalse(file.exists())
  }

  @Test
  fun `fromProfilingTrace with invalid file throws`() {
    val file = File(fixture.pathname)
    val profilingTraceData = mock<ProfilingTraceData> { whenever(it.traceFile).thenReturn(file) }

    assertFailsWith<SentryEnvelopeException>(
      "Dropping profiling trace data, because the file ${file.path} doesn't exists"
    ) {
      SentryEnvelopeItem.fromProfilingTrace(profilingTraceData, fixture.maxAttachmentSize, mock())
        .data
    }
  }

  @Test
  fun `fromProfilingTrace with unreadable file throws`() {
    val file = File(fixture.pathname)
    val profilingTraceData =
      mock<ProfilingTraceData> {
        whenever(it.traceFile).thenReturn(file)
        whenever(it.platform).thenReturn("android")
      }
    file.writeBytes(fixture.bytes)
    file.setReadable(false)
    assertFailsWith<IOException>(
      "Dropping profiling trace data, because the file ${file.path} doesn't exists"
    ) {
      SentryEnvelopeItem.fromProfilingTrace(profilingTraceData, fixture.maxAttachmentSize, mock())
        .data
    }
  }

  @Test
  fun `fromProfilingTrace with empty file throws`() {
    val file = File(fixture.pathname)
    file.writeBytes(ByteArray(0))
    val profilingTraceData = mock<ProfilingTraceData> { whenever(it.traceFile).thenReturn(file) }

    val traceData =
      SentryEnvelopeItem.fromProfilingTrace(profilingTraceData, fixture.maxAttachmentSize, mock())
    assertFailsWith<SentryEnvelopeException>("Profiling trace file is empty") { traceData.data }
  }

  @Test
  fun `fromProfilingTrace with file too big`() {
    val file = File(fixture.pathname)
    file.writeBytes(fixture.bytesTooBig)
    val profilingTraceData = mock<ProfilingTraceData> { whenever(it.traceFile).thenReturn(file) }

    val exception =
      assertFailsWith<IOException> {
        SentryEnvelopeItem.fromProfilingTrace(profilingTraceData, fixture.maxAttachmentSize, mock())
          .data
      }

    assertEquals(
      "Reading file failed, because size located at " +
        "'${fixture.pathname}' with ${file.length()} bytes is bigger than the maximum " +
        "allowed size of ${fixture.maxAttachmentSize} bytes.",
      exception.message,
    )
  }

  @Test
  fun `fromProfileChunk sets platform header`() {
    val file = File(fixture.pathname)
    val profileChunk =
      mock<ProfileChunk> {
        whenever(it.traceFile).thenReturn(file)
        whenever(it.platform).thenReturn("chunk platform")
      }

    val chunk = SentryEnvelopeItem.fromProfileChunk(profileChunk, mock())
    assertEquals("chunk platform", chunk.header.platform)
  }

  @Test
  fun `fromProfileChunk saves file as Base64`() {
    val file = File(fixture.pathname)
    val profileChunk =
      mock<ProfileChunk> {
        whenever(it.traceFile).thenReturn(file)
        whenever(it.platform).thenReturn("android")
      }

    file.writeBytes(fixture.bytes)
    val chunk = SentryEnvelopeItem.fromProfileChunk(profileChunk, mock()).data
    verify(profileChunk).sampledProfile =
      Base64.encodeToString(fixture.bytes, Base64.NO_WRAP or Base64.NO_PADDING)
  }

  @Test
  fun `fromProfileChunk deletes file only after reading data`() {
    val file = File(fixture.pathname)
    val profileChunk =
      mock<ProfileChunk> {
        whenever(it.traceFile).thenReturn(file)
        whenever(it.platform).thenReturn("android")
      }

    file.writeBytes(fixture.bytes)
    assert(file.exists())
    val chunk = SentryEnvelopeItem.fromProfileChunk(profileChunk, mock())
    assert(file.exists())
    chunk.data
    assertFalse(file.exists())
  }

  @Test
  fun `fromProfileChunk with invalid file throws`() {
    val file = File(fixture.pathname)
    val profileChunk =
      mock<ProfileChunk> {
        whenever(it.traceFile).thenReturn(file)
        whenever(it.platform).thenReturn("android")
      }

    assertFailsWith<SentryEnvelopeException>(
      "Dropping profiling trace data, because the file ${file.path} doesn't exists"
    ) {
      SentryEnvelopeItem.fromProfileChunk(profileChunk, mock()).data
    }
  }

  @Test
  fun `fromProfileChunk with unreadable file throws`() {
    val file = File(fixture.pathname)
    val profileChunk =
      mock<ProfileChunk> {
        whenever(it.traceFile).thenReturn(file)
        whenever(it.platform).thenReturn("android")
      }
    file.writeBytes(fixture.bytes)
    file.setReadable(false)
    assertFailsWith<IOException>(
      "Dropping profiling trace data, because the file ${file.path} doesn't exists"
    ) {
      SentryEnvelopeItem.fromProfileChunk(profileChunk, mock()).data
    }
  }

  @Test
  fun `fromProfileChunk with empty file throws`() {
    val file = File(fixture.pathname)
    file.writeBytes(ByteArray(0))
    val profileChunk =
      mock<ProfileChunk> {
        whenever(it.traceFile).thenReturn(file)
        whenever(it.platform).thenReturn("android")
      }

    val chunk = SentryEnvelopeItem.fromProfileChunk(profileChunk, mock())
    assertFailsWith<SentryEnvelopeException>("Profiling trace file is empty") { chunk.data }
  }

  @Test
  fun `fromProfileChunk with file too big`() {
    val file = File(fixture.pathname)
    val maxSize = 50 * 1024 * 1024 // 50MB
    file.writeBytes(ByteArray((maxSize + 1)) { 0 })
    val profileChunk =
      mock<ProfileChunk> {
        whenever(it.traceFile).thenReturn(file)
        whenever(it.platform).thenReturn("android")
      }

    val exception =
      assertFailsWith<IOException> {
        SentryEnvelopeItem.fromProfileChunk(profileChunk, mock()).data
      }

    assertEquals(
      "Reading file failed, because size located at " +
        "'${fixture.pathname}' with ${file.length()} bytes is bigger than the maximum " +
        "allowed size of $maxSize bytes.",
      exception.message,
    )
  }

  @Test
  fun `fromReplay encodes payload into msgpack`() {
    val file = Files.createTempFile("replay", "").toFile()
    val videoBytes = this::class.java.classLoader.getResource("Tongariro.jpg")!!.readBytes()
    file.writeBytes(videoBytes)

    val replayEvent =
      SentryReplayEventSerializationTest.Fixture().getSut().apply { videoFile = file }
    val replayRecording = ReplayRecordingSerializationTest.Fixture().getSut()
    val replayItem =
      SentryEnvelopeItem.fromReplay(
        fixture.serializer,
        fixture.options.logger,
        replayEvent,
        replayRecording,
        false,
      )

    assertEquals(SentryItemType.ReplayVideo, replayItem.header.type)

    assertPayload(replayItem, replayEvent, replayRecording, videoBytes)
  }

  @Test
  fun `fromReplay does not add video item when no bytes`() {
    val file = File(fixture.pathname)
    file.writeBytes(ByteArray(0))

    val replayEvent =
      SentryReplayEventSerializationTest.Fixture().getSut().apply { videoFile = file }

    val replayItem =
      SentryEnvelopeItem.fromReplay(
        fixture.serializer,
        fixture.options.logger,
        replayEvent,
        null,
        false,
      )
    replayItem.data
    assertPayload(replayItem, replayEvent, null, ByteArray(0)) { mapSize ->
      assertEquals(1, mapSize)
    }
  }

  @Test
  fun `fromReplay deletes file only after reading data`() {
    val file = File(fixture.pathname)
    val replayEvent =
      SentryReplayEventSerializationTest.Fixture().getSut().apply { videoFile = file }

    file.writeBytes(fixture.bytes)
    assert(file.exists())
    val replayItem =
      SentryEnvelopeItem.fromReplay(
        fixture.serializer,
        fixture.options.logger,
        replayEvent,
        null,
        false,
      )
    assert(file.exists())
    replayItem.data
    assertFalse(file.exists())
  }

  @Test
  fun `fromReplay cleans up video folder if cleanupReplayFolder is set`() {
    val dir = File(tmpDir.newFolder().absolutePath)
    val file = File(dir, fixture.pathname)
    val replayEvent =
      SentryReplayEventSerializationTest.Fixture().getSut().apply { videoFile = file }

    file.writeBytes(fixture.bytes)
    assert(file.exists())
    val replayItem =
      SentryEnvelopeItem.fromReplay(
        fixture.serializer,
        fixture.options.logger,
        replayEvent,
        null,
        true,
      )
    assert(file.exists())
    replayItem.data
    assertFalse(file.exists())
    assertFalse(dir.exists())
  }

  private fun createSession(): Session = Session("dis", User(), "env", "rel")

  private fun assertAttachment(
    attachment: Attachment,
    expectedBytes: ByteArray,
    actualItem: SentryEnvelopeItem,
  ) {
    assertEquals(attachment.contentType, actualItem.header.contentType)
    assertEquals(attachment.filename, actualItem.header.fileName)
    assertArrayEquals(expectedBytes, actualItem.data)
  }

  private fun serialize(serializable: JsonSerializable): ByteArray {
    ByteArrayOutputStream().use { stream ->
      BufferedWriter(OutputStreamWriter(stream, Charset.forName("UTF-8"))).use { writer ->
        fixture.serializer.serialize<JsonSerializable>(serializable, writer)
        return stream.toByteArray()
      }
    }
  }

  private fun assertPayload(
    replayItem: SentryEnvelopeItem,
    replayEvent: SentryReplayEvent,
    replayRecording: ReplayRecording?,
    videoBytes: ByteArray,
    mapSizeAsserter: (mapSize: Int) -> Unit = {},
  ) {
    val unpacker = MessagePack.newDefaultUnpacker(replayItem.data)
    val mapSize = unpacker.unpackMapHeader()
    mapSizeAsserter(mapSize)
    for (i in 0 until mapSize) {
      val key = unpacker.unpackString()
      when (key) {
        SentryItemType.ReplayEvent.itemType -> {
          val replayEventLength = unpacker.unpackBinaryHeader()
          val replayEventBytes = unpacker.readPayload(replayEventLength)
          val actualReplayEvent =
            fixture.serializer.deserialize(
              InputStreamReader(replayEventBytes.inputStream()),
              SentryReplayEvent::class.java,
            )
          assertEquals(replayEvent, actualReplayEvent)
        }
        SentryItemType.ReplayRecording.itemType -> {
          val replayRecordingLength = unpacker.unpackBinaryHeader()
          val replayRecordingBytes = unpacker.readPayload(replayRecordingLength)
          val actualReplayRecording =
            fixture.serializer.deserialize(
              InputStreamReader(replayRecordingBytes.inputStream()),
              ReplayRecording::class.java,
            )
          assertEquals(replayRecording, actualReplayRecording)
        }
        SentryItemType.ReplayVideo.itemType -> {
          val videoLength = unpacker.unpackBinaryHeader()
          val actualBytes = unpacker.readPayload(videoLength)
          assertArrayEquals(videoBytes, actualBytes)
        }
      }
    }
    unpacker.close()
  }
}
