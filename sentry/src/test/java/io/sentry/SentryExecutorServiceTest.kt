package io.sentry

import io.sentry.test.getProperty
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.awaitility.kotlin.await
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SentryExecutorServiceTest {
  @Test
  fun `SentryExecutorService forwards submit call to ExecutorService`() {
    val executor = mock<ScheduledThreadPoolExecutor> { on { queue } doReturn LinkedBlockingQueue() }
    val sentryExecutor = SentryExecutorService(executor, null)
    sentryExecutor.submit {}
    verify(executor).submit(any())
  }

  @Test
  fun `SentryExecutorService forwards schedule call to ExecutorService`() {
    val executor = mock<ScheduledThreadPoolExecutor> { on { queue } doReturn LinkedBlockingQueue() }
    val sentryExecutor = SentryExecutorService(executor, null)
    sentryExecutor.schedule({}, 0L)
    verify(executor).schedule(any(), any(), any())
  }

  @Test
  fun `SentryExecutorService forwards close call to ExecutorService`() {
    val executor = mock<ScheduledThreadPoolExecutor>()
    val sentryExecutor = SentryExecutorService(executor, null)
    whenever(executor.isShutdown).thenReturn(false)
    whenever(executor.awaitTermination(any(), any())).thenReturn(true)
    sentryExecutor.close(15000)
    verify(executor).shutdown()
  }

  @Test
  fun `SentryExecutorService forwards close and call shutdownNow if not enough time`() {
    val executor = mock<ScheduledThreadPoolExecutor>()
    val sentryExecutor = SentryExecutorService(executor, null)
    whenever(executor.isShutdown).thenReturn(false)
    whenever(executor.awaitTermination(any(), any())).thenReturn(false)
    sentryExecutor.close(15000)
    verify(executor).shutdownNow()
  }

  @Test
  fun `SentryExecutorService forwards close and call shutdownNow if await throws`() {
    val executor = mock<ScheduledThreadPoolExecutor>()
    val sentryExecutor = SentryExecutorService(executor, null)
    whenever(executor.isShutdown).thenReturn(false)
    whenever(executor.awaitTermination(any(), any())).thenThrow(InterruptedException())
    sentryExecutor.close(15000)
    verify(executor).shutdownNow()
  }

  @Test
  fun `SentryExecutorService forwards close but do not shutdown if its already closed`() {
    val executor = mock<ScheduledThreadPoolExecutor>()
    val sentryExecutor = SentryExecutorService(executor, null)
    whenever(executor.isShutdown).thenReturn(true)
    sentryExecutor.close(15000)
    verify(executor, never()).shutdown()
  }

  @Test
  fun `SentryExecutorService forwards close call to ExecutorService and close it`() {
    val executor = ScheduledThreadPoolExecutor(1)
    val sentryExecutor = SentryExecutorService(executor, null)
    sentryExecutor.close(15000)
    assertTrue(executor.isShutdown)
  }

  @Test
  fun `SentryExecutorService executes runnable`() {
    val sentryExecutor = SentryExecutorService()
    val atomicBoolean = AtomicBoolean(true)
    sentryExecutor.submit { atomicBoolean.set(false) }
    await.untilFalse(atomicBoolean)
    sentryExecutor.close(15000)
  }

  @Test
  fun `SentryExecutorService isClosed returns true if executor is shutdown`() {
    val executor = mock<ScheduledThreadPoolExecutor>()
    val sentryExecutor = SentryExecutorService(executor, null)
    whenever(executor.isShutdown).thenReturn(true)
    assertTrue(sentryExecutor.isClosed)
  }

  @Test
  fun `SentryExecutorService isClosed returns false if executor is not shutdown`() {
    val executor = mock<ScheduledThreadPoolExecutor>()
    val sentryExecutor = SentryExecutorService(executor, null)
    whenever(executor.isShutdown).thenReturn(false)
    assertFalse(sentryExecutor.isClosed)
  }

  @Test
  fun `SentryExecutorService submit runnable returns cancelled future when queue size exceeds limit`() {
    val queue = mock<BlockingQueue<Runnable>>()
    whenever(queue.size).thenReturn(272) // Above MAX_QUEUE_SIZE (271)

    val executor = mock<ScheduledThreadPoolExecutor> { on { getQueue() } doReturn queue }

    val options = mock<SentryOptions>()
    val logger = mock<ILogger>()
    whenever(options.logger).thenReturn(logger)

    val sentryExecutor = SentryExecutorService(executor, options)
    val future = sentryExecutor.submit {}

    assertTrue(future.isCancelled)
    assertTrue(future.isDone)
    assertFailsWith<CancellationException> { future.get() }
    verify(executor, never()).submit(any<Runnable>())
    verify(logger).log(any<SentryLevel>(), any<String>())
  }

  @Test
  fun `SentryExecutorService submit runnable accepts when queue size is within limit`() {
    val queue = mock<BlockingQueue<Runnable>>()
    whenever(queue.size).thenReturn(270) // Below MAX_QUEUE_SIZE (271)

    val executor = mock<ScheduledThreadPoolExecutor> { on { getQueue() } doReturn queue }

    val sentryExecutor = SentryExecutorService(executor, null)
    sentryExecutor.submit {}

    verify(executor).submit(any<Runnable>())
  }

  @Test
  fun `SentryExecutorService submit callable returns cancelled future when queue size exceeds limit`() {
    val queue = mock<BlockingQueue<Runnable>>()
    whenever(queue.size).thenReturn(272) // Above MAX_QUEUE_SIZE (271)

    val executor = mock<ScheduledThreadPoolExecutor> { on { getQueue() } doReturn queue }

    val options = mock<SentryOptions>()
    val logger = mock<ILogger>()
    whenever(options.logger).thenReturn(logger)

    val sentryExecutor = SentryExecutorService(executor, options)
    val future = sentryExecutor.submit(Callable { "result" })

    assertTrue(future.isCancelled)
    assertTrue(future.isDone)
    assertFailsWith<CancellationException> { future.get() }
    verify(executor, never()).submit(any<Callable<String>>())
    verify(logger).log(any<SentryLevel>(), any<String>())
  }

  @Test
  fun `SentryExecutorService submit callable accepts when queue size is within limit`() {
    val queue = mock<BlockingQueue<Runnable>>()
    whenever(queue.size).thenReturn(270) // Below MAX_QUEUE_SIZE (271)

    val executor = mock<ScheduledThreadPoolExecutor> { on { getQueue() } doReturn queue }

    val sentryExecutor = SentryExecutorService(executor, null)
    sentryExecutor.submit(Callable { "result" })

    verify(executor).submit(any<Callable<String>>())
  }

  @Test
  fun `SentryExecutorService schedule accepts when queue size is within limit`() {
    val queue = mock<BlockingQueue<Runnable>>()
    whenever(queue.size).thenReturn(270) // Below MAX_QUEUE_SIZE (271)

    val executor = mock<ScheduledThreadPoolExecutor> { on { getQueue() } doReturn queue }

    val sentryExecutor = SentryExecutorService(executor, null)
    sentryExecutor.schedule({}, 1000L)

    verify(executor).schedule(any<Runnable>(), any(), any())
  }

  @Test
  fun `SentryExecutorService prewarm schedules dummy tasks and clears queue`() {
    val executor = ScheduledThreadPoolExecutor(1)

    val sentryExecutor = SentryExecutorService(executor, null)
    sentryExecutor.prewarm()

    Thread.sleep(1000)

    // the internal queue/array should be resized 4 times to 54
    assertEquals(54, (executor.queue.getProperty("queue") as Array<*>).size)
    // the queue should be empty
    assertEquals(0, executor.queue.size)
  }

  @Test
  fun `SentryExecutorService schedules any number of job`() {
    val executor = ScheduledThreadPoolExecutor(1)
    val sentryExecutor = SentryExecutorService(executor, null)
    // Post 1k jobs after 1 day, to test they are all accepted
    repeat(1000) { sentryExecutor.schedule({}, TimeUnit.DAYS.toMillis(1)) }
    assertEquals(1000, executor.queue.size)
  }
}
