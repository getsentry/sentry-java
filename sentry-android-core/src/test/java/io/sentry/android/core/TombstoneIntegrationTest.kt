package io.sentry.android.core

import android.app.ApplicationExitInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.DateUtils
import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.android.core.TombstoneIntegration.TombstoneHint
import io.sentry.android.core.cache.AndroidEnvelopeCache
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.check
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder

@RunWith(AndroidJUnit4::class)
@Config(sdk = [31])
class TombstoneIntegrationTest : ApplicationExitIntegrationTestBase<TombstoneHint>() {

  override val config =
    IntegrationTestConfig(
      setEnabledFlag = { isTombstoneEnabled = it },
      setReportHistoricalFlag = { isReportHistoricalTombstones = it },
      createIntegration = { context -> TombstoneIntegration(context) },
      lastReportedFileName = AndroidEnvelopeCache.LAST_TOMBSTONE_REPORT,
      defaultExitReason = ApplicationExitInfo.REASON_CRASH_NATIVE,
      hintAccessors =
        HintAccessors(
          cast = { it as TombstoneHint },
          shouldEnrich = { it.shouldEnrich() },
          timestamp = { it.timestamp() },
        ),
      addExitInfo = { reason, timestamp, importance, addTrace, addBadTrace ->
        val builder = ApplicationExitInfoBuilder.newBuilder()
        reason?.let { builder.setReason(it) }
        timestamp?.let { builder.setTimestamp(it) }
        importance?.let { builder.setImportance(it) }
        val exitInfo =
          spy(builder.build()) {
            if (!addTrace) {
              return@spy
            }
            if (addBadTrace) {
              whenever(mock.traceInputStream).thenReturn("XXXXX".byteInputStream())
            } else {
              whenever(mock.traceInputStream)
                .thenReturn(
                  GZIPInputStream(
                    TombstoneIntegrationTest::class.java.getResourceAsStream("/tombstone.pb.gz")
                  )
                )
            }
          }
        shadowActivityManager.addApplicationExitInfo(exitInfo)
      },
      flushLogPrefix = "Timed out waiting to flush Tombstone event to disk.",
    )

  override fun assertEnrichedEvent(event: SentryEvent) {
    assertEquals(SentryLevel.FATAL, event.level)
    assertEquals(newTimestamp, event.timestamp!!.time)
    assertEquals("native", event.platform)

    val crashedThreadId = 21891L
    assertEquals(crashedThreadId, event.exceptions!![0].threadId)
    val crashedThread = event.threads!!.find { thread -> thread.id == crashedThreadId }
    assertEquals("samples.android", crashedThread!!.name)
    assertTrue(crashedThread.isCrashed!!)

    // Verify that frames from the app's native library are marked as in-app
    val inAppFrames = crashedThread.stacktrace!!.frames!!.filter { it.isInApp == true }
    assertTrue(inAppFrames.size >= 3, "Expected at least 3 in-app frames, got ${inAppFrames.size}")
    // Should include the native sample library crash function
    assertTrue(
      inAppFrames.any { it.`package`?.contains("libnative-sample.so") == true },
      "Expected in-app frame from libnative-sample.so",
    )

    val image =
      event.debugMeta?.images?.find { image -> image.codeId == "f60b4b74005f33fb3ef3b98aa4546008" }
    assertEquals("744b0bf6-5f00-fb33-3ef3-b98aa4546008", image!!.debugId)
    assertNotNull(image)
    assertEquals("/system/lib64/libcompiler_rt.so", image.codeFile)
    assertEquals("0x764c325000", image.imageAddr)
    assertEquals(57344, image.imageSize)
  }

  @Test
  fun `when matching native event has attachments, they are added to the hint`() {
    val integration =
      fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp) { options ->
        // Set up the outbox directory with the native envelope containing an attachment
        // Use newTimestamp to match the tombstone timestamp
        val outboxDir = File(options.outboxPath!!)
        outboxDir.mkdirs()
        createNativeEnvelopeWithAttachment(outboxDir, newTimestamp)
      }

    // Add tombstone with timestamp matching the native event
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes)
      .captureEvent(
        any(),
        argThat<Hint> {
          val attachments = this.attachments
          attachments.size == 2 &&
            attachments[0].filename == "test-attachment.txt" &&
            attachments[0].contentType == "text/plain" &&
            String(attachments[0].bytes!!) == "some attachment content" &&
            attachments[1].filename == "test-another-attachment.txt" &&
            attachments[1].contentType == "text/plain" &&
            String(attachments[1].bytes!!) == "another attachment content"
        },
      )
  }

  @Test
  fun `when merging with native event, uses native event as base with tombstone stack traces`() {
    val integration =
      fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp) { options ->
        val outboxDir = File(options.outboxPath!!)
        outboxDir.mkdirs()
        createNativeEnvelopeWithContext(outboxDir, newTimestamp)
      }

    // Add tombstone with timestamp matching the native event
    fixture.addAppExitInfo(timestamp = newTimestamp)

    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes)
      .captureEvent(
        check<SentryEvent> { event ->
          // Verify native SDK context is preserved
          assertEquals("native-sdk-user-id", event.user?.id)
          assertEquals("native-sdk-tag-value", event.getTag("native-sdk-tag"))

          // Verify tombstone stack trace data is applied
          assertNotNull(event.exceptions)
          assertTrue(event.exceptions!!.isNotEmpty())
          assertEquals("TombstoneMerged", event.exceptions!![0].mechanism?.type)

          // Verify tombstone debug meta is applied
          assertNotNull(event.debugMeta)
          assertTrue(event.debugMeta!!.images!!.isNotEmpty())

          // Verify tombstone threads are applied (tombstone has 62 threads)
          assertEquals(62, event.threads?.size)
        },
        any<Hint>(),
      )
  }

  private fun createNativeEnvelopeWithContext(outboxDir: File, timestamp: Long): File {
    val isoTimestamp = DateUtils.getTimestamp(DateUtils.getDateTime(timestamp))

    // Native SDK event with user context and tags that should be preserved after merge
    val eventJson =
      """{"event_id":"9ec79c33ec9942ab8353589fcb2e04dc","timestamp":"$isoTimestamp","platform":"native","level":"fatal","user":{"id":"native-sdk-user-id"},"tags":{"native-sdk-tag":"native-sdk-tag-value"}}"""
    val eventJsonSize = eventJson.toByteArray(Charsets.UTF_8).size

    val envelopeContent =
      """
    {"event_id":"9ec79c33ec9942ab8353589fcb2e04dc"}
    {"type":"event","length":$eventJsonSize,"content_type":"application/json"}
    $eventJson
  """
        .trimIndent()

    return File(outboxDir, "native-envelope-with-context.envelope").apply {
      writeText(envelopeContent)
    }
  }

  @Test
  fun `when native event has no message, tombstone message is applied`() {
    val integration =
      fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp) { options ->
        val outboxDir = File(options.outboxPath!!)
        outboxDir.mkdirs()
        createNativeEnvelope(outboxDir, newTimestamp, messageJson = null)
      }

    fixture.addAppExitInfo(timestamp = newTimestamp)
    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes)
      .captureEvent(
        check<SentryEvent> { event ->
          // Tombstone message should be applied
          assertNotNull(event.message)
          assertNotNull(event.message!!.formatted)
          // The message contains the signal info from the tombstone
          assertTrue(event.message!!.formatted!!.contains("Fatal signal"))
        },
        any<Hint>(),
      )
  }

  @Test
  fun `when native event has message with null template, tombstone message is applied`() {
    val integration =
      fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp) { options ->
        val outboxDir = File(options.outboxPath!!)
        outboxDir.mkdirs()
        createNativeEnvelope(
          outboxDir,
          newTimestamp,
          messageJson = """{"formatted":"some formatted text"}""",
        )
      }

    fixture.addAppExitInfo(timestamp = newTimestamp)
    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes)
      .captureEvent(
        check<SentryEvent> { event ->
          // Tombstone message should be applied
          assertNotNull(event.message)
          assertNotNull(event.message!!.formatted)
          assertTrue(event.message!!.formatted!!.contains("Fatal signal"))
        },
        any<Hint>(),
      )
  }

  @Test
  fun `when native event has message with empty template, tombstone message is applied`() {
    val integration =
      fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp) { options ->
        val outboxDir = File(options.outboxPath!!)
        outboxDir.mkdirs()
        createNativeEnvelope(
          outboxDir,
          newTimestamp,
          messageJson = """{"message":"","formatted":"some formatted text"}""",
        )
      }

    fixture.addAppExitInfo(timestamp = newTimestamp)
    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes)
      .captureEvent(
        check<SentryEvent> { event ->
          // Tombstone message should be applied
          assertNotNull(event.message)
          assertNotNull(event.message!!.formatted)
          assertTrue(event.message!!.formatted!!.contains("Fatal signal"))
        },
        any<Hint>(),
      )
  }

  @Test
  fun `when native event has message with content, native message is preserved`() {
    val integration =
      fixture.getSut(tmpDir, lastReportedTimestamp = oldTimestamp) { options ->
        val outboxDir = File(options.outboxPath!!)
        outboxDir.mkdirs()
        createNativeEnvelope(
          outboxDir,
          newTimestamp,
          messageJson =
            """{"message":"Native SDK crash message","formatted":"The crash happened at 0xDEADBEEF"}""",
        )
      }

    fixture.addAppExitInfo(timestamp = newTimestamp)
    integration.register(fixture.scopes, fixture.options)

    verify(fixture.scopes)
      .captureEvent(
        check<SentryEvent> { event ->
          // Native SDK message should be preserved
          assertNotNull(event.message)
          assertEquals("Native SDK crash message", event.message!!.message)
          assertEquals("The crash happened at 0xDEADBEEF", event.message!!.formatted)
        },
        any<Hint>(),
      )
  }

  /**
   * Creates a native envelope file with an optional message field.
   *
   * @param messageJson The JSON for the message field (e.g.,
   *   `{"message":"text","formatted":"text"}`), or null to omit the message field entirely.
   */
  private fun createNativeEnvelope(
    outboxDir: File,
    timestamp: Long,
    messageJson: String? = null,
    fileName: String = "native-envelope.envelope",
  ): File {
    val isoTimestamp = DateUtils.getTimestamp(DateUtils.getDateTime(timestamp))
    val messageField = if (messageJson != null) ""","message":$messageJson""" else ""

    val eventJson =
      """{"event_id":"9ec79c33ec9942ab8353589fcb2e04dc","timestamp":"$isoTimestamp","platform":"native","level":"fatal"$messageField}"""
    val eventJsonSize = eventJson.toByteArray(Charsets.UTF_8).size

    val envelopeContent =
      """
    {"event_id":"9ec79c33ec9942ab8353589fcb2e04dc"}
    {"type":"event","length":$eventJsonSize,"content_type":"application/json"}
    $eventJson
  """
        .trimIndent()

    return File(outboxDir, fileName).apply { writeText(envelopeContent) }
  }

  private fun createNativeEnvelopeWithAttachment(outboxDir: File, timestamp: Long): File {
    val isoTimestamp = DateUtils.getTimestamp(DateUtils.getDateTime(timestamp))

    val eventJson =
      """{"event_id":"9ec79c33ec9942ab8353589fcb2e04dc","timestamp":"$isoTimestamp","platform":"native","level":"fatal"}"""
    val eventJsonSize = eventJson.toByteArray(Charsets.UTF_8).size

    val attachment1Content = "some attachment content"
    val attachment1ContentSize = attachment1Content.toByteArray(Charsets.UTF_8).size

    val attachment2Content = "another attachment content"
    val attachment2ContentSize = attachment2Content.toByteArray(Charsets.UTF_8).size

    val envelopeContent =
      """
    {"event_id":"9ec79c33ec9942ab8353589fcb2e04dc"}
    {"type":"attachment","length":$attachment1ContentSize,"filename":"test-attachment.txt","content_type":"text/plain"}
    $attachment1Content
    {"type":"attachment","length":$attachment2ContentSize,"filename":"test-another-attachment.txt","content_type":"text/plain"}
    $attachment2Content
    {"type":"event","length":$eventJsonSize,"content_type":"application/json"}
    $eventJson
  """
        .trimIndent()

    return File(outboxDir, "native-envelope-with-attachment.envelope").apply {
      writeText(envelopeContent)
    }
  }
}
