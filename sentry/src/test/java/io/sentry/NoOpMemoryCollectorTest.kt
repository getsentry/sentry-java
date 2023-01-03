package io.sentry

import kotlin.test.Test
import kotlin.test.assertNull

class NoOpMemoryCollectorTest {
    private var collector = NoOpMemoryCollector.getInstance()

    @Test
    fun `collect returns null`() {
        assertNull(collector.collect())
    }
}
