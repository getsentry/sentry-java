package io.sentry

import com.nhaarman.mockitokotlin2.anyOrNull
import kotlin.test.Test
import kotlin.test.assertNull

class NoOpTransactionProfilerTest {
    private var profiler: NoOpTransactionProfiler = NoOpTransactionProfiler.getInstance()

    @Test
    fun `onTransactionStart is no op`() = profiler.onTransactionStart(anyOrNull())

    @Test
    fun `onTransactionFinish returns null`() =
        assertNull(profiler.onTransactionFinish(anyOrNull()))
}
