package io.sentry

import kotlin.test.Test
import kotlin.test.assertFalse

class NoOpContinuousProfilerTest {
    private var profiler = NoOpContinuousProfiler.getInstance()

    @Test
    fun `start does not throw`() =
        profiler.start()

    @Test
    fun `stop does not throw`() =
        profiler.stop()

    @Test
    fun `isRunning returns false`() {
        assertFalse(profiler.isRunning)
    }

    @Test
    fun `close does not throw`() =
        profiler.close()
}
