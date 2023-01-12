package io.sentry

import kotlin.test.Test
import kotlin.test.assertNull

class NoOpCpuCollectorTest {
    private var collector = NoOpCpuCollector.getInstance()

    @Test
    fun `setup does not throw`() =
        collector.setup()

    @Test
    fun `collect returns null`() {
        assertNull(collector.collect())
    }
}
