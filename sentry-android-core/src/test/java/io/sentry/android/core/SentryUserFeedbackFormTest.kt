package io.sentry.android.core

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Looper
import android.view.WindowManager
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.ILogger
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.ReplayController
import io.sentry.Sentry
import io.sentry.SentryFeedbackOptions
import io.sentry.SentryLevel
import io.sentry.protocol.SentryId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class SentryUserFeedbackFormTest {
  class Fixture {
    val application: Context = ApplicationProvider.getApplicationContext()
    private val mockDsn = "http://key@localhost/proj"

    val mockedSentry = mockStatic(Sentry::class.java)
    val mockScope = mock<IScope>()
    val mockScopes = mock<IScopes>()
    val mockLogger = mock<ILogger>()
    val mockReplayController = mock<ReplayController>()

    val options =
      SentryAndroidOptions().apply {
        dsn = mockDsn
        profilesSampleRate = 1.0
        isDebug = true
        setLogger(mockLogger)
        setReplayController(mockReplayController)
      }

    init {
      whenever(mockScope.user).thenReturn(mock())
      whenever(mockScopes.scope).thenReturn(mockScope)
      whenever(mockScopes.options).thenReturn(options)
      whenever(mockScopes.isEnabled).thenReturn(true)
    }

    fun getSut(
      associatedEventId: SentryId? = null,
      configuration: SentryUserFeedbackForm.OptionsConfiguration? = null,
      configurator: SentryFeedbackOptions.OptionsConfigurator? = null,
    ): SentryUserFeedbackForm =
      SentryUserFeedbackForm(application, 0, associatedEventId, configuration, configurator)
  }

  private val fixture = Fixture()

  @BeforeTest
  fun setUp() {
    fixture.mockedSentry.`when`<Any> { Sentry.getCurrentScopes() }.thenReturn(fixture.mockScopes)
  }

  @AfterTest
  fun cleanup() {
    fixture.mockedSentry.close()
  }

  @Test
  fun `feedback dialog is shown when sdk is enabled`() {
    fixture.options.isEnabled = true
    val sut = fixture.getSut()
    verifyNoInteractions(fixture.mockLogger)
    sut.show()
    verifyNoInteractions(fixture.mockLogger)
  }

  @Test
  fun `feedback dialog is not shown when sdk is disabled`() {
    fixture.options.isEnabled = false
    val sut = fixture.getSut()
    sut.show()
    verify(fixture.mockLogger)
      .log(eq(SentryLevel.WARNING), eq("Sentry is disabled. Feedback dialog won't be shown."))
  }

  @Test
  fun `when feedback dialog is shown, replay is captured`() {
    fixture.options.isEnabled = true
    val sut = fixture.getSut()
    verifyNoInteractions(fixture.mockReplayController)
    sut.show()
    verify(fixture.mockReplayController).captureReplay(eq(false))
  }

  @Test
  fun `when configuration is passed, it is applied to the current dialog only`() {
    fixture.options.isEnabled = true
    val sut =
      fixture.getSut(configuration = { context, options -> options.formTitle = "custom title" })
    assertNotEquals("custom title", fixture.options.feedbackOptions.formTitle)
    sut.show()
    // After showing the dialog, the title should be set
    assertEquals(
      "custom title",
      sut.findViewById<TextView>(R.id.sentry_dialog_user_feedback_title).text,
    )
    // And the original options should not be modified
    assertNotEquals("custom title", fixture.options.feedbackOptions.formTitle)
  }

  @Test
  fun `when configurator is passed, it is applied to the current dialog only`() {
    fixture.options.isEnabled = true
    val sut = fixture.getSut(configurator = { options -> options.formTitle = "custom title" })
    assertNotEquals("custom title", fixture.options.feedbackOptions.formTitle)
    sut.show()
    // After showing the dialog, the title should be set
    assertEquals(
      "custom title",
      sut.findViewById<TextView>(R.id.sentry_dialog_user_feedback_title).text,
    )
    // And the original options should not be modified
    assertNotEquals("custom title", fixture.options.feedbackOptions.formTitle)
  }

  @Test
  fun `dialog window does not have FLAG_ALT_FOCUSABLE_IM so soft keyboard can appear`() {
    fixture.options.isEnabled = true
    val sut = fixture.getSut()
    sut.show()
    val window = sut.window
    assertNotNull(window)
    val flags = window.attributes.flags
    assertEquals(0, flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
  }

  @Test
  fun `a crashing onFormClose callback does not crash the app when the dialog is closed`() {
    fixture.options.isEnabled = true
    fixture.options.feedbackOptions.onFormClose = Runnable { throw RuntimeException("user bug") }
    val sut = fixture.getSut()
    sut.show()

    sut.dismiss()
    // The dismiss listener is dispatched via a Handler message
    shadowOf(Looper.getMainLooper()).idle()

    verify(fixture.mockLogger)
      .log(eq(SentryLevel.ERROR), eq("onFormClose callback threw an exception."), any())
  }

  @Test
  fun `a crashing onFormClose callback still runs the user's dismiss listener`() {
    fixture.options.isEnabled = true
    fixture.options.feedbackOptions.onFormClose = Runnable { throw RuntimeException("user bug") }
    val sut = fixture.getSut()
    var dismissed = false
    sut.setOnDismissListener { dismissed = true }
    sut.show()

    sut.dismiss()
    shadowOf(Looper.getMainLooper()).idle()

    assertTrue(dismissed)
  }

  @Test
  fun `a crashing onFormOpen callback does not crash the app when the dialog is shown`() {
    fixture.options.isEnabled = true
    fixture.options.feedbackOptions.onFormOpen = Runnable { throw RuntimeException("user bug") }
    val sut = fixture.getSut()

    sut.show()

    verify(fixture.mockLogger)
      .log(eq(SentryLevel.ERROR), eq("onFormOpen callback threw an exception."), any())
    // The form open must still complete its own work after the callback crash
    verify(fixture.mockReplayController).captureReplay(eq(false))
  }

  @Test
  fun `dialog reports its own host activity to the shake integration while visible`() {
    fixture.options.isEnabled = true
    val integration = FeedbackShakeIntegration(fixture.application as Application)
    fixture.options.feedbackOptions.setShakeController(integration)
    val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    val sut = SentryUserFeedbackForm(activity, 0, null, null, null)
    sut.show()

    assertEquals(activity, integration.dialogActivity)

    sut.dismiss()
    shadowOf(Looper.getMainLooper()).idle()

    assertNull(integration.dialogActivity)
  }
}
