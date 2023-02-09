package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class PerformanceCollectionDataTest {

    private val fixture = Fixture()

    private class Fixture {
        fun getSut() = PerformanceCollectionData()
    }

    @Test
    fun `only the last of multiple memory data is saved`() {
        val data = fixture.getSut()
        val memData1 = MemoryCollectionData(0, 0, 0)
        val memData2 = MemoryCollectionData(1, 1, 1)
        data.addMemoryData(memData1)
        data.addMemoryData(memData2)
        val savedMemoryData = data.memoryData
        assertNotEquals(memData1, savedMemoryData)
        assertEquals(memData2, savedMemoryData)
    }

    @Test
    fun `only the last of multiple cpu data is saved`() {
        val data = fixture.getSut()
        val cpuData1 = CpuCollectionData(0, 0.0)
        val cpuData2 = CpuCollectionData(1, 1.0)
        data.addCpuData(cpuData1)
        data.addCpuData(cpuData2)
        val savedCpuData = data.cpuData
        assertNotEquals(cpuData1, savedCpuData)
        assertEquals(cpuData2, savedCpuData)
    }

    @Test
    fun `null values are ignored`() {
        val data = fixture.getSut()
        val cpuData1 = CpuCollectionData(0, 0.0)
        data.addCpuData(cpuData1)
        data.addCpuData(null)
        data.addMemoryData(null)
        assertNull(data.memoryData)
        val savedCpuData = data.cpuData
        assertEquals(cpuData1, savedCpuData)
    }
}
