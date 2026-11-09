package io.sentry.android.core

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Build
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.sentry.android.core.internal.util.CpuInfoUtils
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class DeviceInfoUtilTest {
  private lateinit var context: Context

  private val executor = Executors.newCachedThreadPool()

  @Suppress("deprecation")
  @BeforeTest
  fun `set up`() {
    context = ApplicationProvider.getApplicationContext()
    context.sendStickyBroadcast(
      Intent(Intent.ACTION_BATTERY_CHANGED)
        .putExtra(BatteryManager.EXTRA_LEVEL, 75)
        .putExtra(BatteryManager.EXTRA_PLUGGED, 0)
    )
  }

  @AfterTest
  fun `tear down`() {
    executor.shutdownNow()
  }

  @Test
  fun `same options reuse device info util`() {
    val options = SentryAndroidOptions()

    val first = DeviceInfoUtil.getInstance(context, options)
    val second = DeviceInfoUtil.getInstance(context, options)

    assertSame(first, second)
  }

  @Test
  fun `concurrent callers for the same options share one device info util`() {
    val options = SentryAndroidOptions()
    val stallingContext = StallingContext(context)
    try {
      val first =
        executor.submit<DeviceInfoUtil> { DeviceInfoUtil.getInstance(stallingContext, options) }
      assertThat(stallingContext.creationStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue()

      var second: DeviceInfoUtil? = null
      val secondCaller = Thread { second = DeviceInfoUtil.getInstance(stallingContext, options) }
      secondCaller.start()
      awaitWaiting(secondCaller)
      stallingContext.releaseCreation.countDown()

      val firstResult = first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
      secondCaller.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS))

      assertThat(second).isSameInstanceAs(firstResult)
      assertThat(stallingContext.creationCount.get()).isEqualTo(1)
    } finally {
      stallingContext.releaseCreation.countDown()
    }
  }

  @Test
  fun `traces sampler of the same options is available while device info util creation is in progress`() {
    val options = SentryAndroidOptions()
    val stallingContext = StallingContext(context)
    try {
      executor.submit { DeviceInfoUtil.getInstance(stallingContext, options) }
      assertThat(stallingContext.creationStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue()

      val tracesSampler = executor.submit<Any> { options.internalTracesSampler }

      assertThat(tracesSampler.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNotNull()
    } finally {
      stallingContext.releaseCreation.countDown()
    }
  }

  @Test
  fun `different options use isolated device info utils`() {
    val enabledOptions =
      SentryAndroidOptions().apply {
        dataCollection.setUserInfo(true)
        isCollectAdditionalContext = true
        isEnableRootCheck = true
      }
    val disabledOptions =
      SentryAndroidOptions().apply {
        dataCollection.setUserInfo(false)
        isCollectAdditionalContext = false
        isEnableRootCheck = false
      }

    val enabled = DeviceInfoUtil.getInstance(context, enabledOptions)
    val disabled = DeviceInfoUtil.getInstance(context, disabledOptions)
    val enabledDevice = enabled.collectDeviceInformation(true, false)
    val disabledDevice = disabled.collectDeviceInformation(true, false)

    assertNotSame(enabled, disabled)
    assertNotNull(enabledDevice.id)
    assertNotNull(enabledDevice.storageSize)
    assertNotNull(enabled.operatingSystem.isRooted)
    assertNotNull(disabledDevice.id)
    assertNull(disabledDevice.storageSize)
    assertNull(disabled.operatingSystem.isRooted)
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
  fun `sets device id when user info is disabled`() {
    val options = SentryAndroidOptions().apply { dataCollection.setUserInfo(false) }
    val deviceInfo =
      DeviceInfoUtil.getInstance(context, options).collectDeviceInformation(false, false)

    assertNotNull(deviceInfo.id)
  }

  @Test
  fun `sets device id when user info is enabled`() {
    val options = SentryAndroidOptions().apply { dataCollection.setUserInfo(true) }
    val deviceInfo =
      DeviceInfoUtil.getInstance(context, options).collectDeviceInformation(false, false)

    assertNotNull(deviceInfo.id)
  }

  @Test
  fun `sets default timezone`() {
    val deviceInfoUtil = DeviceInfoUtil.getInstance(context, SentryAndroidOptions())
    val deviceInfo = deviceInfoUtil.collectDeviceInformation(false, false)

    assertEquals(TimeZone.getDefault(), deviceInfo.timezone)
  }

  @Test
  @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
  fun `preserves timezone from locale unicode extension`() {
    val defaultTimeZone = TimeZone.getDefault()
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
      val configuration = Configuration(context.resources.configuration)
      configuration.setLocales(LocaleList(Locale.forLanguageTag("en-US-u-tz-usnyc")))
      val localizedContext = context.createConfigurationContext(configuration)
      val deviceInfoUtil = DeviceInfoUtil(localizedContext, SentryAndroidOptions())
      val deviceInfo = deviceInfoUtil.collectDeviceInformation(false, false)

      assertEquals("America/New_York", deviceInfo.timezone?.id)
    } finally {
      TimeZone.setDefault(defaultTimeZone)
    }
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

  private fun awaitWaiting(thread: Thread) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
    while (thread.state != Thread.State.WAITING) {
      check(System.nanoTime() < deadline) { "Thread did not start waiting, state: ${thread.state}" }
      Thread.yield()
    }
  }

  /**
   * Stalls [DeviceInfoUtil] creation while it queries the activity service, until [releaseCreation]
   * is counted down.
   */
  private class StallingContext(base: Context) : ContextWrapper(base) {
    val creationStarted = CountDownLatch(1)
    val releaseCreation = CountDownLatch(1)
    val creationCount = AtomicInteger()

    override fun getApplicationContext(): Context = this

    override fun getSystemService(name: String): Any? {
      if (name == Context.ACTIVITY_SERVICE) {
        creationCount.incrementAndGet()
        creationStarted.countDown()
        releaseCreation.await(STALL_LIMIT_SECONDS, TimeUnit.SECONDS)
      }
      return super.getSystemService(name)
    }
  }

  private companion object {
    const val TIMEOUT_SECONDS = 5L

    // Longer than TIMEOUT_SECONDS, so a caller blocked behind the stall times out before it ends.
    const val STALL_LIMIT_SECONDS = 30L
  }
}
