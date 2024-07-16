package io.sentry.android.core

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Breadcrumb
import io.sentry.IHub
import io.sentry.ISentryExecutorService
import io.sentry.SentryLevel
import io.sentry.test.DeferredExecutorService
import io.sentry.test.ImmediateExecutorService
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class SystemEventsBreadcrumbsIntegrationTest {

    private class Fixture {
        val context = mock<Context>()
        var options = SentryAndroidOptions()
        val hub = mock<IHub>()

        fun getSut(enableSystemEventBreadcrumbs: Boolean = true, executorService: ISentryExecutorService = ImmediateExecutorService()): SystemEventsBreadcrumbsIntegration {
            options = SentryAndroidOptions().apply {
                isEnableSystemEventBreadcrumbs = enableSystemEventBreadcrumbs
                this.executorService = executorService
            }
            return SystemEventsBreadcrumbsIntegration(context)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `When system events breadcrumb is enabled, it registers callback`() {
        val sut = fixture.getSut()

        sut.register(fixture.hub, fixture.options)

        verify(fixture.context).registerReceiver(any(), any())
        assertNotNull(sut.receiver)
    }

    @Test
    fun `system events callback is registered in the executorService`() {
        val sut = fixture.getSut(executorService = mock())
        val hub = mock<IHub>()
        sut.register(hub, fixture.options)

        assertNull(sut.receiver)
    }

    @Test
    fun `When system events breadcrumb is disabled, it doesn't register callback`() {
        val sut = fixture.getSut(enableSystemEventBreadcrumbs = false)

        sut.register(fixture.hub, fixture.options)

        verify(fixture.context, never()).registerReceiver(any(), any())
        assertNull(sut.receiver)
    }

    @Test
    fun `When ActivityBreadcrumbsIntegration is closed, it should unregister the callback`() {
        val sut = fixture.getSut()

        sut.register(fixture.hub, fixture.options)
        sut.close()

        verify(fixture.context).unregisterReceiver(any())
        assertNull(sut.receiver)
    }

    @Test
    fun `when hub is closed right after start, integration is not registered`() {
        val deferredExecutorService = DeferredExecutorService()
        val sut = fixture.getSut(executorService = deferredExecutorService)
        sut.register(fixture.hub, fixture.options)
        assertNull(sut.receiver)
        sut.close()
        deferredExecutorService.runAll()
        assertNull(sut.receiver)
    }

    @Test
    fun `When broadcast received, added breadcrumb with type and category`() {
        val sut = fixture.getSut()

        sut.register(fixture.hub, fixture.options)
        val intent = Intent().apply {
            action = Intent.ACTION_TIME_CHANGED
        }
        sut.receiver!!.onReceive(fixture.context, intent)

        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("device.event", it.category)
                assertEquals("system", it.type)
                assertEquals(SentryLevel.INFO, it.level)
                // cant assert data, its not a public API
            },
            anyOrNull()
        )
    }

    @Test
    fun `handles battery changes`() {
        val sut = fixture.getSut()

        sut.register(fixture.hub, fixture.options)
        val intent = Intent().apply {
            action = Intent.ACTION_BATTERY_CHANGED
            putExtra(BatteryManager.EXTRA_LEVEL, 75)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_USB)
        }
        sut.receiver!!.onReceive(fixture.context, intent)

        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("device.event", it.category)
                assertEquals("system", it.type)
                assertEquals(SentryLevel.INFO, it.level)
                assertEquals(it.data["level"], 75f)
                assertEquals(it.data["charging"], true)
            },
            anyOrNull()
        )
    }

    @Test
    fun `battery changes are debounced`() {
        val sut = fixture.getSut()

        sut.register(fixture.hub, fixture.options)
        val intent1 = Intent().apply {
            action = Intent.ACTION_BATTERY_CHANGED
            putExtra(BatteryManager.EXTRA_LEVEL, 80)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
        }
        val intent2 = Intent().apply {
            action = Intent.ACTION_BATTERY_CHANGED
            putExtra(BatteryManager.EXTRA_LEVEL, 75)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_USB)
        }
        sut.receiver!!.onReceive(fixture.context, intent1)
        sut.receiver!!.onReceive(fixture.context, intent2)

        // should only add the first crumb
        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals(it.data["level"], 80f)
                assertEquals(it.data["charging"], false)
            },
            anyOrNull()
        )
        verifyNoMoreInteractions(fixture.hub)
    }

    @Test
    fun `Do not crash if registerReceiver throws exception`() {
        val sut = fixture.getSut()
        whenever(fixture.context.registerReceiver(any(), any())).thenThrow(SecurityException())

        sut.register(fixture.hub, fixture.options)

        assertFalse(fixture.options.isEnableSystemEventBreadcrumbs)
    }
}
