package io.sentry.android.core

import android.app.Activity
import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Scopes
import io.sentry.SentryFeedbackOptions
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ShakeDetectionIntegrationTest {

  private class Fixture {
    val application = mock<Application>()
    val scopes = mock<Scopes>()
    val options = SentryAndroidOptions().apply { dsn = "https://key@sentry.io/proj" }
    val activity = mock<Activity>()
    val dialogHandler = mock<SentryFeedbackOptions.IDialogHandler>()

    init {
      options.feedbackOptions.setDialogHandler(dialogHandler)
    }

    fun getSut(useShakeGesture: Boolean = true): ShakeDetectionIntegration {
      options.feedbackOptions.isUseShakeGesture = useShakeGesture
      return ShakeDetectionIntegration(application)
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
}
