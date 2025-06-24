package io.sentry.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

class GpuTest {
  @Test
  fun `copying gpu wont have the same references`() {
    val gpu = Gpu()
    val unknown = mapOf(Pair("unknown", "unknown"))
    gpu.setUnknown(unknown)

    val clone = Gpu(gpu)

    assertNotNull(clone)
    assertNotSame(gpu, clone)

    assertNotSame(gpu.unknown, clone.unknown)
  }

  @Test
  fun `copying gpu will have the same values`() {
    val gpu = Gpu()
    gpu.name = "name"
    gpu.id = 10
    gpu.vendorId = "20"
    gpu.vendorName = "vendor name"
    gpu.memorySize = 1024
    gpu.apiType = "api type"
    gpu.isMultiThreadedRendering = true
    gpu.version = "version"
    gpu.npotSupport = "npot support"
    val unknown = mapOf(Pair("unknown", "unknown"))
    gpu.setUnknown(unknown)

    val clone = Gpu(gpu)

    assertEquals("name", clone.name)
    assertEquals(10, clone.id)
    assertEquals("20", clone.vendorId)
    assertEquals("vendor name", clone.vendorName)
    assertEquals(1024, clone.memorySize)
    assertEquals("api type", clone.apiType)
    assertEquals(true, clone.isMultiThreadedRendering)
    assertEquals("version", clone.version)
    assertEquals("npot support", clone.npotSupport)
    assertNotNull(clone.unknown) { assertEquals("unknown", it["unknown"]) }
  }
}
