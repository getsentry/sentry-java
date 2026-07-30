package io.sentry

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AsyncEventProcessingExecutorTest {
  private val noOpDrop = Runnable {}

  private fun getSut(maxQueueSize: Int = 10) =
    AsyncEventProcessingExecutor(maxQueueSize, NoOpLogger.getInstance())

  @Test
  fun `submit runs the task off the caller thread`() {
    val sut = getSut()
    val done = CountDownLatch(1)
    var taskThread: String? = null

    assertTrue(
      sut.submit(
        {
          taskThread = Thread.currentThread().name
          done.countDown()
        },
        noOpDrop,
      )
    )

    assertTrue(done.await(5, TimeUnit.SECONDS))
    assertTrue(taskThread!!.startsWith("SentryAsyncEventProcessing-"))
    sut.close(5000)
  }

  @Test
  fun `submit rejects once the queue is full`() {
    val sut = getSut(maxQueueSize = 1)
    val block = CountDownLatch(1)
    val started = CountDownLatch(1)

    assertTrue(
      sut.submit(
        {
          started.countDown()
          block.await(5, TimeUnit.SECONDS)
        },
        noOpDrop,
      )
    )
    assertTrue(started.await(5, TimeUnit.SECONDS))
    assertFalse(sut.submit({}, noOpDrop))

    block.countDown()
    sut.close(5000)
  }

  @Test
  fun `submit rejects after close`() {
    val sut = getSut()
    sut.close(5000)

    assertTrue(sut.isClosed)
    assertFalse(sut.submit({}, noOpDrop))
  }

  @Test
  fun `close reports tasks that never ran`() {
    val sut = getSut()
    val block = CountDownLatch(1)
    val started = CountDownLatch(1)
    val dropped = AtomicInteger()

    sut.submit(
      {
        started.countDown()
        block.await(10, TimeUnit.SECONDS)
      },
      noOpDrop,
    )
    assertTrue(started.await(5, TimeUnit.SECONDS))
    // Queued behind the blocked worker, so shutdownNow never gets to run it.
    sut.submit({}, { dropped.incrementAndGet() })

    sut.close(100)

    assertEquals(1, dropped.get())
    block.countDown()
  }

  @Test
  fun `submit does not block behind a draining close`() {
    val sut = getSut()
    val block = CountDownLatch(1)
    val started = CountDownLatch(1)
    sut.submit(
      {
        started.countDown()
        block.await(10, TimeUnit.SECONDS)
      },
      noOpDrop,
    )
    assertTrue(started.await(5, TimeUnit.SECONDS))

    val closing = Thread { sut.close(30000) }
    closing.start()
    awaitThreadState(closing, Thread.State.TIMED_WAITING)

    // close() is parked in awaitTermination. Submitting must return right away (rejected, since
    // the executor is closed) rather than block on a lock held for the whole shutdown.
    val durationNanos = measureNanoTime { assertFalse(sut.submit({}, noOpDrop)) }

    block.countDown()
    closing.join(15000)
    assertTrue(
      durationNanos < TimeUnit.SECONDS.toNanos(5),
      "submit blocked for ${TimeUnit.NANOSECONDS.toMillis(durationNanos)}ms during close",
    )
  }

  @Test
  fun `waitTillIdle returns once accepted work finishes`() {
    val sut = getSut()
    val block = CountDownLatch(1)
    val finished = AtomicInteger()
    sut.submit(
      {
        block.await(5, TimeUnit.SECONDS)
        finished.incrementAndGet()
      },
      noOpDrop,
    )

    block.countDown()
    sut.waitTillIdle(5000)

    assertEquals(1, finished.get())
    sut.close(5000)
  }

  private fun awaitThreadState(thread: Thread, state: Thread.State) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (thread.state != state && System.nanoTime() < deadline) {
      Thread.sleep(1)
    }
    assertEquals(state, thread.state)
  }
}
