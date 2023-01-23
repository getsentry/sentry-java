package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class JavaMemoryCollectorTest {

    private val fixture = Fixture()

    private class Fixture {
        val runtime: Runtime = Runtime.getRuntime()
        val collector = JavaMemoryCollector()
    }

    @Test
    fun `when collect, only heap memory is collected`() {
        val performanceCollectionData = PerformanceCollectionData()
        val data = listOf(performanceCollectionData)
        val usedMemory = fixture.runtime.totalMemory() - fixture.runtime.freeMemory()
        fixture.collector.collect(data)
        performanceCollectionData.commitData()
        val memoryData = performanceCollectionData.memoryData
        assertFalse(memoryData.isEmpty())
        assertEquals(-1, memoryData.first().usedNativeMemory)
        assertEquals(usedMemory, memoryData.first().usedHeapMemory)
        assertNotEquals(0, memoryData.first().timestampMillis)
    }
}
