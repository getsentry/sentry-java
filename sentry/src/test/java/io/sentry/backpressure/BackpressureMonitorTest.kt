package io.sentry.backpressure

import io.sentry.IHub
import io.sentry.ISentryExecutorService
import io.sentry.SentryOptions
import io.sentry.backpressure.BackpressureMonitor.MAX_DOWNSAMPLE_FACTOR
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.Future
import kotlin.test.Test
import kotlin.test.assertEquals

class BackpressureMonitorTest {

    class Fixture {

        val options = SentryOptions()
        val hub = mock<IHub>()
        val executor = mock<ISentryExecutorService>()
        fun getSut(): BackpressureMonitor {
            options.executorService = executor
            whenever(executor.isClosed).thenReturn(false)
            whenever(executor.schedule(any(), any())).thenReturn(mock<Future<Any>>())
            return BackpressureMonitor(options, hub)
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
        whenever(fixture.hub.isHealthy).thenReturn(false)
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
        whenever(fixture.hub.isHealthy).thenReturn(false)
        assertEquals(0, sut.downsampleFactor)

        sut.checkHealth()
        assertEquals(1, sut.downsampleFactor)

        whenever(fixture.hub.isHealthy).thenReturn(true)
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
}
