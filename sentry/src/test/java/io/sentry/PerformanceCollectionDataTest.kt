package io.sentry

import org.mockito.kotlin.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PerformanceCollectionDataTest {

    private val fixture = Fixture()

    private class Fixture {
        fun getSut() = PerformanceCollectionData()
    }

    @Test
    fun `memory data is saved only after commitData`() {
        val data = fixture.getSut()
        data.addMemoryData(mock())
        assertTrue(data.memoryData.isEmpty())
        data.commitData()
        assertFalse(data.memoryData.isEmpty())
    }

    @Test
    fun `cpu data is saved only after commitData`() {
        val data = fixture.getSut()
        data.addCpuData(mock())
        assertTrue(data.cpuData.isEmpty())
        data.commitData()
        assertFalse(data.cpuData.isEmpty())
    }

    @Test
    fun `only the last of multiple memory data is saved on commit`() {
        val data = fixture.getSut()
        val memData1 = MemoryCollectionData(0, 0, 0)
        val memData2 = MemoryCollectionData(1, 1, 1)
        data.addMemoryData(memData1)
        data.addMemoryData(memData2)
        data.commitData()
        val savedMemoryData = data.memoryData.first()
        assertNotEquals(memData1, savedMemoryData)
        assertEquals(memData2, savedMemoryData)
    }

    @Test
    fun `only the last of multiple cpu data is saved on commit`() {
        val data = fixture.getSut()
        val cpuData1 = CpuCollectionData(0, 0.0)
        val cpuData2 = CpuCollectionData(1, 1.0)
        data.addCpuData(cpuData1)
        data.addCpuData(cpuData2)
        data.commitData()
        val savedCpuData = data.cpuData.first()
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
        data.commitData()
        assertEquals(1, data.cpuData.size)
        assertTrue(data.memoryData.isEmpty())
        val savedCpuData = data.cpuData.first()
        assertEquals(cpuData1, savedCpuData)
    }

    @Test
    fun `committing multiple times does not duplicate values`() {
        val data = fixture.getSut()
        data.addCpuData(mock())
        data.addMemoryData(mock())
        data.commitData()
        assertEquals(1, data.cpuData.size)
        assertEquals(1, data.memoryData.size)
        data.commitData()
        assertEquals(1, data.cpuData.size)
        assertEquals(1, data.memoryData.size)
    }
}
