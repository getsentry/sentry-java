package io.sentry.protocol

import java.util.Date
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

class DeviceTest {
  @Test
  fun `copying device wont have the same references`() {
    val device = Device()
    device.archs = arrayOf("archs1", "archs2")
    device.bootTime = Date()
    device.timezone = TimeZone.getDefault()
    val unknown = mapOf(Pair("unknown", "unknown"))
    device.setUnknown(unknown)

    val clone = Device(device)

    assertNotNull(clone)
    assertNotSame(device, clone)
    assertNotSame(device.archs, clone.archs)
    assertNotSame(device.bootTime, clone.bootTime)
    assertNotSame(device.timezone, clone.timezone)
    assertNotSame(device.unknown, clone.unknown)
  }

  @Test
  fun `copying device will have the same values`() {
    val device = Device()
    device.name = "name"
    device.manufacturer = "manufacturer"
    device.brand = "brand"
    device.family = "family"
    device.model = "model"
    device.modelId = "modelId"
    device.archs = arrayOf("archs1", "archs2")
    device.batteryLevel = 3.14f
    device.isCharging = true
    device.isOnline = true
    device.orientation = Device.DeviceOrientation.LANDSCAPE
    device.isSimulator = true
    device.memorySize = 10
    device.freeMemory = 5
    device.usableMemory = 2
    device.isLowMemory = true
    device.storageSize = 1024
    device.freeStorage = 512
    device.externalStorageSize = 768
    device.externalFreeStorage = 384
    device.screenWidthPixels = 1024
    device.screenHeightPixels = 768
    device.screenDensity = 1.5f
    device.screenDpi = 300
    device.bootTime = Date()
    device.timezone = TimeZone.getDefault()
    device.id = "id"
    device.connectionType = "connection type"
    device.batteryTemperature = 30f
    device.locale = "en-US"
    device.cpuDescription = "cpu0"
    device.processorCount = 4
    device.processorFrequency = 800.0
    val unknown = mapOf(Pair("unknown", "unknown"))
    device.setUnknown(unknown)

    val clone = Device(device)

    assertEquals("name", clone.name)
    assertEquals("manufacturer", clone.manufacturer)
    assertEquals("brand", clone.brand)
    assertEquals("family", clone.family)
    assertEquals("model", clone.model)
    assertEquals("modelId", clone.modelId)
    assertNotNull(clone.archs) {
      assertEquals(2, it.size)
      assertEquals("archs1", it[0])
      assertEquals("archs2", it[1])
    }
    assertEquals(3.14f, clone.batteryLevel)
    assertEquals(true, clone.isCharging)
    assertEquals(true, clone.isOnline)
    assertEquals(Device.DeviceOrientation.LANDSCAPE, clone.orientation)
    assertEquals(true, clone.isSimulator)
    assertEquals(10, clone.memorySize)
    assertEquals(5, clone.freeMemory)
    assertEquals(2, clone.usableMemory)
    assertEquals(true, clone.isLowMemory)
    assertEquals(1024, clone.storageSize)
    assertEquals(512, clone.freeStorage)
    assertEquals(768, clone.externalStorageSize)
    assertEquals(384, clone.externalFreeStorage)
    assertEquals(1024, clone.screenWidthPixels)
    assertEquals(768, clone.screenHeightPixels)
    assertEquals(1.5f, clone.screenDensity)
    assertEquals(300, clone.screenDpi)
    assertEquals("id", clone.id)
    assertEquals("connection type", clone.connectionType)
    assertEquals(30f, clone.batteryTemperature)
    assertEquals("cpu0", clone.cpuDescription)
    assertEquals(4, clone.processorCount)
    assertEquals(800.0, clone.processorFrequency)
    device.processorFrequency = 800.0
    assertNotNull(clone.unknown) { assertEquals("unknown", it["unknown"]) }
  }
}
