package io.sentry.android.core

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.android.core.internal.util.CpuInfoUtils
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceInfoUtilTest {
  private lateinit var context: Context

  @Suppress("deprecation")
  @BeforeTest
  fun `set up`() {
    context = ApplicationProvider.getApplicationContext()
    context.sendStickyBroadcast(
      Intent(Intent.ACTION_BATTERY_CHANGED)
        .putExtra(BatteryManager.EXTRA_LEVEL, 75)
        .putExtra(BatteryManager.EXTRA_PLUGGED, 0)
    )
    DeviceInfoUtil.resetInstance()
  }

  @Test
  fun `provides os, memory and sideloaded info`() {
    val deviceInfoUtil = DeviceInfoUtil.getInstance(context, SentryAndroidOptions())

    val os = deviceInfoUtil.operatingSystem
    val sideLoadedInfo = deviceInfoUtil.sideLoadedInfo
    val deviceInfo = deviceInfoUtil.collectDeviceInformation(false, false)

    assertNotNull(os.kernelVersion)
    assertNotNull(os.isRooted)

    assertNotNull(sideLoadedInfo)
    assertNotNull(sideLoadedInfo.isSideLoaded)

    assertNotNull(deviceInfo.isSimulator)
    assertNotNull(deviceInfo.memorySize)
  }

  @Test
  fun `does include cpu data`() {
    CpuInfoUtils.getInstance().setCpuMaxFrequencies(listOf(1024))
    val deviceInfoUtil = DeviceInfoUtil.getInstance(context, SentryAndroidOptions())
    val deviceInfo = deviceInfoUtil.collectDeviceInformation(false, false)

    assertEquals(1, deviceInfo.processorCount)
    assertEquals(1024.0, deviceInfo.processorFrequency)
  }

  @Test
  fun `does include device io data when enabled`() {
    val options = SentryAndroidOptions().apply { isCollectAdditionalContext = true }
    val deviceInfoUtil = DeviceInfoUtil.getInstance(context, options)
    val deviceInfo = deviceInfoUtil.collectDeviceInformation(true, false)

    // all values are 0 when running via robolectric
    assertNotNull(deviceInfo.memorySize)
    assertNotNull(deviceInfo.storageSize)
    assertNotNull(deviceInfo.freeStorage)
  }

  @Test
  fun `does not include device io data when disabled`() {
    val options = SentryAndroidOptions().apply { isCollectAdditionalContext = true }
    val deviceInfoUtil = DeviceInfoUtil.getInstance(context, options)
    val deviceInfo = deviceInfoUtil.collectDeviceInformation(false, false)

    assertNull(deviceInfo.storageSize)
    assertNull(deviceInfo.freeStorage)
  }

  @Test
  fun `does include dynamic data when enabled`() {
    val options = SentryAndroidOptions().apply { isCollectAdditionalContext = true }
    val deviceInfoUtil = DeviceInfoUtil.getInstance(context, options)
    val deviceInfo = deviceInfoUtil.collectDeviceInformation(true, true)

    // all values are 0 when running via robolectric
    assertNotNull(deviceInfo.freeMemory)
    assertNotNull(deviceInfo.isLowMemory)
  }

  @Test
  fun `does not include dynamic data when disabled`() {
    val options = SentryAndroidOptions().apply { isCollectAdditionalContext = true }
    val deviceInfoUtil = DeviceInfoUtil.getInstance(context, options)
    val deviceInfo = deviceInfoUtil.collectDeviceInformation(true, false)

    assertNull(deviceInfo.freeMemory)
    assertNull(deviceInfo.isLowMemory)
  }

  @Test
  fun `does perform root check if root checker is enabled`() {
    val options = SentryAndroidOptions().apply { isEnableRootCheck = true }
    val deviceInfoUtil = DeviceInfoUtil.getInstance(context, options)
    assertNotNull(deviceInfoUtil.operatingSystem.isRooted)
  }

  @Test
  fun `does not perform root check if root checker is disabled`() {
    val options = SentryAndroidOptions().apply { isEnableRootCheck = false }
    val deviceInfoUtil = DeviceInfoUtil.getInstance(context, options)
    assertNull(deviceInfoUtil.operatingSystem.isRooted)
  }
}
