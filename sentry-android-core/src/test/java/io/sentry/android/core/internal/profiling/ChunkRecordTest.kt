package io.sentry.android.core.internal.profiling

import io.sentry.SentryLongDate
import io.sentry.profiling.ProfileRecordingState
import io.sentry.protocol.SentryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChunkRecordTest {
  private val chunkStart = SentryLongDate(1000)
  private val chunkEnd = SentryLongDate(2000)

  private fun getSut(withEnd: Boolean = true): ChunkRecord =
    ChunkRecord(SentryId(), chunkStart).apply { if (withEnd) setEndTimestamp(chunkEnd) }

  @Test
  fun `a new chunk has an unknown state`() {
    assertEquals(ProfileRecordingState.UNKNOWN, getSut().recordingState)
  }

  @Test
  fun `not recorded is final`() {
    val chunk = getSut()

    chunk.recordingState = ProfileRecordingState.NOT_RECORDED
    chunk.recordingState = ProfileRecordingState.RECORDED

    assertEquals(ProfileRecordingState.NOT_RECORDED, chunk.recordingState)
  }

  @Test
  fun `a recorded chunk can still turn out to be not recorded`() {
    val chunk = getSut()

    chunk.recordingState = ProfileRecordingState.RECORDED
    chunk.recordingState = ProfileRecordingState.NOT_RECORDED

    assertEquals(ProfileRecordingState.NOT_RECORDED, chunk.recordingState)
  }

  @Test
  fun `a window inside the chunk overlaps`() {
    assertTrue(getSut().overlaps(SentryLongDate(1200), SentryLongDate(1800)))
  }

  @Test
  fun `a window around the chunk overlaps`() {
    assertTrue(getSut().overlaps(SentryLongDate(500), SentryLongDate(2500)))
  }

  @Test
  fun `a window ending exactly at the chunk start overlaps`() {
    assertTrue(getSut().overlaps(SentryLongDate(500), chunkStart))
  }

  @Test
  fun `a window starting exactly at the chunk end overlaps`() {
    assertTrue(getSut().overlaps(chunkEnd, SentryLongDate(2500)))
  }

  @Test
  fun `a window before the chunk does not overlap`() {
    assertFalse(getSut().overlaps(SentryLongDate(500), SentryLongDate(999)))
  }

  @Test
  fun `a window after the chunk does not overlap`() {
    assertFalse(getSut().overlaps(SentryLongDate(2001), SentryLongDate(2500)))
  }

  @Test
  fun `a running chunk covers everything from its start on`() {
    val chunk = getSut(withEnd = false)

    assertTrue(chunk.overlaps(SentryLongDate(1200), SentryLongDate(1200)))
    assertTrue(chunk.overlaps(SentryLongDate(9000), SentryLongDate(9000)))
    assertFalse(chunk.overlaps(SentryLongDate(500), SentryLongDate(999)))
  }
}
