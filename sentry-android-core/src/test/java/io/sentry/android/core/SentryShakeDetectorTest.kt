package io.sentry.android.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.os.Handler
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ILogger
import kotlin.test.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SentryShakeDetectorTest {

  private class Fixture {
    val logger = mock<ILogger>()
    val context = mock<Context>()
    val sensorManager = mock<SensorManager>()
    val accelerometer = mock<Sensor>()
    val listener = mock<SentryShakeDetector.Listener>()

    init {
      whenever(context.getSystemService(Context.SENSOR_SERVICE)).thenReturn(sensorManager)
      whenever(sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, false)).thenReturn(accelerometer)
    }

    fun getSut(): SentryShakeDetector {
      return SentryShakeDetector(logger)
    }
  }

  private val fixture = Fixture()

  @Test
  fun `registers sensor listener on start`() {
    val sut = fixture.getSut()
    sut.start(fixture.context, fixture.listener)

    verify(fixture.sensorManager)
      .registerListener(eq(sut), eq(fixture.accelerometer), eq(SensorManager.SENSOR_DELAY_NORMAL), isA<Handler>())
  }

  @Test
  fun `unregisters sensor listener on stop`() {
    val sut = fixture.getSut()
    sut.start(fixture.context, fixture.listener)
    sut.stop()

    verify(fixture.sensorManager).unregisterListener(sut)
  }

  @Test
  fun `does not crash when SensorManager is null`() {
    whenever(fixture.context.getSystemService(Context.SENSOR_SERVICE)).thenReturn(null)

    val sut = fixture.getSut()
    sut.start(fixture.context, fixture.listener)

    verify(fixture.sensorManager, never()).registerListener(any(), any<Sensor>(), any<Int>(), any<Handler>())
  }

  @Test
  fun `does not crash when accelerometer is null`() {
    whenever(fixture.sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, false)).thenReturn(null)

    val sut = fixture.getSut()
    sut.start(fixture.context, fixture.listener)

    verify(fixture.sensorManager, never()).registerListener(any(), any<Sensor>(), any<Int>(), any<Handler>())
  }

  @Test
  fun `triggers listener when shake is detected`() {
    // Advance clock so cooldown check (now - 0 > 1000) passes
    SystemClock.setCurrentTimeMillis(2000)

    val sut = fixture.getSut()
    sut.start(fixture.context, fixture.listener)

    // Needs at least SHAKE_COUNT_THRESHOLD (2) readings above threshold
    val event1 = createSensorEvent(floatArrayOf(30f, 0f, 0f))
    sut.onSensorChanged(event1)
    val event2 = createSensorEvent(floatArrayOf(30f, 0f, 0f))
    sut.onSensorChanged(event2)

    verify(fixture.listener).onShake()
  }

  @Test
  fun `does not trigger listener on single shake`() {
    val sut = fixture.getSut()
    sut.start(fixture.context, fixture.listener)

    // A single threshold crossing should not trigger
    val event = createSensorEvent(floatArrayOf(30f, 0f, 0f))
    sut.onSensorChanged(event)

    verify(fixture.listener, never()).onShake()
  }

  @Test
  fun `does not trigger listener below threshold`() {
    val sut = fixture.getSut()
    sut.start(fixture.context, fixture.listener)

    // Gravity only (1G) - no shake
    val event = createSensorEvent(floatArrayOf(0f, 0f, SensorManager.GRAVITY_EARTH))
    sut.onSensorChanged(event)

    verify(fixture.listener, never()).onShake()
  }

  @Test
  fun `does not trigger listener for non-accelerometer events`() {
    val sut = fixture.getSut()
    sut.start(fixture.context, fixture.listener)

    val event = createSensorEvent(floatArrayOf(30f, 0f, 0f), sensorType = Sensor.TYPE_GYROSCOPE)
    sut.onSensorChanged(event)

    verify(fixture.listener, never()).onShake()
  }

  @Test
  fun `stop without start does not crash`() {
    val sut = fixture.getSut()
    sut.stop()
  }

  private fun createSensorEvent(
    values: FloatArray,
    sensorType: Int = Sensor.TYPE_ACCELEROMETER,
  ): SensorEvent {
    val sensor = mock<Sensor>()
    whenever(sensor.type).thenReturn(sensorType)

    val constructor = SensorEvent::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType)
    constructor.isAccessible = true
    val event = constructor.newInstance(values.size)
    values.copyInto(event.values)

    val sensorField = SensorEvent::class.java.getField("sensor")
    sensorField.set(event, sensor)

    return event
  }
}
