package io.sentry.android.core.hang

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class RuntimeStatsCollectorTest {

  private class FakeSource : RuntimeStatsSource {
    var next = RuntimeStatsSnapshot()
    var reads = 0

    override fun read(): RuntimeStatsSnapshot {
      reads++
      return next
    }
  }

  private val source = FakeSource()
  private val collector = RuntimeStatsCollector(source)
  private val interval = HangInterval(0)

  private fun snapshot(gcCount: Long, gcTimeMs: Double, blocking: Long, blockingTimeMs: Double) =
    RuntimeStatsSnapshot().also {
      it.gcCount = gcCount
      it.gcTimeMs = gcTimeMs
      it.blockingGcCount = blocking
      it.blockingGcTimeMs = blockingTimeMs
      it.preOomeGcCount = 0
      it.gcWaitingTimeMs = 2.5
      it.freeMemory = 10
      it.freeMemoryUntilOome = 60
      it.totalMemory = 50
      it.maxMemory = 100
    }

  private fun stop(): Map<String, Any>? {
    val builder = HangRecord.Builder(1_000, 1)
    collector.onRecordingStopped(interval, builder)
    return builder.build().runtimeStats
  }

  @Test
  fun `records the final values and the deltas over the hang`() {
    source.next = snapshot(10, 100.0, 2, 20.0)
    collector.onSample(interval)
    source.next = snapshot(14, 130.5, 3, 45.0)

    val stats = stop()!!

    assertThat(stats[RuntimeStatsCollector.KEY_GC_COUNT]).isEqualTo(14L)
    assertThat(stats[RuntimeStatsCollector.KEY_GC_TIME_MS]).isEqualTo(130.5)
    assertThat(stats[RuntimeStatsCollector.KEY_BLOCKING_GC_COUNT]).isEqualTo(3L)
    assertThat(stats[RuntimeStatsCollector.KEY_BLOCKING_GC_TIME_MS]).isEqualTo(45.0)
    assertThat(stats[RuntimeStatsCollector.KEY_PRE_OOME_GC_COUNT]).isEqualTo(0L)
    assertThat(stats[RuntimeStatsCollector.KEY_GC_WAITING_TIME_MS]).isEqualTo(2.5)
    assertThat(stats[RuntimeStatsCollector.KEY_FREE_MEMORY]).isEqualTo(10L)
    assertThat(stats[RuntimeStatsCollector.KEY_FREE_MEMORY_UNTIL_OOME]).isEqualTo(60L)
    assertThat(stats[RuntimeStatsCollector.KEY_TOTAL_MEMORY]).isEqualTo(50L)
    assertThat(stats[RuntimeStatsCollector.KEY_MAX_MEMORY]).isEqualTo(100L)
    assertThat(stats[RuntimeStatsCollector.KEY_GC_COUNT_DELTA]).isEqualTo(4L)
    assertThat(stats[RuntimeStatsCollector.KEY_GC_TIME_MS_DELTA]).isEqualTo(30.5)
    assertThat(stats[RuntimeStatsCollector.KEY_BLOCKING_GC_COUNT_DELTA]).isEqualTo(1L)
    assertThat(stats[RuntimeStatsCollector.KEY_BLOCKING_GC_TIME_MS_DELTA]).isEqualTo(25.0)
  }

  @Test
  fun `only the first sample takes the baseline`() {
    source.next = snapshot(10, 100.0, 2, 20.0)
    collector.onSample(interval)
    source.next = snapshot(12, 100.0, 2, 20.0)
    collector.onSample(interval)
    source.next = snapshot(15, 100.0, 2, 20.0)

    assertThat(stop()!![RuntimeStatsCollector.KEY_GC_COUNT_DELTA]).isEqualTo(5L)
    assertThat(source.reads).isEqualTo(2)
  }

  @Test
  fun `stop without a sample adds nothing`() {
    assertThat(stop()).isNull()
    assertThat(source.reads).isEqualTo(0)
  }

  @Test
  fun `onRecordingCanceled drops the baseline`() {
    collector.onSample(interval)
    collector.onRecordingCanceled()

    assertThat(stop()).isNull()
  }

  @Test
  fun `a new interval after onRecordingCanceled gets a new baseline`() {
    source.next = snapshot(10, 100.0, 2, 20.0)
    collector.onSample(interval)
    collector.onRecordingCanceled()
    source.next = snapshot(20, 100.0, 2, 20.0)
    collector.onSample(interval)
    source.next = snapshot(21, 100.0, 2, 20.0)

    assertThat(stop()!![RuntimeStatsCollector.KEY_GC_COUNT_DELTA]).isEqualTo(1L)
  }

  @Test
  fun `onRecordingStarted drops the baseline of the previous interval`() {
    collector.onSample(interval)

    collector.onRecordingStarted(HangInterval(1_000))

    assertThat(stop()).isNull()
  }

  @Test
  fun `close drops the baseline`() {
    collector.onSample(interval)

    collector.close()

    assertThat(stop()).isNull()
  }

  @Test
  fun `values the source does not provide are left out`() {
    source.next = RuntimeStatsSnapshot().also { it.totalMemory = 50 }
    collector.onSample(interval)

    val stats = stop()!!

    assertThat(stats).containsExactly(RuntimeStatsCollector.KEY_TOTAL_MEMORY, 50L)
  }
}
