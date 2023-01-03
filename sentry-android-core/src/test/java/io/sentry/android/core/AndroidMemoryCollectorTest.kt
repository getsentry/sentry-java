package io.sentry.android.core

import android.os.Debug
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
        val usedNativeMemory = Debug.getNativeHeapSize() - Debug.getNativeHeapFreeSize()
        val usedMemory = fixture.runtime.totalMemory() - fixture.runtime.freeMemory()
        val data = fixture.collector.collect()
        assertNotNull(data)
        assertNotEquals(-1, data.usedNativeMemory)
        assertEquals(usedNativeMemory, data.usedNativeMemory)
        assertEquals(usedMemory, data.usedHeapMemory)
        assertNotEquals(0, data.timestamp)
    }
}
