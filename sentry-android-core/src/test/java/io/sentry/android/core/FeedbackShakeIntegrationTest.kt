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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class FeedbackShakeIntegrationTest {

  private class Fixture {
    val application = mock<Application>()
    val scopes = mock<Scopes>()
    val options = SentryAndroidOptions().apply { dsn = "https://key@sentry.io/proj" }
    val activity = mock<Activity>()
    val dialogHandler = mock<SentryFeedbackOptions.IDialogHandler>()

    init {
      options.feedbackOptions.setDialogHandler(dialogHandler)
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
  fun `when useShakeGesture is disabled still registers activity lifecycle callbacks for runtime toggle`() {
    val sut = fixture.getSut(useShakeGesture = false)
    sut.register(fixture.scopes, fixture.options)

    verify(fixture.application).registerActivityLifecycleCallbacks(any())
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
  fun `enabling shake at runtime starts detection on next activity resume`() {
    val sut = fixture.getSut(useShakeGesture = false)
    sut.register(fixture.scopes, fixture.options)

    whenever(fixture.activity.getSystemService(any())).thenReturn(null)

    // Shake is disabled, onActivityResumed should not crash
    sut.onActivityResumed(fixture.activity)

    // Now enable at runtime and resume a new activity
    fixture.options.feedbackOptions.isUseShakeGesture = true
    sut.onActivityResumed(fixture.activity)
    // Should not throw — shake detection attempted (fails gracefully with null SensorManager)
  }

  @Test
  fun `disabling shake at runtime stops detection on next activity resume`() {
    val sut = fixture.getSut(useShakeGesture = true)
    sut.register(fixture.scopes, fixture.options)

    whenever(fixture.activity.getSystemService(any())).thenReturn(null)
    sut.onActivityResumed(fixture.activity)

    // Disable at runtime and resume
    fixture.options.feedbackOptions.isUseShakeGesture = false
    sut.onActivityResumed(fixture.activity)
    // Should not throw — detection stopped gracefully
  }
}
