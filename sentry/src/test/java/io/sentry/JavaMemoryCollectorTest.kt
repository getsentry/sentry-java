package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class JavaMemoryCollectorTest {

    private val fixture = Fixture()

    private class Fixture {
        val runtime: Runtime = Runtime.getRuntime()
        val collector = JavaMemoryCollector()
    }

    @Test
    fun `when collect, only heap memory is collected`() {
        val usedMemory = fixture.runtime.totalMemory() - fixture.runtime.freeMemory()
        val data = fixture.collector.collect()
        assertNotNull(data)
        assertEquals(-1, data.usedNativeMemory)
        assertEquals(usedMemory, data.usedHeapMemory)
        assertNotEquals(0, data.timestampMillis)
    }
}
