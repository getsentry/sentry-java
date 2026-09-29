package io.sentry.android.core

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.sentry.Scopes
import io.sentry.SentryFeedbackOptions
import io.sentry.test.DeferredExecutorService
import io.sentry.test.ImmediateExecutorService
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class FeedbackShakeIntegrationTest {

  private class Fixture {
    val application = mock<Application>()
    val scopes = mock<Scopes>()
    val options =
      SentryAndroidOptions().apply {
        dsn = "https://key@sentry.io/proj"
        executorService = ImmediateExecutorService()
      }
    val activity = mock<Activity>()
    val formHandler = mock<SentryFeedbackOptions.IFormHandler>()

    init {
      options.feedbackOptions.setFormHandler(formHandler)
    }

    fun getSut(useShakeGesture: Boolean = true): FeedbackShakeIntegration {
      options.feedbackOptions.isUseShakeGesture = useShakeGesture
      return FeedbackShakeIntegration(application)
    }
  }

  private val fixture = Fixture()

  @BeforeTest
  fun setup() {
    CurrentActivityHolder.getInstance().clearActivity()
  }

  @Test
  fun `when useShakeGesture is enabled registers activity lifecycle callbacks`() {
    val sut = fixture.getSut(useShakeGesture = true)
    sut.register(fixture.scopes, fixture.options)

    verify(fixture.application).registerActivityLifecycleCallbacks(any())
  }

  @Test
  fun `resolves the accelerometer sensor off the main thread`() {
    val deferredExecutor = DeferredExecutorService()
    fixture.options.executorService = deferredExecutor
    whenever(fixture.application.getSystemService(any())).thenReturn(null)

    val sut = fixture.getSut(useShakeGesture = true)
    sut.register(fixture.scopes, fixture.options)

    // Callback registration stays synchronous, but the expensive SensorManager lookup is deferred.
    verify(fixture.application).registerActivityLifecycleCallbacks(any())
    verify(fixture.application, never()).getSystemService(eq(Context.SENSOR_SERVICE))

    deferredExecutor.runAll()

    verify(fixture.application).getSystemService(eq(Context.SENSOR_SERVICE))
  }

  @Test
  fun `warm-up drained after close does not resolve the sensor`() {
    // Integrations are closed before the executor drains, so a queued warm-up can run after
    // close(). It must be a no-op rather than resolving the sensor and spinning up a HandlerThread.
    val deferredExecutor = DeferredExecutorService()
    fixture.options.executorService = deferredExecutor
    whenever(fixture.application.getSystemService(any())).thenReturn(null)

    val sut = fixture.getSut(useShakeGesture = true)
    sut.register(fixture.scopes, fixture.options)
    sut.close()

    deferredExecutor.runAll()

    verify(fixture.application, never()).getSystemService(eq(Context.SENSOR_SERVICE))
  }

  @Test
  fun `re-registering after close re-arms shake detection`() {
    // A second Sentry.init reusing the same integration must revive shake detection rather than
    // stay off because of the closed latch.
    val deferredExecutor = DeferredExecutorService()
    fixture.options.executorService = deferredExecutor
    whenever(fixture.application.getSystemService(any())).thenReturn(null)

    val sut = fixture.getSut(useShakeGesture = true)
    sut.register(fixture.scopes, fixture.options)
    sut.close()
    sut.register(fixture.scopes, fixture.options)

    deferredExecutor.runAll()

    verify(fixture.application, atLeastOnce()).getSystemService(eq(Context.SENSOR_SERVICE))
  }

  @Test
  fun `when useShakeGesture is disabled does not register activity lifecycle callbacks`() {
    val sut = fixture.getSut(useShakeGesture = false)
    sut.register(fixture.scopes, fixture.options)

    verify(fixture.application, never()).registerActivityLifecycleCallbacks(any())
  }

  @Test
  fun `close unregisters activity lifecycle callbacks`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)

    sut.close()

    verify(fixture.application).unregisterActivityLifecycleCallbacks(any())
  }

  @Test
  fun `hooks into already-resumed activity on deferred init`() {
    CurrentActivityHolder.getInstance().setActivity(fixture.activity)
    whenever(fixture.activity.getSystemService(any())).thenReturn(null)

    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)

    // The integration should have attempted to start shake detection
    // (it will fail gracefully because SensorManager is null in tests,
    // but the important thing is it tried)
  }

  @Test
  fun `does not crash when no activity is available on deferred init`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)
    // Should not throw
  }

  @Test
  fun `onActivityPaused stops shake detection`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)

    whenever(fixture.activity.getSystemService(any())).thenReturn(null)
    sut.onActivityResumed(fixture.activity)
    sut.onActivityPaused(fixture.activity)
    // Should not throw, shake detection stopped gracefully
  }

  @Test
  fun `close without register does not crash`() {
    val sut = fixture.getSut()
    sut.close()
  }

  @Test
  fun `register sets itself as shake controller even when useShakeGesture is disabled`() {
    val sut = fixture.getSut(useShakeGesture = false)
    sut.register(fixture.scopes, fixture.options)

    assertThat(fixture.options.feedbackOptions.shakeController).isSameInstanceAs(sut)
    assertThat(sut.isOnShakeEnabled).isFalse()
  }

  @Test
  fun `enable after register starts shake detection at runtime`() {
    CurrentActivityHolder.getInstance().setActivity(fixture.activity)
    whenever(fixture.activity.getSystemService(any())).thenReturn(null)

    val sut = fixture.getSut(useShakeGesture = false)
    sut.register(fixture.scopes, fixture.options)
    verify(fixture.application, never()).registerActivityLifecycleCallbacks(any())

    sut.enableOnShake()

    assertThat(sut.isOnShakeEnabled).isTrue()
    verify(fixture.application).registerActivityLifecycleCallbacks(any())
    // Hooks into the already-resumed activity
    verify(fixture.activity).getSystemService(eq(Context.SENSOR_SERVICE))
  }

  @Test
  fun `enable is idempotent`() {
    val sut = fixture.getSut(useShakeGesture = false)
    sut.register(fixture.scopes, fixture.options)

    sut.enableOnShake()
    sut.enableOnShake()

    verify(fixture.application, times(1)).registerActivityLifecycleCallbacks(any())
  }

  @Test
  fun `disable stops shake detection at runtime`() {
    val sut = fixture.getSut(useShakeGesture = true)
    sut.register(fixture.scopes, fixture.options)

    sut.disableOnShake()

    assertThat(sut.isOnShakeEnabled).isFalse()
    verify(fixture.application).unregisterActivityLifecycleCallbacks(any())
  }

  @Test
  fun `disable is idempotent`() {
    val sut = fixture.getSut(useShakeGesture = true)
    sut.register(fixture.scopes, fixture.options)

    sut.disableOnShake()
    sut.disableOnShake()

    verify(fixture.application, times(1)).unregisterActivityLifecycleCallbacks(any())
  }

  @Test
  fun `disable when never enabled does not unregister callbacks`() {
    val sut = fixture.getSut(useShakeGesture = false)
    sut.register(fixture.scopes, fixture.options)

    sut.disableOnShake()

    verify(fixture.application, never()).unregisterActivityLifecycleCallbacks(any())
  }

  @Test
  fun `enable before register is a no-op`() {
    val sut = fixture.getSut(useShakeGesture = false)

    sut.enableOnShake()

    assertThat(sut.isOnShakeEnabled).isFalse()
    verify(fixture.application, never()).registerActivityLifecycleCallbacks(any())
  }

  @Test
  fun `re-enable after disable re-arms shake detection`() {
    val deferredExecutor = DeferredExecutorService()
    fixture.options.executorService = deferredExecutor
    whenever(fixture.application.getSystemService(any())).thenReturn(null)

    val sut = fixture.getSut(useShakeGesture = true)
    sut.register(fixture.scopes, fixture.options)
    sut.disableOnShake()
    sut.enableOnShake()

    deferredExecutor.runAll()

    assertThat(sut.isOnShakeEnabled).isTrue()
    verify(fixture.application, atLeastOnce()).getSystemService(eq(Context.SENSOR_SERVICE))
  }

  @Test
  fun `close disables shake detection`() {
    val sut = fixture.getSut(useShakeGesture = true)
    sut.register(fixture.scopes, fixture.options)

    sut.close()

    assertThat(sut.isOnShakeEnabled).isFalse()
  }

  @Test
  fun `a visible dialog does not tear down the detection machinery`() {
    val sut = fixture.getSut(useShakeGesture = true)
    sut.register(fixture.scopes, fixture.options)

    val dialog = mock<Dialog>()
    sut.onDialogVisible(fixture.activity, dialog)
    sut.onDialogGone(dialog)

    verify(fixture.application, never()).unregisterActivityLifecycleCallbacks(any())
    assertThat(sut.isOnShakeEnabled).isTrue()
  }

  @Test
  fun `a dialog suppresses detection on the activity it belongs to`() {
    whenever(fixture.activity.getSystemService(any())).thenReturn(null)

    val sut = fixture.getSut(useShakeGesture = true)
    sut.register(fixture.scopes, fixture.options)

    sut.onDialogVisible(fixture.activity, mock())
    assertThat(sut.dialogActivity).isSameInstanceAs(fixture.activity)

    // Coming back to the activity the dialog is on (e.g. screen off/on) must not re-arm detection,
    // otherwise a shake would stack a second dialog on top of the visible one.
    sut.onActivityResumed(fixture.activity)

    verify(fixture.activity, never()).getSystemService(eq(Context.SENSOR_SERVICE))
  }

  @Test
  fun `a dialog on a backgrounded activity does not suppress detection on the next one`() {
    // A dialog lives in the window of the activity that created it, so once that activity is no
    // longer resumed the dialog cannot be seen - it must not keep detection off on the activity
    // now in front. Android's order is A.onPause() -> B.onResume(), so exercise exactly that.
    val otherActivity = mock<Activity>()
    whenever(fixture.activity.getSystemService(any())).thenReturn(null)
    whenever(otherActivity.getSystemService(any())).thenReturn(null)

    val sut = fixture.getSut(useShakeGesture = true)
    sut.register(fixture.scopes, fixture.options)

    CurrentActivityHolder.getInstance().setActivity(fixture.activity)
    sut.onActivityResumed(fixture.activity)
    sut.onDialogVisible(fixture.activity, mock())

    sut.onActivityPaused(fixture.activity)
    sut.onActivityResumed(otherActivity)

    verify(otherActivity).getSystemService(eq(Context.SENSOR_SERVICE))
  }

  @Test
  fun `a dialog reports the activity it is showing on, not the current one`() {
    // The dialog's host activity is what a stacked dialog would land on, so a mid-transition
    // CurrentActivityHolder must not decide which activity detection is suppressed for.
    val otherActivity = mock<Activity>()
    whenever(fixture.activity.getSystemService(any())).thenReturn(null)

    val sut = fixture.getSut(useShakeGesture = true)
    sut.register(fixture.scopes, fixture.options)

    CurrentActivityHolder.getInstance().setActivity(otherActivity)
    sut.onDialogVisible(fixture.activity, mock())

    assertThat(sut.dialogActivity).isSameInstanceAs(fixture.activity)

    sut.onActivityResumed(fixture.activity)

    verify(fixture.activity, never()).getSystemService(eq(Context.SENSOR_SERVICE))
  }

  @Test
  fun `dismissing a dialog re-arms detection on the current activity`() {
    whenever(fixture.activity.getSystemService(any())).thenReturn(null)

    val sut = fixture.getSut(useShakeGesture = true)
    sut.register(fixture.scopes, fixture.options)

    CurrentActivityHolder.getInstance().setActivity(fixture.activity)
    sut.onActivityResumed(fixture.activity)
    val dialog = mock<Dialog>()
    sut.onDialogVisible(fixture.activity, dialog)
    sut.onDialogGone(dialog)

    assertThat(sut.dialogActivity).isNull()
    verify(fixture.activity, atLeastOnce()).getSystemService(eq(Context.SENSOR_SERVICE))
  }

  @Test
  fun `dismissing one of two visible dialogs keeps detection suppressed`() {
    // Two dialogs can be visible at once, e.g. when the app calls showForm() while a dialog is
    // already up. The first one going away must not re-arm detection under the second.
    whenever(fixture.activity.getSystemService(any())).thenReturn(null)

    val sut = fixture.getSut(useShakeGesture = true)
    sut.register(fixture.scopes, fixture.options)

    CurrentActivityHolder.getInstance().setActivity(fixture.activity)
    sut.onActivityResumed(fixture.activity)
    val first = mock<Dialog>()
    val second = mock<Dialog>()
    sut.onDialogVisible(fixture.activity, first)
    sut.onDialogVisible(fixture.activity, second)

    sut.onDialogGone(first)
    assertThat(sut.dialogActivity).isSameInstanceAs(fixture.activity)
    verify(fixture.activity, times(1)).getSystemService(eq(Context.SENSOR_SERVICE))

    sut.onDialogGone(second)
    assertThat(sut.dialogActivity).isNull()
    verify(fixture.activity, times(2)).getSystemService(eq(Context.SENSOR_SERVICE))
  }

  @Test
  fun `reporting the same dialog gone twice re-arms detection only once`() {
    // A dismissed dialog reports back from both onStop() and onDetachedFromWindow().
    whenever(fixture.activity.getSystemService(any())).thenReturn(null)

    val sut = fixture.getSut(useShakeGesture = true)
    sut.register(fixture.scopes, fixture.options)

    CurrentActivityHolder.getInstance().setActivity(fixture.activity)
    sut.onActivityResumed(fixture.activity)
    val dialog = mock<Dialog>()
    sut.onDialogVisible(fixture.activity, dialog)
    sut.onDialogGone(dialog)
    sut.onDialogGone(dialog)

    // Once for the resume, once for the single re-arm - the second report is a no-op.
    verify(fixture.activity, times(2)).getSystemService(eq(Context.SENSOR_SERVICE))
  }

  @Test
  fun `a dialog that fails to show does not leave detection suppressed`() {
    // Dialog.show() runs onStart() - which reports the dialog as visible and stops detection -
    // before the window is added, so an addView() failure hits with the dialog already tracked
    // and no lifecycle callback left to report it gone.
    val sensorManager = mock<SensorManager>()
    val accelerometer = mock<Sensor>()
    whenever(fixture.activity.getSystemService(Context.SENSOR_SERVICE)).thenReturn(sensorManager)
    whenever(sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, false))
      .thenReturn(accelerometer)
    whenever(fixture.activity.runOnUiThread(any())).thenAnswer {
      (it.arguments[0] as Runnable).run()
      null
    }

    val sut = fixture.getSut(useShakeGesture = true)
    sut.register(fixture.scopes, fixture.options)
    CurrentActivityHolder.getInstance().setActivity(fixture.activity)
    sut.onActivityResumed(fixture.activity)

    val dialog = mock<Dialog>()
    doAnswer {
        sut.onDialogVisible(fixture.activity, dialog)
        throw WindowManager.BadTokenException("Unable to add window")
      }
      .whenever(dialog)
      .show()
    sut.setDialogFactory { dialog }

    val listener = argumentCaptor<SensorEventListener>()
    verify(sensorManager)
      .registerListener(
        listener.capture(),
        eq(accelerometer),
        eq(SensorManager.SENSOR_DELAY_NORMAL),
        isA<Handler>(),
      )
    shake(listener.lastValue)

    verify(dialog).show()
    assertThat(sut.dialogActivity).isNull()
    verify(sensorManager, times(2))
      .registerListener(
        any<SensorEventListener>(),
        eq(accelerometer),
        eq(SensorManager.SENSOR_DELAY_NORMAL),
        isA<Handler>(),
      )
  }

  private fun shake(listener: SensorEventListener) {
    val baseTimestamp = 1_000_000_000L
    val intervalNs = 20_000_000L
    for (i in 0 until 20) {
      listener.onSensorChanged(
        createSensorEvent(floatArrayOf(20f, 0f, 0f), baseTimestamp + i * intervalNs)
      )
    }
  }

  private fun createSensorEvent(values: FloatArray, timestamp: Long): SensorEvent {
    val sensor = mock<Sensor>()
    whenever(sensor.type).thenReturn(Sensor.TYPE_ACCELEROMETER)

    val constructor = SensorEvent::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType)
    constructor.isAccessible = true
    val event = constructor.newInstance(values.size)
    values.copyInto(event.values)
    SensorEvent::class.java.getField("sensor").set(event, sensor)
    SensorEvent::class.java.getField("timestamp").set(event, timestamp)
    return event
  }
}
