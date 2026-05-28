package io.sentry

import io.sentry.protocol.SentryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.mockito.kotlin.mock

class NoOpContinuousProfilerTest {
  private var profiler = NoOpContinuousProfiler.getInstance()

  @Test fun `start does not throw`() = profiler.startProfiler(mock(), mock())

  @Test fun `stop does not throw`() = profiler.stopProfiler(mock())

  @Test
  fun `isRunning returns false`() {
    assertFalse(profiler.isRunning)
  }

  @Test fun `close does not throw`() = profiler.close(true)

  @Test
  fun `getProfilerId returns Empty SentryId`() {
    assertEquals(profiler.profilerId, SentryId.EMPTY_ID)
  }

  @Test
  fun `getChunkId returns Empty SentryId`() {
    assertEquals(profiler.chunkId, SentryId.EMPTY_ID)
  }

  @Test
  fun `reevaluateSampling does not throw`() {
    profiler.reevaluateSampling()
  }
}
