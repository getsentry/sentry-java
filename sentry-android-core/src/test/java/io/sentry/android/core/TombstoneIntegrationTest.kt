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
