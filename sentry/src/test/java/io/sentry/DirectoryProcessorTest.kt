package io.sentry

import io.sentry.hints.ApplyScopeData
import io.sentry.hints.Enqueable
import io.sentry.hints.Retryable
import io.sentry.transport.RateLimiter
import io.sentry.util.HintUtils
import io.sentry.util.noFlushTimeout
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argWhere
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DirectoryProcessorTest {
  private class Fixture {
    var scopes: IScopes = mock()
    var envelopeReader: IEnvelopeReader = mock()
    var serializer: ISerializer = mock()
    var logger: ILogger = mock()
    var options = SentryOptions().noFlushTimeout()

    init {
      options.setDebug(true)
      options.setLogger(logger)
    }

    fun getSut(isRetryable: Boolean = false, isRateLimitingActive: Boolean = false): OutboxSender {
      val hintCaptor = argumentCaptor<Hint>()
      whenever(scopes.captureEvent(any(), hintCaptor.capture())).then {
        HintUtils.runIfHasType(hintCaptor.firstValue, Enqueable::class.java) { enqueable: Enqueable
          ->
          enqueable.markEnqueued()

          // activate rate limiting when a first envelope was processed
          if (isRateLimitingActive) {
            val rateLimiter =
              mock<RateLimiter> { whenever(mock.isActiveForCategory(any())).thenReturn(true) }
            whenever(scopes.rateLimiter).thenReturn(rateLimiter)
          }
        }
        HintUtils.runIfHasType(hintCaptor.firstValue, Retryable::class.java) { retryable ->
          retryable.isRetry = isRetryable
        }
      }
      return OutboxSender(scopes, envelopeReader, serializer, logger, 500, 30)
    }
  }

  private val fixture = Fixture()

  private lateinit var file: File

  @BeforeTest
  fun `set up`() {
    file = Files.createTempDirectory("sentry-disk-cache-test").toAbsolutePath().toFile()
  }

  @AfterTest
  fun shutdown() {
    file.deleteRecursively()
  }

  @Test
  fun `process directory folder has a non ApplyScopeData hint`() {
    val path = getTempEnvelope("envelope-event-attachment.txt")
    assertTrue(File(path).exists()) // sanity check
    val event = SentryEvent()
    val envelope = SentryEnvelope.from(fixture.serializer, event, null)

    whenever(fixture.envelopeReader.read(any())).thenReturn(envelope)
    whenever(fixture.serializer.deserialize(any(), eq(SentryEvent::class.java))).thenReturn(event)

    fixture.getSut().processDirectory(file)
    verify(fixture.scopes)
      .captureEvent(any(), argWhere<Hint> { !HintUtils.hasType(it, ApplyScopeData::class.java) })
  }

  @Test
  fun `process directory ignores non files on the cache folder`() {
    val dir = File(file.absolutePath, "testDir")
    dir.mkdirs()
    assertTrue(dir.exists()) // sanity check
    fixture.getSut().processDirectory(file)
    verify(fixture.scopes, never()).captureEnvelope(any(), any())
  }

  @Test
  fun `when envelope has already been submitted to the queue, does not process it again`() {
    getTempEnvelope("envelope-event-attachment.txt")

    val event = SentryEvent()
    val envelope = SentryEnvelope.from(fixture.serializer, event, null)

    whenever(fixture.envelopeReader.read(any())).thenReturn(envelope)
    whenever(fixture.serializer.deserialize(any(), eq(SentryEvent::class.java))).thenReturn(event)

    // make it retryable so it doesn't get deleted
    val sut = fixture.getSut(isRetryable = true)
    sut.processDirectory(file)

    // process it once again
    sut.processDirectory(file)

    // should only capture once
    verify(fixture.scopes).captureEvent(any(), anyOrNull<Hint>())
  }

  @Test
  fun `when rate limiting gets active in the middle of processing, stops processing`() {
    getTempEnvelope("envelope-event-attachment.txt")
    getTempEnvelope("envelope-event-attachment.txt")

    val event = SentryEvent()
    val envelope = SentryEnvelope.from(fixture.serializer, event, null)

    whenever(fixture.envelopeReader.read(any())).thenReturn(envelope)
    whenever(fixture.serializer.deserialize(any(), eq(SentryEvent::class.java))).thenReturn(event)

    val sut = fixture.getSut(isRateLimitingActive = true)
    sut.processDirectory(file)

    // should only capture once
    verify(fixture.scopes).captureEvent(any(), anyOrNull<Hint>())
  }

  private fun getTempEnvelope(fileName: String): String {
    val testFile = this::class.java.classLoader.getResource(fileName)
    val testFileBytes = testFile!!.readBytes()
    val targetFile = File.createTempFile("temp-envelope", ".tmp", file)
    Files.write(Paths.get(targetFile.toURI()), testFileBytes)
    return targetFile.absolutePath
  }
}
