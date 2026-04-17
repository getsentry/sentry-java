package io.sentry.android.core

import io.sentry.CompositePerformanceCollector
import io.sentry.PerformanceCollectionData
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector
import io.sentry.profilemeasurements.ProfileMeasurement
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ChunkMeasurementCollectorTest {

  /**
   * Drives [PerfettoContinuousProfiler.ChunkMeasurementCollector] through two full `start ->
   * collect -> stop` cycles to assert that the metrics collected are correct.
   */
  @Test
  fun `each start-stop cycle returns its own independent measurements`() {
    val frameMetricsCollector: SentryFrameMetricsCollector = mock()
    val performanceCollector: CompositePerformanceCollector = mock()
    val collector = PerfettoContinuousProfiler.ChunkMeasurementCollector(frameMetricsCollector)
    val listenerCaptor = argumentCaptor<SentryFrameMetricsCollector.FrameMetricsCollectorListener>()

    // Return distinct performance data for each stop() call.
    whenever(performanceCollector.stop(any<String>()))
      .thenReturn(
        // Cycle 1: 2 samples, both with cpu + heap, only first with native.
        listOf(
          perfData(nanos = 100L, cpu = 10.0, heap = 1_000L, native = 500L),
          perfData(nanos = 200L, cpu = 20.0, heap = 2_000L, native = null),
        ),
        // Cycle 2: 3 samples, all with heap, only some with cpu/native.
        listOf(
          perfData(nanos = 1_000L, cpu = 30.0, heap = 3_000L, native = null),
          perfData(nanos = 1_100L, cpu = null, heap = 4_000L, native = 800L),
          perfData(nanos = 1_200L, cpu = 50.0, heap = 5_000L, native = 900L),
        ),
      )

    // --- Cycle 1 ---
    collector.start(performanceCollector, "chunk-1")
    verify(frameMetricsCollector).startCollection(listenerCaptor.capture())
    // onFrameMetricCollected(frameStart, frameEnd, duration, delay, isSlow, isFrozen, refreshRate)
    listenerCaptor.lastValue.apply {
      onFrameMetricCollected(0L, 1_000L, 100L, 0L, true, false, 60.0f) // slow
      onFrameMetricCollected(0L, 2_000L, 800L, 0L, false, true, 60.0f) // frozen
      onFrameMetricCollected(0L, 3_000L, 50L, 0L, false, false, 90.0f) // refresh change
    }
    val chunk1 = collector.stop()

    // --- Cycle 2 ---
    collector.start(performanceCollector, "chunk-2")
    verify(frameMetricsCollector, times(2)).startCollection(listenerCaptor.capture())
    listenerCaptor.lastValue.apply {
      onFrameMetricCollected(0L, 10_000L, 150L, 0L, true, false, 60.0f) // slow
      onFrameMetricCollected(0L, 11_000L, 200L, 0L, true, false, 60.0f) // slow
      onFrameMetricCollected(0L, 12_000L, 900L, 0L, false, true, 60.0f) // frozen
    }
    val chunk2 = collector.stop()

    // Cycle 1: 1 slow, 1 frozen; refresh rate goes 0 -> 60 -> 90 (2 changes recorded);
    // 2 cpu samples, 2 heap samples, 1 native sample.
    assertChunkCounts(chunk1, slow = 1, frozen = 1, refreshRate = 2, cpu = 2, heap = 2, native = 1)
    // Cycle 2: 2 slow, 1 frozen; refresh rate goes 0 -> 60 (1 change recorded);
    // 2 cpu samples (one was null), 3 heap samples, 2 native samples.
    assertChunkCounts(chunk2, slow = 2, frozen = 1, refreshRate = 1, cpu = 2, heap = 3, native = 2)
  }

  private fun perfData(nanos: Long, cpu: Double?, heap: Long?, native: Long?) =
    PerformanceCollectionData(nanos).apply {
      cpuUsagePercentage = cpu
      usedHeapMemory = heap
      usedNativeMemory = native
    }

  private fun assertChunkCounts(
    measurements: Map<String, ProfileMeasurement>,
    slow: Int,
    frozen: Int,
    refreshRate: Int,
    cpu: Int,
    heap: Int,
    native: Int,
  ) {
    assertEquals(slow, measurements[ProfileMeasurement.ID_SLOW_FRAME_RENDERS]?.values?.size ?: 0)
    assertEquals(
      frozen,
      measurements[ProfileMeasurement.ID_FROZEN_FRAME_RENDERS]?.values?.size ?: 0,
    )
    assertEquals(
      refreshRate,
      measurements[ProfileMeasurement.ID_SCREEN_FRAME_RATES]?.values?.size ?: 0,
    )
    assertEquals(cpu, measurements[ProfileMeasurement.ID_CPU_USAGE]?.values?.size ?: 0)
    assertEquals(heap, measurements[ProfileMeasurement.ID_MEMORY_FOOTPRINT]?.values?.size ?: 0)
    assertEquals(
      native,
      measurements[ProfileMeasurement.ID_MEMORY_NATIVE_FOOTPRINT]?.values?.size ?: 0,
    )
  }
}
