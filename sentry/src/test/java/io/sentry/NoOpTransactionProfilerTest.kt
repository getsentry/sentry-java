package io.sentry

import org.mockito.kotlin.mock
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull

class NoOpTransactionProfilerTest {
    private var profiler = NoOpTransactionProfiler.getInstance()

    @Test
    fun `start does not throw`() = profiler.start()

    @Test
    fun `bindTransaction does not throw`() = profiler.bindTransaction(mock())

    @Test
    fun `isRunning returns false`() {
        assertFalse(profiler.isRunning)
    }

    @Test
    fun `onTransactionFinish does returns null`() {
        assertNull(profiler.onTransactionFinish(NoOpTransaction.getInstance(), null, mock()))
    }

    @Test
    fun `close does not throw`() = profiler.close()
}
