package io.sentry.android.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.ISentryExecutorService
import io.sentry.SentryLevel
import io.sentry.TypeCheckHint
import io.sentry.test.DeferredExecutorService
import io.sentry.test.ImmediateExecutorService
import io.sentry.test.getDeclaredCtor
import io.sentry.test.injectForField
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TempSensorBreadcrumbsIntegrationTest {
    private class Fixture {
        val context = mock<Context>()
        val manager = mock<SensorManager>()
        val sensor = mock<Sensor>()
        val options = SentryAndroidOptions()

        fun getSut(executorService: ISentryExecutorService = ImmediateExecutorService()): TempSensorBreadcrumbsIntegration {
            options.executorService = executorService
            whenever(context.getSystemService(Context.SENSOR_SERVICE)).thenReturn(manager)
            whenever(manager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)).thenReturn(sensor)
            return TempSensorBreadcrumbsIntegration(context)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `When system events breadcrumb is enabled, it registers callback`() {
        val sut = fixture.getSut()
        val scopes = mock<IScopes>()
        sut.register(scopes, fixture.options)
        verify(fixture.manager).registerListener(any(), any<Sensor>(), eq(SensorManager.SENSOR_DELAY_NORMAL))
        assertNotNull(sut.sensorManager)
    }

    @Test
    fun `temp sensor listener is registered in the executorService`() {
        val sut = fixture.getSut(executorService = mock())
        val scopes = mock<IScopes>()
        sut.register(scopes, fixture.options)

        assertNull(sut.sensorManager)
    }

    @Test
    fun `When system events breadcrumb is disabled, it should not register a callback`() {
        val sut = fixture.getSut()
        val scopes = mock<IScopes>()
        sut.register(
            scopes,
            fixture.options.apply {
                isEnableSystemEventBreadcrumbs = false
            }
        )
        verify(fixture.manager, never()).registerListener(any(), any<Sensor>(), any())
        assertNull(sut.sensorManager)
    }

    @Test
    fun `When TempSensorBreadcrumbsIntegration is closed, it should unregister the callback`() {
        val sut = fixture.getSut()
        val scopes = mock<IScopes>()
        sut.register(scopes, fixture.options)
        sut.close()
        verify(fixture.manager).unregisterListener(any<SensorEventListener>())
        assertNull(sut.sensorManager)
    }

    @Test
    fun `when scopes is closed right after start, integration is not registered`() {
        val deferredExecutorService = DeferredExecutorService()
        val sut = fixture.getSut(executorService = deferredExecutorService)
        sut.register(mock(), fixture.options)
        assertNull(sut.sensorManager)
        sut.close()
        deferredExecutorService.runAll()
        assertNull(sut.sensorManager)
    }

    @Test
    fun `When onSensorChanged received, add a breadcrumb with type and category`() {
        val sut = fixture.getSut()
        val scopes = mock<IScopes>()
        sut.register(scopes, fixture.options)
        val sensorCtor = "android.hardware.SensorEvent".getDeclaredCtor(emptyArray())
        val sensorEvent: SensorEvent = sensorCtor.newInstance() as SensorEvent
        sensorEvent.injectForField("values", FloatArray(2) { 1F })
        sut.onSensorChanged(sensorEvent)

        verify(scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("device.event", it.category)
                assertEquals("system", it.type)
                assertEquals(SentryLevel.INFO, it.level)
            },
            check<Hint> {
                assertEquals(sensorEvent, it.get(TypeCheckHint.ANDROID_SENSOR_EVENT))
            }
        )
    }

    @Test
    fun `When onSensorChanged received and null values, do not add a breadcrumb`() {
        val sut = fixture.getSut()
        val scopes = mock<IScopes>()
        sut.register(scopes, fixture.options)
        val event = mock<SensorEvent>()
        assertNull(event.values)
        sut.onSensorChanged(event)

        verify(scopes, never()).addBreadcrumb(any<Breadcrumb>())
    }
}
