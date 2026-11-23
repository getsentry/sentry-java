package io.sentry.android.core.hang

import com.google.common.truth.Truth.assertThat
import io.sentry.android.core.anr.AnrStackTrace
import io.sentry.time.EpochClock
import io.sentry.time.Timestamp
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.locks.LockSupport
import kotlin.test.Test

class ThreadSampleCollectorTest {

  private var unixTimeMs = 1_700_000_000_000L
  private val epochClock = EpochClock { Timestamp.ofEpochNanos(MILLISECONDS.toNanos(unixTimeMs)) }
  private val interval = HangInterval(0)

  @Test
  fun `onSample returns the stack, the state and the time of the thread`() {
    val parked = Thread { while (!Thread.currentThread().isInterrupted) LockSupport.park() }
    parked.isDaemon = true
    parked.start()
    try {
      val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
      while (parked.state != Thread.State.WAITING && System.nanoTime() < deadlineNanos) {
        Thread.yield()
      }
      assertThat(parked.state).isEqualTo(Thread.State.WAITING)
      val collector = ThreadSampleCollector(epochClock, parked)

      val sample = collector.onSample(interval)

      assertThat(sample.state).isEqualTo(Thread.State.WAITING)
      assertThat(sample.timestampMs).isEqualTo(unixTimeMs)
      assertThat(sample.stack.map { it.methodName }).contains("park")
    } finally {
      parked.interrupt()
      parked.join()
    }
  }

  @Test
  fun `each onSample reads the clock again`() {
    val collector = ThreadSampleCollector(epochClock, Thread.currentThread())

    val first = collector.onSample(interval)
    unixTimeMs += 66
    val second = collector.onSample(interval)

    assertThat(second.timestampMs - first.timestampMs).isEqualTo(66)
  }

  @Test
  fun `onRecordingStopped puts a copy of the samples into the record`() {
    val collector = ThreadSampleCollector(epochClock, Thread.currentThread())
    val samples = mutableListOf(AnrStackTrace(1, emptyArray()), AnrStackTrace(2, emptyArray()))
    val builder = HangRecord.Builder(66, unixTimeMs)

    collector.onRecordingStopped(samples, builder)
    samples.clear()

    assertThat(builder.build().samples.map { it.timestampMs }).containsExactly(1L, 2L).inOrder()
  }
}
