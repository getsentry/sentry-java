package io.sentry

import kotlin.test.Test
import kotlin.test.assertNull

class NoOpTransactionProfilerTest {
    private var profiler = NoOpTransactionProfiler.getInstance()

    @Test
    fun `onTransactionStart does not throw`() =
        profiler.onTransactionStart(NoOpTransaction.getInstance())

    @Test
    fun `onTransactionFinish does returns null`() {
        assertNull(profiler.onTransactionFinish(NoOpTransaction.getInstance()))
    }
}
