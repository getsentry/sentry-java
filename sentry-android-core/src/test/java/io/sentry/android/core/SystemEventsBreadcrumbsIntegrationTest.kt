package io.sentry.android.core

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Breadcrumb
import io.sentry.IScopes
import io.sentry.ISentryExecutorService
import io.sentry.SentryLevel
import io.sentry.test.DeferredExecutorService
import io.sentry.test.ImmediateExecutorService
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class SystemEventsBreadcrumbsIntegrationTest {
  private class Fixture {
    val context = mock<Context>()
    var options = SentryAndroidOptions()
    val scopes = mock<IScopes>()
    lateinit var handler: MainLooperHandler

    fun getSut(
      enableSystemEventBreadcrumbs: Boolean = true,
      executorService: ISentryExecutorService = ImmediateExecutorService(),
      mockHandler: Boolean = true,
    ): SystemEventsBreadcrumbsIntegration {
      handler = if (mockHandler) mock() else MainLooperHandler()
      options =
        SentryAndroidOptions().apply {
          isEnableSystemEventBreadcrumbs = enableSystemEventBreadcrumbs
          this.executorService = executorService
        }
      return SystemEventsBreadcrumbsIntegration(
        context,
        SystemEventsBreadcrumbsIntegration.getDefaultActions().toTypedArray(),
        handler,
      )
    }
  }

  private val fixture = Fixture()

  @Test
  fun `When system events breadcrumb is enabled, it registers callback`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)

    verify(fixture.context).registerReceiver(any(), any(), any())
    assertNotNull(sut.receiver)
  }

  @Test
  fun `system events callback is registered in the executorService`() {
    val sut = fixture.getSut(executorService = mock())
    val scopes = mock<IScopes>()
    sut.register(scopes, fixture.options)

    assertNull(sut.receiver)
  }

  @Test
  fun `When system events breadcrumb is disabled, it doesn't register callback`() {
    val sut = fixture.getSut(enableSystemEventBreadcrumbs = false)

    sut.register(fixture.scopes, fixture.options)

    verify(fixture.context, never()).registerReceiver(any(), any(), any())
    assertNull(sut.receiver)
  }

  @Test
  fun `When ActivityBreadcrumbsIntegration is closed, it should unregister the callback`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)
    sut.close()

    verify(fixture.context).unregisterReceiver(any())
    assertNull(sut.receiver)
  }

  @Test
  fun `when scopes is closed right after start, integration is not registered`() {
    val deferredExecutorService = DeferredExecutorService()
    val sut = fixture.getSut(executorService = deferredExecutorService)
    sut.register(fixture.scopes, fixture.options)
    assertNull(sut.receiver)
    sut.close()
    deferredExecutorService.runAll()
    assertNull(sut.receiver)
  }

  @Test
  fun `When broadcast received, added breadcrumb with type and category`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)
    val intent =
      Intent().apply {
        action = Intent.ACTION_TIME_CHANGED
        putExtra("test", 10)
        putExtra("test2", 20)
      }
    sut.receiver!!.onReceive(fixture.context, intent)

    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          assertEquals("device.event", it.category)
          assertEquals("system", it.type)
          assertEquals(SentryLevel.INFO, it.level)
          // cant assert data, its not a public API
        },
        anyOrNull(),
      )
  }

  @Test
  fun `handles battery changes`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)
    val intent =
      Intent().apply {
        action = Intent.ACTION_BATTERY_CHANGED
        putExtra(BatteryManager.EXTRA_LEVEL, 75)
        putExtra(BatteryManager.EXTRA_SCALE, 100)
        putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_USB)
      }
    sut.receiver!!.onReceive(fixture.context, intent)

    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          assertEquals("device.event", it.category)
          assertEquals("system", it.type)
          assertEquals(SentryLevel.INFO, it.level)
          assertEquals(it.data["level"], 75f)
          assertEquals(it.data["charging"], true)
        },
        anyOrNull(),
      )
  }

  @Test
  fun `battery changes are debounced`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)
    val intent1 =
      Intent().apply {
        action = Intent.ACTION_BATTERY_CHANGED
        putExtra(BatteryManager.EXTRA_LEVEL, 80)
        putExtra(BatteryManager.EXTRA_SCALE, 100)
      }
    val intent2 =
      Intent().apply {
        action = Intent.ACTION_BATTERY_CHANGED
        putExtra(BatteryManager.EXTRA_LEVEL, 75)
        putExtra(BatteryManager.EXTRA_SCALE, 100)
        putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_USB)
      }
    sut.receiver!!.onReceive(fixture.context, intent1)
    sut.receiver!!.onReceive(fixture.context, intent2)

    // should only add the first crumb
    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          assertEquals(it.data["level"], 80f)
          assertEquals(it.data["charging"], false)
        },
        anyOrNull(),
      )
    verifyNoMoreInteractions(fixture.scopes)
  }

  @Test
  fun `Do not crash if registerReceiver throws exception`() {
    val sut = fixture.getSut()
    whenever(fixture.context.registerReceiver(any(), any(), any())).thenThrow(SecurityException())

    sut.register(fixture.scopes, fixture.options)

    assertFalse(fixture.options.isEnableSystemEventBreadcrumbs)
  }

  @Test
  fun `when str has full package, return last string after dot`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)

    assertEquals(
      "DEVICE_IDLE_MODE_CHANGED",
      sut.receiver?.getStringAfterDotFast("io.sentry.DEVICE_IDLE_MODE_CHANGED"),
    )
    assertEquals(
      "POWER_SAVE_MODE_CHANGED",
      sut.receiver?.getStringAfterDotFast("io.sentry.POWER_SAVE_MODE_CHANGED"),
    )
  }

  @Test
  fun `when str is null, return null`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)

    assertNull(sut.receiver?.getStringAfterDotFast(null))
  }

  @Test
  fun `when str is empty, return the original str`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)

    assertEquals("", sut.receiver?.getStringAfterDotFast(""))
  }

  @Test
  fun `when str ends with a dot, return empty str`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)

    assertEquals("", sut.receiver?.getStringAfterDotFast("io.sentry."))
  }

  @Test
  fun `when str has no dots, return the original str`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)

    assertEquals("iosentry", sut.receiver?.getStringAfterDotFast("iosentry"))
  }

  @Test
  fun `When integration is added, lifecycle handler should be started`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)

    assertNotNull(sut.lifecycleHandler)
  }

  @Test
  fun `When system events breadcrumbs are disabled, lifecycle handler should not be started`() {
    val sut = fixture.getSut()
    fixture.options.apply { isEnableSystemEventBreadcrumbs = false }

    sut.register(fixture.scopes, fixture.options)

    assertNull(sut.lifecycleHandler)
  }

  @Test
  fun `When integration is closed, lifecycle handler should be closed`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)

    assertNotNull(sut.lifecycleHandler)

    sut.close()

    assertNull(sut.lifecycleHandler)
  }

  @Test
  fun `When integration is registered from a background thread, post on the main thread`() {
    val sut = fixture.getSut()
    val latch = CountDownLatch(1)

    Thread {
        sut.register(fixture.scopes, fixture.options)
        latch.countDown()
      }
      .start()

    latch.await()

    verify(fixture.handler).post(any())
  }

  @Test
  fun `When integration is closed from a background thread, post on the main thread`() {
    val sut = fixture.getSut()
    val latch = CountDownLatch(1)

    sut.register(fixture.scopes, fixture.options)

    assertNotNull(sut.lifecycleHandler)

    Thread {
        sut.close()
        latch.countDown()
      }
      .start()

    latch.await()

    verify(fixture.handler).post(any())
  }

  @Test
  fun `When integration is closed from a background thread, watcher is set to null`() {
    val sut = fixture.getSut(mockHandler = false)
    val latch = CountDownLatch(1)

    sut.register(fixture.scopes, fixture.options)

    assertNotNull(sut.lifecycleHandler)

    Thread {
        sut.close()
        latch.countDown()
      }
      .start()

    latch.await()

    // ensure all messages on main looper got processed
    shadowOf(Looper.getMainLooper()).idle()

    assertNull(sut.lifecycleHandler)
  }

  @Test
  fun `when enters background unregisters receiver`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)

    sut.lifecycleHandler!!.onStop(mock())

    verify(fixture.context).unregisterReceiver(any())
    assertNull(sut.receiver)
  }

  @Test
  fun `when enters foreground registers receiver`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)
    verify(fixture.context).registerReceiver(any(), any(), any())

    sut.lifecycleHandler!!.onStop(mock())
    sut.lifecycleHandler!!.onStart(mock())

    verify(fixture.context, times(2)).registerReceiver(any(), any(), any())
    assertNotNull(sut.receiver)
  }

  @Test
  fun `when enters foreground after register does not recreate the receiver`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)
    verify(fixture.context).registerReceiver(any(), any(), any())
    val receiver = sut.receiver

    sut.lifecycleHandler!!.onStart(mock())
    assertEquals(receiver, sut.receiver)
  }

  @Test
  fun `when goes background right after entering foreground, receiver is not registered`() {
    val deferredExecutorService = DeferredExecutorService()
    val sut = fixture.getSut(executorService = deferredExecutorService)
    sut.register(fixture.scopes, fixture.options)
    deferredExecutorService.runAll()
    assertNotNull(sut.receiver)

    sut.lifecycleHandler!!.onStop(mock())
    sut.lifecycleHandler!!.onStart(mock())
    assertNull(sut.receiver)
    sut.lifecycleHandler!!.onStop(mock())
    deferredExecutorService.runAll()
    assertNull(sut.receiver)
  }

  @Test
  fun `when enters foreground right after closing, receiver is not registered`() {
    val deferredExecutorService = DeferredExecutorService()
    val latch = CountDownLatch(1)

    val sut = fixture.getSut(executorService = deferredExecutorService, mockHandler = false)
    sut.register(fixture.scopes, fixture.options)
    deferredExecutorService.runAll()
    assertNotNull(sut.receiver)

    Thread {
        sut.close()
        latch.countDown()
      }
      .start()

    latch.await()

    sut.lifecycleHandler!!.onStart(mock())
    assertNull(sut.receiver)
    deferredExecutorService.runAll()

    shadowOf(Looper.getMainLooper()).idle()

    assertNull(sut.receiver)
    assertNull(sut.lifecycleHandler)
  }
}
