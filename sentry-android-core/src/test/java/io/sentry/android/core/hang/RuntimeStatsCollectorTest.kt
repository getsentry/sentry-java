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

  private fun stop(samples: List<RuntimeStatsSnapshot>): Map<String, Any>? {
    val builder = HangRecord.Builder(1_000, 1)
    collector.onRecordingStopped(samples, builder)
    return builder.build().runtimeStats
  }

  @Test
  fun `onSample returns a read of the source`() {
    val snapshot = snapshot(10, 100.0, 2, 20.0)
    source.next = snapshot

    assertThat(collector.onSample(interval)).isSameInstanceAs(snapshot)
    assertThat(source.reads).isEqualTo(1)
  }

  @Test
  fun `records the last values and the deltas from the first to the last sample`() {
    val stats =
      stop(
        listOf(
          snapshot(10, 100.0, 2, 20.0),
          snapshot(12, 110.0, 2, 30.0),
          snapshot(14, 130.5, 3, 45.0),
        ),
      )!!

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
  fun `one sample gives zero deltas`() {
    val stats = stop(listOf(snapshot(10, 100.0, 2, 20.0)))!!

    assertThat(stats[RuntimeStatsCollector.KEY_GC_COUNT]).isEqualTo(10L)
    assertThat(stats[RuntimeStatsCollector.KEY_GC_COUNT_DELTA]).isEqualTo(0L)
    assertThat(stats[RuntimeStatsCollector.KEY_BLOCKING_GC_TIME_MS_DELTA]).isEqualTo(0.0)
  }

  @Test
  fun `no samples adds nothing`() {
    assertThat(stop(emptyList())).isNull()
  }

  @Test
  fun `values the source does not provide are left out`() {
    val stats = stop(listOf(RuntimeStatsSnapshot().also { it.totalMemory = 50 }))!!

    assertThat(stats).containsExactly(RuntimeStatsCollector.KEY_TOTAL_MEMORY, 50L)
  }

  @Test
  fun `a delta needs the value in the first and the last sample`() {
    val first = RuntimeStatsSnapshot().also { it.gcTimeMs = 100.0 }
    val last =
      RuntimeStatsSnapshot().also {
        it.gcCount = 14
        it.gcTimeMs = 130.0
      }

    val stats = stop(listOf(first, last))!!

    assertThat(stats).doesNotContainKey(RuntimeStatsCollector.KEY_GC_COUNT_DELTA)
    assertThat(stats[RuntimeStatsCollector.KEY_GC_TIME_MS_DELTA]).isEqualTo(30.0)
  }
}
