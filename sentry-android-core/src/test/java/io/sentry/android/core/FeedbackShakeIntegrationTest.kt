package io.sentry.android.core

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.content.Context
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
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric

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
    assertThat(sut.isEnabled).isFalse()
  }

  @Test
  fun `enable after register starts shake detection at runtime`() {
    CurrentActivityHolder.getInstance().setActivity(fixture.activity)
    whenever(fixture.activity.getSystemService(any())).thenReturn(null)

    val sut = fixture.getSut(useShakeGesture = false)
    sut.register(fixture.scopes, fixture.options)
    verify(fixture.application, never()).registerActivityLifecycleCallbacks(any())

    sut.enable()

    assertThat(sut.isEnabled).isTrue()
    verify(fixture.application).registerActivityLifecycleCallbacks(any())
    // Hooks into the already-resumed activity
    verify(fixture.activity).getSystemService(eq(Context.SENSOR_SERVICE))
  }

  @Test
  fun `enable is idempotent`() {
    val sut = fixture.getSut(useShakeGesture = false)
    sut.register(fixture.scopes, fixture.options)

    sut.enable()
    sut.enable()

    verify(fixture.application, times(1)).registerActivityLifecycleCallbacks(any())
  }

  @Test
  fun `disable stops shake detection at runtime`() {
    val sut = fixture.getSut(useShakeGesture = true)
    sut.register(fixture.scopes, fixture.options)

    sut.disable()

    assertThat(sut.isEnabled).isFalse()
    verify(fixture.application).unregisterActivityLifecycleCallbacks(any())
  }

  @Test
  fun `disable is idempotent`() {
    val sut = fixture.getSut(useShakeGesture = true)
    sut.register(fixture.scopes, fixture.options)

    sut.disable()
    sut.disable()

    verify(fixture.application, times(1)).unregisterActivityLifecycleCallbacks(any())
  }

  @Test
  fun `disable when never enabled does not unregister callbacks`() {
    val sut = fixture.getSut(useShakeGesture = false)
    sut.register(fixture.scopes, fixture.options)

    sut.disable()

    verify(fixture.application, never()).unregisterActivityLifecycleCallbacks(any())
  }

  @Test
  fun `enable before register is a no-op`() {
    val sut = fixture.getSut(useShakeGesture = false)

    sut.enable()

    assertThat(sut.isEnabled).isFalse()
    verify(fixture.application, never()).registerActivityLifecycleCallbacks(any())
  }

  @Test
  fun `re-enable after disable re-arms shake detection`() {
    val deferredExecutor = DeferredExecutorService()
    fixture.options.executorService = deferredExecutor
    whenever(fixture.application.getSystemService(any())).thenReturn(null)

    val sut = fixture.getSut(useShakeGesture = true)
    sut.register(fixture.scopes, fixture.options)
    sut.disable()
    sut.enable()

    deferredExecutor.runAll()

    assertThat(sut.isEnabled).isTrue()
    verify(fixture.application, atLeastOnce()).getSystemService(eq(Context.SENSOR_SERVICE))
  }

  @Test
  fun `close disables shake detection`() {
    val sut = fixture.getSut(useShakeGesture = true)
    sut.register(fixture.scopes, fixture.options)

    sut.close()

    assertThat(sut.isEnabled).isFalse()
  }

  private fun createShakeDialog(): TestShakeDialog {
    val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    return TestShakeDialog(activity)
  }

  private class TestShakeDialog(val activity: Activity) :
    Dialog(activity), SentryFeedbackOptions.IShakeDialog

  @Test
  fun `setDialog with startShakeDetection starts detection without enabling the global toggle`() {
    val sut = fixture.getSut(useShakeGesture = false)
    sut.register(fixture.scopes, fixture.options)

    sut.setDialog(createShakeDialog(), true)

    verify(fixture.application).registerActivityLifecycleCallbacks(any())
    assertThat(sut.isEnabled).isFalse()
  }

  @Test
  fun `setDialog without startShakeDetection only tracks the dialog`() {
    val sut = fixture.getSut(useShakeGesture = false)
    sut.register(fixture.scopes, fixture.options)

    sut.setDialog(createShakeDialog(), false)

    verify(fixture.application, never()).registerActivityLifecycleCallbacks(any())
  }

  @Test
  fun `setDialog with null stops shake detection when globally disabled`() {
    val sut = fixture.getSut(useShakeGesture = false)
    sut.register(fixture.scopes, fixture.options)
    sut.setDialog(createShakeDialog(), true)

    sut.setDialog(null, false)

    verify(fixture.application).unregisterActivityLifecycleCallbacks(any())
  }

  @Test
  fun `setDialog with null keeps shake detection when globally enabled`() {
    val sut = fixture.getSut(useShakeGesture = true)
    sut.register(fixture.scopes, fixture.options)
    sut.setDialog(createShakeDialog(), true)

    sut.setDialog(null, false)

    verify(fixture.application, never()).unregisterActivityLifecycleCallbacks(any())
  }

  @Test
  fun `disable keeps shake detection while an opted-in dialog is tracked`() {
    val sut = fixture.getSut(useShakeGesture = true)
    sut.register(fixture.scopes, fixture.options)
    val dialog = createShakeDialog()
    sut.setDialog(dialog, true)

    sut.disable()

    assertThat(sut.isEnabled).isFalse()
    verify(fixture.application, never()).unregisterActivityLifecycleCallbacks(any())

    // Once the dialog is cleared, nothing keeps detection alive anymore
    sut.setDialog(null, false)
    verify(fixture.application).unregisterActivityLifecycleCallbacks(any())
  }

  @Test
  fun `disable stops shake detection when the tracked dialog did not opt in`() {
    val sut = fixture.getSut(useShakeGesture = true)
    sut.register(fixture.scopes, fixture.options)
    sut.setDialog(createShakeDialog(), false)

    sut.disable()

    verify(fixture.application).unregisterActivityLifecycleCallbacks(any())
  }

  @Test
  fun `re-setting an opted-in dialog keeps detection alive`() {
    val sut = fixture.getSut(useShakeGesture = false)
    sut.register(fixture.scopes, fixture.options)
    val dialog = createShakeDialog()
    sut.setDialog(dialog, true)

    // An opted-in dialog reports itself again with startShakeDetection on every show
    sut.setDialog(dialog, true)

    verify(fixture.application, never()).unregisterActivityLifecycleCallbacks(any())
    verify(fixture.application, times(1)).registerActivityLifecycleCallbacks(any())
  }

  @Test
  fun `replacing an opted-in dialog with a tracking-only one stops detection when globally disabled`() {
    val sut = fixture.getSut(useShakeGesture = false)
    sut.register(fixture.scopes, fixture.options)
    sut.setDialog(createShakeDialog(), true)

    // A different dialog only reporting visibility no longer justifies detection
    sut.setDialog(createShakeDialog(), false)

    verify(fixture.application).unregisterActivityLifecycleCallbacks(any())
  }

  @Test
  fun `disable keeps detection alive after an opted-in dialog re-registers on show`() {
    // Regression: global toggle on, opted-in dialog shown (re-registers), runtime disable —
    // the opt-in must keep detection running.
    val sut = fixture.getSut(useShakeGesture = true)
    sut.register(fixture.scopes, fixture.options)
    val dialog = createShakeDialog()
    sut.setDialog(dialog, true)
    sut.setDialog(dialog, true)

    sut.disable()

    verify(fixture.application, never()).unregisterActivityLifecycleCallbacks(any())
  }

  @Test
  fun `destroying the dialog host activity clears the dialog and stops detection`() {
    val sut = fixture.getSut(useShakeGesture = false)
    sut.register(fixture.scopes, fixture.options)
    val dialog = createShakeDialog()
    sut.setDialog(dialog, true)

    sut.onActivityDestroyed(dialog.activity)

    verify(fixture.application).unregisterActivityLifecycleCallbacks(any())
  }

  @Test
  fun `setDialog before register is a no-op`() {
    val sut = fixture.getSut(useShakeGesture = false)

    sut.setDialog(createShakeDialog(), true)

    verify(fixture.application, never()).registerActivityLifecycleCallbacks(any())
  }
}
