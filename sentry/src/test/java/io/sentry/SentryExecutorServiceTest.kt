package io.sentry

import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.awaitility.kotlin.await
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SentryExecutorServiceTest {

  // region submit(Runnable)

  @Test
  fun `executes submitted runnable`() {
    val executor = SentryExecutorService()
    val executed = AtomicBoolean(false)
    executor.submit { executed.set(true) }
    await.untilTrue(executed)
    executor.close(15_000)
  }

  @Test
  fun `submit runnable returns non-cancelled future`() {
    val executor = SentryExecutorService()
    val future = executor.submit {}
    future.get(5, TimeUnit.SECONDS)
    assertFalse(future.isCancelled)
    executor.close(15_000)
  }

  // endregion

  // region submit(Callable)

  @Test
  fun `executes submitted callable and returns result`() {
    val executor = SentryExecutorService()
    val future = executor.submit(Callable { 42 })
    assertTrue(future.get(5, TimeUnit.SECONDS) == 42)
    executor.close(15_000)
  }

  // endregion

  // region schedule

  @Test
  fun `executes scheduled runnable after delay`() {
    val executor = SentryExecutorService()
    val executed = AtomicBoolean(false)
    val startMs = System.currentTimeMillis()
    executor.schedule({ executed.set(true) }, 200L)
    await.untilTrue(executed)
    val elapsedMs = System.currentTimeMillis() - startMs
    assertTrue(elapsedMs >= 150L, "Expected >= 150ms delay, got ${elapsedMs}ms")
    executor.close(15_000)
  }

  @Test
  fun `schedule returns non-cancelled future`() {
    val executor = SentryExecutorService()
    val future = executor.schedule({}, 0L)
    future.get(5, TimeUnit.SECONDS)
    assertFalse(future.isCancelled)
    executor.close(15_000)
  }

  // endregion

  // region close / isClosed

  @Test
  fun `isClosed returns false before close`() {
    val executor = SentryExecutorService()
    assertFalse(executor.isClosed)
    executor.close(15_000)
  }

  @Test
  fun `isClosed returns true after close`() {
    val executor = SentryExecutorService()
    executor.close(15_000)
    assertTrue(executor.isClosed)
  }

  @Test
  fun `close is idempotent`() {
    val executor = SentryExecutorService()
    executor.close(15_000)
    executor.close(15_000) // second call must not throw
    assertTrue(executor.isClosed)
  }

  @Test
  fun `close waits for in-flight task to complete`() {
    val executor = SentryExecutorService()
    val started = AtomicBoolean(false)
    val completed = AtomicBoolean(false)
    executor.submit {
      started.set(true)
      Thread.sleep(200)
      completed.set(true)
    }
    await.untilTrue(started)
    executor.close(15_000)
    assertTrue(completed.get())
  }

  @Test
  fun `submit after close returns cancelled future`() {
    val executor = SentryExecutorService()
    executor.close(15_000)
    val future = executor.submit {}
    assertTrue(future.isCancelled)
    assertTrue(future.isDone)
    assertFailsWith<CancellationException> { future.get() }
  }

  @Test
  fun `schedule after close returns cancelled future`() {
    val executor = SentryExecutorService()
    executor.close(15_000)
    val future = executor.schedule({}, 1_000L)
    assertTrue(future.isCancelled)
    assertTrue(future.isDone)
    assertFailsWith<CancellationException> { future.get() }
  }

  // endregion

  // region queue limit

  @Test
  fun `submit runnable returns cancelled future when queue exceeds limit`() {
    val options = mock<SentryOptions>()
    val logger = mock<ILogger>()
    whenever(options.logger).thenReturn(logger)

    val executor = SentryExecutorService(options)
    // Fill queue past MAX_QUEUE_SIZE with far-future tasks
    repeat(272) { executor.schedule({}, TimeUnit.DAYS.toMillis(1)) }

    val future = executor.submit {}
    assertTrue(future.isCancelled)
    assertTrue(future.isDone)
    assertFailsWith<CancellationException> { future.get() }
    verify(logger).log(any<SentryLevel>(), any<String>())
    executor.close(100)
  }

  @Test
  fun `submit callable returns cancelled future when queue exceeds limit`() {
    val options = mock<SentryOptions>()
    val logger = mock<ILogger>()
    whenever(options.logger).thenReturn(logger)

    val executor = SentryExecutorService(options)
    repeat(272) { executor.schedule({}, TimeUnit.DAYS.toMillis(1)) }

    val future = executor.submit(Callable { "result" })
    assertTrue(future.isCancelled)
    verify(logger).log(any<SentryLevel>(), any<String>())
    executor.close(100)
  }

  @Test
  fun `submit purges cancelled tasks when queue limit is reached`() {
    val executor = SentryExecutorService()
    // Fill and cancel all
    val futures = (1..272).map { executor.schedule({}, TimeUnit.DAYS.toMillis(1)) }
    futures.forEach { it.cancel(true) }

    // Next submit should succeed after purge
    val executed = AtomicBoolean(false)
    val future = executor.submit { executed.set(true) }
    assertFalse(future.isCancelled)
    await.untilTrue(executed)
    executor.close(15_000)
  }

  @Test
  fun `submit logs nothing when queue size is within limit`() {
    val options = mock<SentryOptions>()
    val logger = mock<ILogger>()
    whenever(options.logger).thenReturn(logger)

    val executor = SentryExecutorService(options)
    executor.submit {}
    verify(logger, never()).log(any<SentryLevel>(), any<String>())
    executor.close(15_000)
  }

  // endregion

  // region ordering

  @Test
  fun `tasks run in trigger-time order`() {
    val executor = SentryExecutorService()
    val order = mutableListOf<Int>()
    val latch = CountDownLatch(3)
    // Schedule out of order; single worker ensures serialised execution.
    executor.schedule(
      {
        synchronized(order) { order.add(3) }
        latch.countDown()
      },
      300L,
    )
    executor.schedule(
      {
        synchronized(order) { order.add(1) }
        latch.countDown()
      },
      100L,
    )
    executor.schedule(
      {
        synchronized(order) { order.add(2) }
        latch.countDown()
      },
      200L,
    )
    latch.await(10, TimeUnit.SECONDS)
    assertTrue(order == listOf(1, 2, 3), "Expected [1,2,3] but got $order")
    executor.close(15_000)
  }

  // endregion

  // region prewarm

  @Test
  fun `prewarm is a no-op`() {
    val executor = SentryExecutorService()
    executor.prewarm() // must not throw or block
    executor.close(15_000)
  }

  // endregion

  // region initial capacity

  @Test
  fun `initial queue capacity constant is in expected range`() {
    assertTrue(
      SentryExecutorService.INITIAL_QUEUE_CAPACITY >= 32,
      "INITIAL_QUEUE_CAPACITY should be at least 32",
    )
    assertTrue(
      SentryExecutorService.INITIAL_QUEUE_CAPACITY <= 128,
      "INITIAL_QUEUE_CAPACITY should be at most 128",
    )
  }

  // endregion
}
