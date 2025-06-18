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
        val data = PerformanceCollectionData()
        val usedMemory = fixture.runtime.totalMemory() - fixture.runtime.freeMemory()
        fixture.collector.collect(data)
        val memoryData = data.memoryData
        assertNotNull(memoryData)
        assertEquals(-1, memoryData.usedNativeMemory)
        assertEquals(usedMemory, memoryData.usedHeapMemory)
        assertNotEquals(0, memoryData.timestamp.nanoTimestamp())
    }
}
