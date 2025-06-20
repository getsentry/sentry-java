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
    val data = PerformanceCollectionData()
    val usedNativeMemory = Debug.getNativeHeapSize() - Debug.getNativeHeapFreeSize()
    val usedMemory = fixture.runtime.totalMemory() - fixture.runtime.freeMemory()
    fixture.collector.collect(data)
    val memoryData = data.memoryData
    assertNotNull(memoryData)
    assertNotEquals(-1, memoryData.usedNativeMemory)
    assertEquals(usedNativeMemory, memoryData.usedNativeMemory)
    assertEquals(usedMemory, memoryData.usedHeapMemory)
    assertNotEquals(0, memoryData.timestamp.nanoTimestamp())
  }
}
