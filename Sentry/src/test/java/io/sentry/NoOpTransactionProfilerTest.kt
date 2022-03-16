package io.sentry

import kotlin.test.Test
import kotlin.test.assertNull

class NoOpTransactionProfilerTest {
    private var profiler: NoOpTransactionProfiler = NoOpTransactionProfiler.getInstance()

    @Test
    fun `onTransactionStart is no op`() = profiler.onTransactionStart(NoOpTransaction.getInstance())

    @Test
    fun `onTransactionFinish returns null`() =
        assertNull(profiler.onTransactionFinish(NoOpTransaction.getInstance()))
}
