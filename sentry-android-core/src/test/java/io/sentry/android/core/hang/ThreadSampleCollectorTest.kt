package io.sentry.android.core.hang

import com.google.common.truth.Truth.assertThat
import io.sentry.time.Timestamp
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.locks.LockSupport
import kotlin.test.Test

class ThreadSampleCollectorTest {

  private val unixTimeMs = 1_700_000_000_000L
  private val timestamp = Timestamp.ofEpochNanos(MILLISECONDS.toNanos(unixTimeMs))

  @Test
  fun `sample returns the stack and the state of the thread at the given time`() {
    val parked = Thread { while (!Thread.currentThread().isInterrupted) LockSupport.park() }
    parked.isDaemon = true
    parked.start()
    try {
      val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
      while (parked.state != Thread.State.WAITING && System.nanoTime() < deadlineNanos) {
        Thread.yield()
      }
      assertThat(parked.state).isEqualTo(Thread.State.WAITING)
      val collector = ThreadSampleCollector(parked)

      val sample = collector.sample(timestamp)

      assertThat(sample.state).isEqualTo(Thread.State.WAITING)
      assertThat(sample.timestampMs).isEqualTo(unixTimeMs)
      assertThat(sample.stack.map { it.methodName }).contains("park")
    } finally {
      parked.interrupt()
      parked.join()
    }
  }
}
