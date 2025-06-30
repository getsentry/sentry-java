package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JavaMemoryCollectorTest {
  private val fixture = Fixture()

  private class Fixture {
    val runtime: Runtime = Runtime.getRuntime()
    val collector = JavaMemoryCollector()
  }

  @Test
  fun `when collect, only heap memory is collected`() {
    val data = PerformanceCollectionData(10)
    val usedMemory = fixture.runtime.totalMemory() - fixture.runtime.freeMemory()
    fixture.collector.collect(data)
    assertNull(data.usedNativeMemory)
    assertEquals(usedMemory, data.usedHeapMemory)
    assertEquals(10, data.nanoTimestamp)
  }
}
