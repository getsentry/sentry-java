package io.sentry.android.core

import io.sentry.ILogger
import io.sentry.PerformanceCollectionData
import io.sentry.test.getCtor
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.mockito.kotlin.mock

class AndroidCpuCollectorTest {
  private val className = "io.sentry.android.core.AndroidCpuCollector"
  private val ctorTypes = arrayOf<Class<*>>(ILogger::class.java)
  private val fixture = Fixture()

  private class Fixture {
    fun getSut() = AndroidCpuCollector(mock())
  }

  @Test
  fun `when null param is provided, invalid argument is thrown`() {
    val ctor = className.getCtor(ctorTypes)

    assertFailsWith<IllegalArgumentException> { ctor.newInstance(arrayOf(mock<ILogger>())) }
  }

  @Test
  fun `collect works only after setup`() {
    val data = PerformanceCollectionData(10)
    fixture.getSut().collect(data)
    assertNull(data.cpuUsagePercentage)
  }

  @Test
  fun `when collect cpu is collected`() {
    val data = PerformanceCollectionData(10)
    val collector = fixture.getSut()
    collector.setup()
    collector.collect(data)
    val cpuData = data.cpuUsagePercentage
    assertNotNull(cpuData)
    assertNotEquals(0.0, cpuData)
  }
}
