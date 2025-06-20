package io.sentry.android.core

import android.os.Debug
import io.sentry.PerformanceCollectionData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class AndroidMemoryCollectorTest {

    private val fixture = Fixture()

    private class Fixture {
        val runtime: Runtime = Runtime.getRuntime()
        val collector = AndroidMemoryCollector()
    }

    @Test
    fun `when collect, both native and heap memory are collected`() {
        val data = PerformanceCollectionData(10)
        val usedNativeMemory = Debug.getNativeHeapSize() - Debug.getNativeHeapFreeSize()
        val usedMemory = fixture.runtime.totalMemory() - fixture.runtime.freeMemory()
        fixture.collector.collect(data)
        assertNotNull(data.usedHeapMemory)
        assertNotNull(data.usedNativeMemory)
        assertNotEquals(-1, data.usedNativeMemory)
        assertEquals(usedNativeMemory, data.usedNativeMemory)
        assertEquals(usedMemory, data.usedHeapMemory)
    }
}
