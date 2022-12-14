package io.sentry

import kotlin.test.Test

class NoOpTransactionProfilerTest {
    private var profiler = NoOpTransactionProfiler.getInstance()

    @Test
    fun `onTransactionStart does not throw`() =
        profiler.onTransactionStart(NoOpTransaction.getInstance())

    @Test
    fun `onTransactionFinish does not throw`() =
        profiler.onTransactionFinish(NoOpTransaction.getInstance())
}
