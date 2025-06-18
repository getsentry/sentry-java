package io.sentry.backpressure

import io.sentry.IScopes
import io.sentry.ISentryExecutorService
import io.sentry.SentryOptions
import io.sentry.backpressure.BackpressureMonitor.MAX_DOWNSAMPLE_FACTOR
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.Future
import kotlin.test.Test
import kotlin.test.assertEquals

class BackpressureMonitorTest {
    class Fixture {
        val options = SentryOptions()
        val scopes = mock<IScopes>()
        val executor = mock<ISentryExecutorService>()
        val returnedFuture = mock<Future<Any>>()

        fun getSut(): BackpressureMonitor {
            options.executorService = executor
            whenever(executor.isClosed).thenReturn(false)
            whenever(executor.schedule(any(), any())).thenReturn(returnedFuture)
            whenever(returnedFuture.cancel(any())).thenReturn(true)
            return BackpressureMonitor(options, scopes)
        }
    }

    val fixture = Fixture()

    @Test
    fun `starts off with downsampleFactor 0`() {
        val sut = fixture.getSut()
        assertEquals(0, sut.downsampleFactor)
    }

    @Test
    fun `downsampleFactor increases with negative health checks up to max`() {
        val sut = fixture.getSut()
        whenever(fixture.scopes.isHealthy).thenReturn(false)
        assertEquals(0, sut.downsampleFactor)

        (1..MAX_DOWNSAMPLE_FACTOR).forEach { i ->
            sut.checkHealth()
            assertEquals(i, sut.downsampleFactor)
        }

        assertEquals(MAX_DOWNSAMPLE_FACTOR, sut.downsampleFactor)
        sut.checkHealth()
        assertEquals(MAX_DOWNSAMPLE_FACTOR, sut.downsampleFactor)
    }

    @Test
    fun `downsampleFactor goes back to 0 after positive health check`() {
        val sut = fixture.getSut()
        whenever(fixture.scopes.isHealthy).thenReturn(false)
        assertEquals(0, sut.downsampleFactor)

        sut.checkHealth()
        assertEquals(1, sut.downsampleFactor)

        whenever(fixture.scopes.isHealthy).thenReturn(true)
        sut.checkHealth()
        assertEquals(0, sut.downsampleFactor)
    }

    @Test
    fun `schedules on start`() {
        val sut = fixture.getSut()
        sut.start()

        verify(fixture.executor).schedule(any(), any())
    }

    @Test
    fun `reschedules on run`() {
        val sut = fixture.getSut()
        sut.run()

        verify(fixture.executor).schedule(any(), any())
    }

    @Test
    fun `close cancels latest job`() {
        val sut = fixture.getSut()
        sut.run()

        verify(fixture.executor).schedule(any(), any())
        verify(fixture.returnedFuture, never()).cancel(any())

        sut.close()

        verify(fixture.returnedFuture).cancel(eq(true))
    }
}
