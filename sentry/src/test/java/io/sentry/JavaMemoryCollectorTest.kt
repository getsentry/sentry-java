package io.sentry

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

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
    assertThat(data.hasUsedNativeMemory()).isFalse()
    assertThat(data.hasUsedHeapMemory()).isTrue()
    assertThat(data.usedHeapMemory).isEqualTo(usedMemory)
    assertThat(data.nanoTimestamp).isEqualTo(10)
  }
}
