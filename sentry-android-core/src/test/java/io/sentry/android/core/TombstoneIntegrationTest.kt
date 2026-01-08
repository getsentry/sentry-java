package io.sentry.android.core

import android.app.ApplicationExitInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.android.core.TombstoneIntegration.TombstoneHint
import io.sentry.android.core.cache.AndroidEnvelopeCache
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.kotlin.spy
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
                  TombstoneIntegrationTest::class.java.getResourceAsStream("/tombstone.pb")
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
    assertEquals("0x764c32a000", image.imageAddr)
    assertEquals(32768, image.imageSize)
  }
}
