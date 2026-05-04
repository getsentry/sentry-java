package io.sentry.android.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.os.Handler
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ILogger
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
      whenever(sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, false))
        .thenReturn(accelerometer)
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
      .registerListener(
        eq(sut),
        eq(fixture.accelerometer),
        eq(SensorManager.SENSOR_DELAY_NORMAL),
        isA<Handler>(),
      )
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

    verify(fixture.sensorManager, never())
      .registerListener(any(), any<Sensor>(), any<Int>(), any<Handler>())
  }

  @Test
  fun `does not crash when accelerometer is null`() {
    whenever(fixture.sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, false))
      .thenReturn(null)

    val sut = fixture.getSut()
    sut.start(fixture.context, fixture.listener)

    verify(fixture.sensorManager, never())
      .registerListener(any(), any<Sensor>(), any<Int>(), any<Handler>())
  }

  @Test
  fun `triggers listener when sustained shake is detected`() {
    val sut = fixture.getSut()
    sut.start(fixture.context, fixture.listener)

    // Send enough accelerating samples over 0.25s+ to trigger (>75% accelerating)
    val baseTimestamp = 1_000_000_000L // 1s in nanos
    val intervalNs = 20_000_000L // 20ms between samples (~50Hz)
    for (i in 0 until 20) {
      val event = createSensorEvent(floatArrayOf(20f, 0f, 0f), baseTimestamp + i * intervalNs)
      sut.onSensorChanged(event)
    }

    verify(fixture.listener).onShake()
  }

  @Test
  fun `does not trigger listener on single spike`() {
    val sut = fixture.getSut()
    sut.start(fixture.context, fixture.listener)

    val event = createSensorEvent(floatArrayOf(30f, 0f, 0f), 1_000_000_000L)
    sut.onSensorChanged(event)

    verify(fixture.listener, never()).onShake()
  }

  @Test
  fun `does not trigger listener below threshold`() {
    val sut = fixture.getSut()
    sut.start(fixture.context, fixture.listener)

    val baseTimestamp = 1_000_000_000L
    val intervalNs = 20_000_000L
    for (i in 0 until 20) {
      val event = createSensorEvent(
        floatArrayOf(0f, 0f, SensorManager.GRAVITY_EARTH),
        baseTimestamp + i * intervalNs,
      )
      sut.onSensorChanged(event)
    }

    verify(fixture.listener, never()).onShake()
  }

  @Test
  fun `does not trigger listener for non-accelerometer events`() {
    val sut = fixture.getSut()
    sut.start(fixture.context, fixture.listener)

    val event = createSensorEvent(floatArrayOf(30f, 0f, 0f), 1_000_000_000L, Sensor.TYPE_GYROSCOPE)
    sut.onSensorChanged(event)

    verify(fixture.listener, never()).onShake()
  }

  @Test
  fun `stop without start does not crash`() {
    val sut = fixture.getSut()
    sut.stop()
  }

  @Test
  fun `sample queue triggers when 75 percent of samples are accelerating`() {
    val queue = SentryShakeDetector.SampleQueue()
    val intervalNs = 20_000_000L

    // 15 accelerating + 5 not = 75% in a 0.4s window (> 0.25s minimum)
    for (i in 0 until 15) {
      queue.add(i * intervalNs, true)
    }
    for (i in 15 until 20) {
      queue.add(i * intervalNs, false)
    }

    assertTrue(queue.isShaking())
  }

  @Test
  fun `sample queue does not trigger below 75 percent`() {
    val queue = SentryShakeDetector.SampleQueue()
    val intervalNs = 20_000_000L

    // 10 accelerating + 10 not = 50%
    for (i in 0 until 10) {
      queue.add(i * intervalNs, true)
    }
    for (i in 10 until 20) {
      queue.add(i * intervalNs, false)
    }

    assertFalse(queue.isShaking())
  }

  @Test
  fun `sample queue does not trigger below minimum window`() {
    val queue = SentryShakeDetector.SampleQueue()

    // All accelerating but only 0.06s apart (below 0.25s minimum)
    for (i in 0 until 4) {
      queue.add(i * 20_000_000L, true)
    }

    assertFalse(queue.isShaking())
  }

  private fun createSensorEvent(
    values: FloatArray,
    timestamp: Long = 0L,
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

    val timestampField = SensorEvent::class.java.getField("timestamp")
    timestampField.set(event, timestamp)

    return event
  }
}
