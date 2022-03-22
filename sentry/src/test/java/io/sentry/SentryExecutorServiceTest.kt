package io.sentry

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.awaitility.kotlin.await
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertTrue

class SentryExecutorServiceTest {

    @Test
    fun `SentryExecutorService forwards submit call to ExecutorService`() {
        val executor = mock<ScheduledExecutorService>()
        val sentryExecutor = SentryExecutorService(executor)
        sentryExecutor.submit {}
        verify(executor).submit(any())
    }

    @Test
    fun `SentryExecutorService forwards schedule call to ExecutorService`() {
        val executor = mock<ScheduledExecutorService>()
        val sentryExecutor = SentryExecutorService(executor)
        sentryExecutor.schedule({}, 0L)
        verify(executor).schedule(any(), any(), any())
    }

    @Test
    fun `SentryExecutorService forwards close call to ExecutorService`() {
        val executor = mock<ScheduledExecutorService>()
        val sentryExecutor = SentryExecutorService(executor)
        whenever(executor.isShutdown).thenReturn(false)
        whenever(executor.awaitTermination(any(), any())).thenReturn(true)
        sentryExecutor.close(15000)
        verify(executor).shutdown()
    }

    @Test
    fun `SentryExecutorService forwards close and call shutdownNow if not enough time`() {
        val executor = mock<ScheduledExecutorService>()
        val sentryExecutor = SentryExecutorService(executor)
        whenever(executor.isShutdown).thenReturn(false)
        whenever(executor.awaitTermination(any(), any())).thenReturn(false)
        sentryExecutor.close(15000)
        verify(executor).shutdownNow()
    }

    @Test
    fun `SentryExecutorService forwards close and call shutdownNow if await throws`() {
        val executor = mock<ScheduledExecutorService>()
        val sentryExecutor = SentryExecutorService(executor)
        whenever(executor.isShutdown).thenReturn(false)
        whenever(executor.awaitTermination(any(), any())).thenThrow(InterruptedException())
        sentryExecutor.close(15000)
        verify(executor).shutdownNow()
    }

    @Test
    fun `SentryExecutorService forwards close but do not shutdown if its already closed`() {
        val executor = mock<ScheduledExecutorService>()
        val sentryExecutor = SentryExecutorService(executor)
        whenever(executor.isShutdown).thenReturn(true)
        sentryExecutor.close(15000)
        verify(executor, never()).shutdown()
    }

    @Test
    fun `SentryExecutorService forwards close call to ExecutorService and close it`() {
        val executor = Executors.newSingleThreadScheduledExecutor()
        val sentryExecutor = SentryExecutorService(executor)
        sentryExecutor.close(15000)
        assertTrue(executor.isShutdown)
    }

    @Test
    fun `SentryExecutorService executes runnable`() {
        val sentryExecutor = SentryExecutorService()
        val atomicBoolean = AtomicBoolean(true)
        sentryExecutor.submit {
            atomicBoolean.set(false)
        }
        await.untilFalse(atomicBoolean)
        sentryExecutor.close(15000)
    }
}
