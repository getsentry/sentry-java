package io.sentry.android.core

import android.os.Debug
import com.google.common.truth.Truth.assertThat
import io.sentry.PerformanceCollectionData
import kotlin.test.Test

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
    assertThat(data.hasUsedHeapMemory()).isTrue()
    assertThat(data.hasUsedNativeMemory()).isTrue()
    assertThat(data.usedNativeMemory).isEqualTo(usedNativeMemory)
    assertThat(data.usedHeapMemory).isEqualTo(usedMemory)
  }
}
