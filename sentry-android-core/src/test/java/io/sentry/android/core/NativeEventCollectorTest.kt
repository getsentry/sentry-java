package io.sentry.android.core

import io.sentry.DateUtils
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock

class NativeEventCollectorTest {

  @get:Rule val tmpDir = TemporaryFolder()

  class Fixture {
    lateinit var outboxDir: File

    val options =
      SentryAndroidOptions().apply {
        setLogger(mock())
        isDebug = true
      }

    fun getSut(tmpDir: TemporaryFolder): NativeEventCollector {
      outboxDir = File(tmpDir.root, "outbox")
      outboxDir.mkdirs()
      options.cacheDirPath = tmpDir.root.absolutePath
      return NativeEventCollector(options)
    }
  }

  private val fixture = Fixture()

  @Test
  fun `collects native event from outbox`() {
    val sut = fixture.getSut(tmpDir)
    copyEnvelopeToOutbox("native-event.txt")

    val timestamp = DateUtils.getDateTime("2023-07-15T10:30:00.000Z").time
    val match = sut.findAndRemoveMatchingNativeEvent(timestamp)
    assertNotNull(match)
  }

  @Test
  fun `does not collect java platform event`() {
    val sut = fixture.getSut(tmpDir)
    copyEnvelopeToOutbox("java-event.txt")

    val match = sut.findAndRemoveMatchingNativeEvent(0L)
    assertNull(match)
  }

  @Test
  fun `does not collect session-only envelope`() {
    val sut = fixture.getSut(tmpDir)
    copyEnvelopeToOutbox("session-only.txt")

    val match = sut.findAndRemoveMatchingNativeEvent(0L)
    assertNull(match)
  }

  @Test
  fun `collects native event after skipping attachment`() {
    val sut = fixture.getSut(tmpDir)
    copyEnvelopeToOutbox("native-with-attachment.txt")

    val timestamp = DateUtils.getDateTime("2023-07-15T11:45:30.500Z").time
    val match = sut.findAndRemoveMatchingNativeEvent(timestamp)
    assertNotNull(match)
  }

  @Test
  fun `handles empty file without throwing`() {
    val sut = fixture.getSut(tmpDir)
    File(fixture.outboxDir, "empty.envelope").writeText("")

    val match = sut.findAndRemoveMatchingNativeEvent(0L)
    assertNull(match)
  }

  @Test
  fun `handles malformed envelope without throwing`() {
    val sut = fixture.getSut(tmpDir)
    File(fixture.outboxDir, "malformed.envelope").writeText("this is not a valid envelope")

    val match = sut.findAndRemoveMatchingNativeEvent(0L)
    assertNull(match)
  }

  @Test
  fun `handles envelope with event and attachments without throwing`() {
    val sut = fixture.getSut(tmpDir)
    copyEnvelopeToOutbox("event-attachment.txt")

    val match = sut.findAndRemoveMatchingNativeEvent(0L)
    assertNull(match)
  }

  @Test
  fun `handles transaction envelope without throwing`() {
    val sut = fixture.getSut(tmpDir)
    copyEnvelopeToOutbox("transaction.txt")

    val match = sut.findAndRemoveMatchingNativeEvent(0L)
    assertNull(match)
  }

  @Test
  fun `handles session envelope without throwing`() {
    val sut = fixture.getSut(tmpDir)
    copyEnvelopeToOutbox("session.txt")

    val match = sut.findAndRemoveMatchingNativeEvent(0L)
    assertNull(match)
  }

  @Test
  fun `handles feedback envelope without throwing`() {
    val sut = fixture.getSut(tmpDir)
    copyEnvelopeToOutbox("feedback.txt")

    val match = sut.findAndRemoveMatchingNativeEvent(0L)
    assertNull(match)
  }

  @Test
  fun `handles attachment-only envelope without throwing`() {
    val sut = fixture.getSut(tmpDir)
    copyEnvelopeToOutbox("attachment.txt")

    val match = sut.findAndRemoveMatchingNativeEvent(0L)
    assertNull(match)
  }

  @Test
  fun `collects multiple native events`() {
    val sut = fixture.getSut(tmpDir)
    copyEnvelopeToOutbox("native-event.txt")
    copyEnvelopeToOutbox("native-with-attachment.txt")

    val timestamp1 = DateUtils.getDateTime("2023-07-15T10:30:00.000Z").time
    val timestamp2 = DateUtils.getDateTime("2023-07-15T11:45:30.500Z").time
    val match1 = sut.findAndRemoveMatchingNativeEvent(timestamp1)
    val match2 = sut.findAndRemoveMatchingNativeEvent(timestamp2)
    assertNotNull(match1)
    assertNotNull(match2)
  }

  @Test
  fun `ignores non-native events when collecting multiple envelopes`() {
    val sut = fixture.getSut(tmpDir)
    copyEnvelopeToOutbox("native-event.txt")
    copyEnvelopeToOutbox("java-event.txt")
    copyEnvelopeToOutbox("transaction.txt")
    copyEnvelopeToOutbox("session.txt")

    val timestamp = DateUtils.getDateTime("2023-07-15T10:30:00.000Z").time
    val nativeMatch = sut.findAndRemoveMatchingNativeEvent(timestamp)
    assertNotNull(nativeMatch)

    // No other matches (already removed)
    val noMatch = sut.findAndRemoveMatchingNativeEvent(timestamp)
    assertNull(noMatch)
  }

  private fun copyEnvelopeToOutbox(name: String): File {
    val resourcePath = "envelopes/$name"
    val inputStream =
      javaClass.classLoader?.getResourceAsStream(resourcePath)
        ?: throw IllegalArgumentException("Resource not found: $resourcePath")
    val outFile = File(fixture.outboxDir, name)
    inputStream.use { input -> outFile.outputStream().use { output -> input.copyTo(output) } }
    return outFile
  }
}
