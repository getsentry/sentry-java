package io.sentry.android.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.sentry.Hint
import io.sentry.IFeedbackApi
import io.sentry.ILogger
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.ReplayController
import io.sentry.Sentry
import io.sentry.SentryFeedbackOptions
import io.sentry.SentryLevel
import io.sentry.protocol.Feedback
import io.sentry.protocol.SentryId
import io.sentry.util.LoadClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import org.junit.runner.RunWith
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
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
    val mockFeedbackApi = mock<IFeedbackApi>()

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
      context: Context = application,
    ): SentryUserFeedbackForm =
      SentryUserFeedbackForm(context, 0, associatedEventId, configuration, configurator)
  }

  private val fixture = Fixture()

  @BeforeTest
  fun setUp() {
    fixture.mockedSentry.`when`<Any> { Sentry.getCurrentScopes() }.thenReturn(fixture.mockScopes)
    fixture.mockedSentry.`when`<Any> { Sentry.feedback() }.thenReturn(fixture.mockFeedbackApi)
  }

  @AfterTest
  fun cleanup() {
    fixture.mockedSentry.close()
  }

  @Test
  fun `feedback dialog is shown when sdk is enabled`() {
    fixture.options.isEnabled = true
    val sut = fixture.getSut(context = componentActivity())
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
  fun `screenshot button is hidden when enableScreenshot is false`() {
    fixture.options.isEnabled = true
    val sut =
      fixture.getSut(
        context = componentActivity(),
        configurator = { options -> options.isEnableAttachScreenshot = false },
      )
    sut.show()
    assertThat(addScreenshotButton(sut).visibility).isEqualTo(View.GONE)
  }

  @Test
  fun `screenshot button is hidden and a warning is logged when host is not a ComponentActivity`() {
    fixture.options.isEnabled = true
    val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    val sut = fixture.getSut(context = activity)
    sut.show()
    assertThat(addScreenshotButton(sut).visibility).isEqualTo(View.GONE)
    verify(fixture.mockLogger)
      .log(
        eq(SentryLevel.WARNING),
        eq(
          "Feedback screenshot button won't be shown. It requires the androidx.activity " +
            "dependency and the feedback form being shown from a ComponentActivity."
        ),
      )
  }

  @Test
  fun `screenshot button is hidden when androidx activity is not available`() {
    fixture.options.isEnabled = true
    val loadClass = mock<LoadClass>()
    whenever(loadClass.isClassAvailable(any(), anyOrNull<io.sentry.SentryOptions>()))
      .thenReturn(false)
    fixture.options.feedbackOptions.loadClass = loadClass
    val sut = fixture.getSut(context = componentActivity())
    sut.show()
    assertThat(addScreenshotButton(sut).visibility).isEqualTo(View.GONE)
  }

  @Test
  fun `screenshot button is visible by default when hosted by a ComponentActivity`() {
    fixture.options.isEnabled = true
    val sut = fixture.getSut(context = componentActivity())
    sut.show()
    val button = addScreenshotButton(sut)
    assertThat(button.visibility).isEqualTo(View.VISIBLE)
    assertThat(button.text.toString())
      .isEqualTo(fixture.options.feedbackOptions.addScreenshotButtonLabel.toString())
  }

  @Test
  fun `screenshot button label toggles when an image is picked and removed`() {
    fixture.options.isEnabled = true
    val activity = componentActivity()
    val sut = fixture.getSut(context = activity)
    sut.show()
    val button = addScreenshotButton(sut)

    button.performClick()
    deliverPickerResult(activity, Uri.parse("content://media/external/images/media/1"))
    assertThat(button.text.toString())
      .isEqualTo(fixture.options.feedbackOptions.removeScreenshotButtonLabel.toString())

    // Clicking again removes the selected image
    button.performClick()
    assertThat(button.text.toString())
      .isEqualTo(fixture.options.feedbackOptions.addScreenshotButtonLabel.toString())
  }

  @Test
  fun `feedback is captured with an image attachment when an image is picked`() {
    fixture.options.isEnabled = true
    whenever(fixture.mockFeedbackApi.capture(any<Feedback>(), anyOrNull()))
      .thenReturn(SentryId(java.util.UUID.randomUUID()))
    val activity = componentActivity()
    val uri = Uri.parse("content://media/external/images/media/1")
    shadowOf(activity.contentResolver).registerInputStream(uri, "fake image".byteInputStream())
    val sut = fixture.getSut(context = activity)
    sut.show()

    addScreenshotButton(sut).performClick()
    deliverPickerResult(activity, uri)
    sut.findViewById<EditText>(R.id.sentry_dialog_user_feedback_edt_description).setText("message")
    sut.findViewById<Button>(R.id.sentry_dialog_user_feedback_btn_send).performClick()

    val hintCaptor = argumentCaptor<Hint>()
    verify(fixture.mockFeedbackApi).capture(any<Feedback>(), hintCaptor.capture())
    val attachments = hintCaptor.firstValue.attachments
    assertThat(attachments).hasSize(1)
    assertThat(attachments[0].filename).startsWith("screenshot.")
    assertThat(attachments[0].byteProvider).isNotNull()
    assertThat(attachments[0].byteProvider!!.call()).isEqualTo("fake image".toByteArray())
  }

  @Test
  fun `feedback is captured without attachments when no image is picked`() {
    fixture.options.isEnabled = true
    whenever(fixture.mockFeedbackApi.capture(any<Feedback>(), anyOrNull()))
      .thenReturn(SentryId(java.util.UUID.randomUUID()))
    val sut = fixture.getSut(context = componentActivity())
    sut.show()

    sut.findViewById<EditText>(R.id.sentry_dialog_user_feedback_edt_description).setText("message")
    sut.findViewById<Button>(R.id.sentry_dialog_user_feedback_btn_send).performClick()

    val hintCaptor = argumentCaptor<Hint>()
    verify(fixture.mockFeedbackApi).capture(any<Feedback>(), hintCaptor.capture())
    assertThat(hintCaptor.firstValue.attachments).isEmpty()
  }

  @Test
  fun `screenshot picker can be registered again when the dialog is shown again`() {
    fixture.options.isEnabled = true
    val sut = fixture.getSut(context = componentActivity())
    sut.show()
    sut.dismiss()
    // Would throw if the launcher of the first show() was still registered under the same key
    sut.show()
    assertThat(addScreenshotButton(sut).visibility).isEqualTo(View.VISIBLE)
  }

  private fun componentActivity(): ComponentActivity =
    Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

  private fun addScreenshotButton(sut: SentryUserFeedbackForm): Button =
    sut.findViewById(R.id.sentry_dialog_user_feedback_btn_add_screenshot)

  private fun deliverPickerResult(activity: ComponentActivity, uri: Uri) {
    // The photo picker activity is faked by dispatching the result directly to the launched
    // activity result request
    val shadowActivity = shadowOf(activity)
    val intentForResult = shadowActivity.nextStartedActivityForResult
    assertNotNull(intentForResult)
    shadowActivity.receiveResult(
      intentForResult.intent,
      Activity.RESULT_OK,
      Intent().setData(uri),
    )
  }
}
