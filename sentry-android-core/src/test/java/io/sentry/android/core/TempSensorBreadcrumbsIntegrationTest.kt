package io.sentry.android.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.core.Breadcrumb
import io.sentry.core.IHub
import io.sentry.core.SentryLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TempSensorBreadcrumbsIntegrationTest {
    private class Fixture {
        val context = mock<Context>()
        val manager = mock<SensorManager>()
        val sensor = mock<Sensor>()

        fun getSut(): TempSensorBreadcrumbsIntegration {
            whenever(context.getSystemService(Context.SENSOR_SERVICE)).thenReturn(manager)
            whenever(manager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)).thenReturn(sensor)
            return TempSensorBreadcrumbsIntegration(context)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `When system events breadcrumb is enabled, it registers callback`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        sut.register(hub, options)
        verify(fixture.manager).registerListener(any(), any<Sensor>(), eq(SensorManager.SENSOR_DELAY_NORMAL))
        assertNotNull(sut.sensorManager)
    }

    @Test
    fun `When system events breadcrumb is disabled, it should not register a callback`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions().apply {
            isEnableSystemEventBreadcrumbs = false
        }
        val hub = mock<IHub>()
        sut.register(hub, options)
        verify(fixture.manager, never()).registerListener(any(), any<Sensor>(), any())
        assertNull(sut.sensorManager)
    }

    @Test
    fun `When TempSensorBreadcrumbsIntegration is closed, it should unregister the callback`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        sut.register(hub, options)
        sut.close()
        verify(fixture.manager).unregisterListener(any<SensorEventListener>())
        assertNull(sut.sensorManager)
    }

    @Test
    fun `When on call state received, added breadcrumb with type and category`() {
        val sut = fixture.getSut()
        val options = SentryAndroidOptions()
        val hub = mock<IHub>()
        sut.register(hub, options)
        sut.onSensorChanged(mock())

        verify(hub).addBreadcrumb(check<Breadcrumb> {
            assertEquals("device.event", it.category)
            assertEquals("system", it.type)
            assertEquals(SentryLevel.INFO, it.level)
        })
    }
}
