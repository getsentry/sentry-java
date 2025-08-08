package io.sentry.android.core

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Breadcrumb
import io.sentry.IScopes
import io.sentry.ISentryExecutorService
import io.sentry.SentryLevel
import io.sentry.test.DeferredExecutorService
import io.sentry.test.ImmediateExecutorService
import java.util.concurrent.CountDownLatch
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowActivityManager
import org.robolectric.shadows.ShadowBuild

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class SystemEventsBreadcrumbsIntegrationTest {
  private class Fixture {
    val context = mock<Context>()
    var options = SentryAndroidOptions()
    val scopes = mock<IScopes>()
    lateinit var shadowActivityManager: ShadowActivityManager

    fun getSut(
      enableSystemEventBreadcrumbs: Boolean = true,
      executorService: ISentryExecutorService = ImmediateExecutorService(),
    ): SystemEventsBreadcrumbsIntegration {
      options =
        SentryAndroidOptions().apply {
          isEnableSystemEventBreadcrumbs = enableSystemEventBreadcrumbs
          this.executorService = executorService
        }
      return SystemEventsBreadcrumbsIntegration(
        context,
        SystemEventsBreadcrumbsIntegration.getDefaultActions().toTypedArray(),
      )
    }
  }

  private val fixture = Fixture()

  @BeforeTest
  fun `set up`() {
    AppState.getInstance().resetInstance()
    AppState.getInstance().registerLifecycleObserver(fixture.options)
    ShadowBuild.reset()
    val activityManager =
      ApplicationProvider.getApplicationContext<Context>()
        .getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
    fixture.shadowActivityManager = Shadow.extract(activityManager)
  }

  @AfterTest
  fun `tear down`() {
    AppState.getInstance().unregisterLifecycleObserver()
  }

  @Test
  fun `When system events breadcrumb is enabled, it registers callback`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)

    verify(fixture.context).registerReceiver(any(), any(), anyOrNull(), anyOrNull(), any())
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
          assertEquals(it.data["level"], 75)
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
          assertEquals(it.data["level"], 80)
          assertEquals(it.data["charging"], false)
        },
        anyOrNull(),
      )
    verifyNoMoreInteractions(fixture.scopes)
  }

  @Test
  fun `battery changes with identical values do not generate breadcrumbs`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)

    val intent1 =
      Intent().apply {
        action = Intent.ACTION_BATTERY_CHANGED
        putExtra(BatteryManager.EXTRA_LEVEL, 80)
        putExtra(BatteryManager.EXTRA_SCALE, 100)
        putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_USB)
      }
    val intent2 =
      Intent().apply {
        action = Intent.ACTION_BATTERY_CHANGED
        putExtra(BatteryManager.EXTRA_LEVEL, 80)
        putExtra(BatteryManager.EXTRA_SCALE, 100)
        putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_USB)
      }

    // Receive first battery change
    sut.receiver!!.onReceive(fixture.context, intent1)

    // Receive second battery change with identical values
    sut.receiver!!.onReceive(fixture.context, intent2)

    // should only add the first crumb since values are identical
    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          assertEquals(it.data["level"], 80)
          assertEquals(it.data["charging"], true)
        },
        anyOrNull(),
      )
    verifyNoMoreInteractions(fixture.scopes)
  }

  @Test
  fun `battery changes with minor level differences do not generate breadcrumbs`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)

    val intent1 =
      Intent().apply {
        action = Intent.ACTION_BATTERY_CHANGED
        putExtra(BatteryManager.EXTRA_LEVEL, 80001) // 80.001%
        putExtra(BatteryManager.EXTRA_SCALE, 100000)
        putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_USB)
      }
    val intent2 =
      Intent().apply {
        action = Intent.ACTION_BATTERY_CHANGED
        putExtra(BatteryManager.EXTRA_LEVEL, 80002) // 80.002%
        putExtra(BatteryManager.EXTRA_SCALE, 100000)
        putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_USB)
      }

    // Receive first battery change
    sut.receiver!!.onReceive(fixture.context, intent1)

    // Receive second battery change with very minor level difference (rounds to same 3 decimal
    // places)
    sut.receiver!!.onReceive(fixture.context, intent2)

    // should only add the first crumb since both round to 80.000%
    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          assertEquals(it.data["level"], 80)
          assertEquals(it.data["charging"], true)
        },
        anyOrNull(),
      )
    verifyNoMoreInteractions(fixture.scopes)
  }

  @Test
  fun `Do not crash if registerReceiver throws exception`() {
    val sut = fixture.getSut()
    whenever(fixture.context.registerReceiver(any(), any(), anyOrNull(), anyOrNull(), any()))
      .thenThrow(SecurityException())

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
  fun `When integration is added, should subscribe for app state events`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)

    assertTrue(
      AppState.getInstance().lifecycleObserver.listeners.any {
        it is SystemEventsBreadcrumbsIntegration
      }
    )
  }

  @Test
  fun `When system events breadcrumbs are disabled, should not subscribe for app state events`() {
    val sut = fixture.getSut()
    fixture.options.apply { isEnableSystemEventBreadcrumbs = false }

    sut.register(fixture.scopes, fixture.options)

    assertFalse(
      AppState.getInstance().lifecycleObserver.listeners.any {
        it is SystemEventsBreadcrumbsIntegration
      }
    )
  }

  @Test
  fun `When integration is closed, should unsubscribe from app state events`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)

    assertTrue(
      AppState.getInstance().lifecycleObserver.listeners.any {
        it is SystemEventsBreadcrumbsIntegration
      }
    )

    sut.close()

    assertFalse(
      AppState.getInstance().lifecycleObserver.listeners.any {
        it is SystemEventsBreadcrumbsIntegration
      }
    )
  }

  @Test
  fun `When integration is closed from a background thread, unsubscribes from app events`() {
    val sut = fixture.getSut()
    val latch = CountDownLatch(1)

    sut.register(fixture.scopes, fixture.options)

    Thread {
        sut.close()
        latch.countDown()
      }
      .start()

    latch.await()

    // ensure all messages on main looper got processed
    shadowOf(Looper.getMainLooper()).idle()

    assertFalse(
      AppState.getInstance().lifecycleObserver.listeners.any {
        it is SystemEventsBreadcrumbsIntegration
      }
    )
  }

  @Test
  fun `when enters background unregisters receiver`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)

    sut.onBackground()

    verify(fixture.context).unregisterReceiver(any())
    assertNull(sut.receiver)
  }

  @Test
  fun `when enters foreground registers receiver`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)
    verify(fixture.context).registerReceiver(any(), any(), anyOrNull(), anyOrNull(), any())

    sut.onBackground()
    sut.onForeground()

    verify(fixture.context, times(2))
      .registerReceiver(any(), any(), anyOrNull(), anyOrNull(), any())
    assertNotNull(sut.receiver)
  }

  @Test
  fun `when enters foreground after register does not recreate the receiver`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)
    verify(fixture.context).registerReceiver(any(), any(), anyOrNull(), anyOrNull(), any())
    val receiver = sut.receiver

    sut.onForeground()
    assertEquals(receiver, sut.receiver)
  }

  @Test
  fun `when goes background right after entering foreground, receiver is not registered`() {
    val deferredExecutorService = DeferredExecutorService()
    val sut = fixture.getSut(executorService = deferredExecutorService)
    sut.register(fixture.scopes, fixture.options)
    deferredExecutorService.runAll()
    assertNotNull(sut.receiver)

    sut.onBackground()
    sut.onForeground()
    deferredExecutorService.runAll()
    assertNull(sut.receiver)
    sut.onBackground()
    deferredExecutorService.runAll()
    assertNull(sut.receiver)
  }

  @Test
  fun `when enters foreground right after closing, receiver is not registered`() {
    val deferredExecutorService = DeferredExecutorService()
    val latch = CountDownLatch(1)

    val sut = fixture.getSut(executorService = deferredExecutorService)
    sut.register(fixture.scopes, fixture.options)
    deferredExecutorService.runAll()
    assertNotNull(sut.receiver)

    Thread {
        sut.close()
        latch.countDown()
      }
      .start()

    latch.await()
    deferredExecutorService.runAll()

    sut.onForeground()
    assertNull(sut.receiver)
    deferredExecutorService.runAll()

    shadowOf(Looper.getMainLooper()).idle()

    assertNull(sut.receiver)
  }

  @Test
  fun `when integration is registered in background, receiver is not registered`() {
    val process =
      RunningAppProcessInfo().apply { this.importance = RunningAppProcessInfo.IMPORTANCE_CACHED }
    val processes = mutableListOf(process)
    fixture.shadowActivityManager.setProcesses(processes)

    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)

    assertNull(sut.receiver)
  }
}
