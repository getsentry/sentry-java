package io.sentry.uitest.android

import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryFeedbackOptions.SentryFeedbackCallback
import io.sentry.SentryOptions
import io.sentry.android.core.AndroidLogger
import io.sentry.android.core.R
import io.sentry.android.core.SentryUserFeedbackButton
import io.sentry.android.core.SentryUserFeedbackDialog
import io.sentry.assertEnvelopeFeedback
import io.sentry.protocol.User
import io.sentry.test.getProperty
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class UserFeedbackUiTest : BaseUiTest() {

    @Test
    fun userFeedbackNotShownWhenSdkDisabled() {
        launchActivity<EmptyActivity>().onActivity {
            SentryUserFeedbackDialog.Builder(it).create().show()
        }
        onView(withId(R.id.sentry_dialog_user_feedback_title))
            .check(doesNotExist())
    }

    @Test
    fun userFeedbackTextCustomizations() {
        initSentry {
            it.feedbackOptions.formTitle = "Test Form Title"
            it.feedbackOptions.submitButtonLabel = "Test Send Bug Report"
            it.feedbackOptions.cancelButtonLabel = "Test Cancel"
            it.feedbackOptions.nameLabel = "Test Name"
            it.feedbackOptions.namePlaceholder = "Test Your Name"
            it.feedbackOptions.emailLabel = "Test Email"
            it.feedbackOptions.emailPlaceholder = "Test your.email@example.org"
            it.feedbackOptions.messageLabel = "Test Description"
            it.feedbackOptions.messagePlaceholder = "Test What's the bug? What did you expect?"
            it.feedbackOptions.successMessageText = "Test Thank you for your report!"
            it.feedbackOptions.isRequiredLabel = " Test (Required)"
        }

        showDialogAndCheck {
            onView(withId(R.id.sentry_dialog_user_feedback_title))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                .check(matches(withText("Test Form Title")))
            onView(withId(R.id.sentry_dialog_user_feedback_btn_send))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                .check(matches(withText("Test Send Bug Report")))
            onView(withId(R.id.sentry_dialog_user_feedback_btn_cancel))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                .check(matches(withText("Test Cancel")))
            onView(withId(R.id.sentry_dialog_user_feedback_txt_name))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                .check(matches(withText("Test Name")))
            onView(withId(R.id.sentry_dialog_user_feedback_edt_name))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                .check(
                    matches(
                        allOf(
                            withHint("Test Your Name"),
                            withText("")
                        )
                    )
                )
            onView(withId(R.id.sentry_dialog_user_feedback_txt_email))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                .check(matches(withText("Test Email")))
            onView(withId(R.id.sentry_dialog_user_feedback_edt_email))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                .check(
                    matches(
                        allOf(
                            withHint("Test your.email@example.org"),
                            withText("")
                        )
                    )
                )
            onView(withId(R.id.sentry_dialog_user_feedback_txt_description))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                .check(matches(withText("Test Description Test (Required)")))
            onView(withId(R.id.sentry_dialog_user_feedback_edt_description))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                .check(
                    matches(
                        allOf(
                            withHint("Test What's the bug? What did you expect?"),
                            withText("")
                        )
                    )
                )
        }
    }

    @Test
    fun userFeedbackShowNameFalse() {
        initSentry {
            it.feedbackOptions.isShowName = false
        }

        showDialogAndCheck {
            checkViewVisibility(R.id.sentry_dialog_user_feedback_txt_name, true)
            checkViewVisibility(R.id.sentry_dialog_user_feedback_edt_name, true)

            // Ensure no other views are affected
            checkViewVisibility(R.id.sentry_dialog_user_feedback_title)
            checkViewVisibility(R.id.sentry_dialog_user_feedback_btn_send)
            checkViewVisibility(R.id.sentry_dialog_user_feedback_btn_cancel)
            checkViewVisibility(R.id.sentry_dialog_user_feedback_txt_email)
            checkViewVisibility(R.id.sentry_dialog_user_feedback_edt_email)
            checkViewVisibility(R.id.sentry_dialog_user_feedback_txt_description)
            checkViewVisibility(R.id.sentry_dialog_user_feedback_edt_description)
            checkViewVisibility(R.id.sentry_dialog_user_feedback_logo)
        }
    }

    @Test
    fun userFeedbackShowEmailFalse() {
        initSentry {
            it.feedbackOptions.isShowEmail = false
        }

        showDialogAndCheck {
            checkViewVisibility(R.id.sentry_dialog_user_feedback_txt_email, true)
            checkViewVisibility(R.id.sentry_dialog_user_feedback_edt_email, true)

            // Ensure no other views are affected
            checkViewVisibility(R.id.sentry_dialog_user_feedback_title)
            checkViewVisibility(R.id.sentry_dialog_user_feedback_btn_send)
            checkViewVisibility(R.id.sentry_dialog_user_feedback_btn_cancel)
            checkViewVisibility(R.id.sentry_dialog_user_feedback_txt_name)
            checkViewVisibility(R.id.sentry_dialog_user_feedback_edt_name)
            checkViewVisibility(R.id.sentry_dialog_user_feedback_txt_description)
            checkViewVisibility(R.id.sentry_dialog_user_feedback_edt_description)
            checkViewVisibility(R.id.sentry_dialog_user_feedback_logo)
        }
    }

    @Test
    fun userFeedbackShowBrandingFalse() {
        initSentry {
            it.feedbackOptions.isShowBranding = false
        }

        showDialogAndCheck {
            checkViewVisibility(R.id.sentry_dialog_user_feedback_logo, true)

            // Ensure no other views are affected
            checkViewVisibility(R.id.sentry_dialog_user_feedback_title)
            checkViewVisibility(R.id.sentry_dialog_user_feedback_btn_send)
            checkViewVisibility(R.id.sentry_dialog_user_feedback_btn_cancel)
            checkViewVisibility(R.id.sentry_dialog_user_feedback_txt_name)
            checkViewVisibility(R.id.sentry_dialog_user_feedback_edt_name)
            checkViewVisibility(R.id.sentry_dialog_user_feedback_txt_email)
            checkViewVisibility(R.id.sentry_dialog_user_feedback_edt_email)
            checkViewVisibility(R.id.sentry_dialog_user_feedback_txt_description)
            checkViewVisibility(R.id.sentry_dialog_user_feedback_edt_description)
        }
    }

    @Test
    fun userFeedbackNameRequired() {
        var anyCallbackCalled = false

        initSentry {
            it.feedbackOptions.isRequiredLabel = " Test (Required)"
            it.feedbackOptions.isNameRequired = true
            it.feedbackOptions.nameLabel = "Label of Name"
            // Requiring name should show the name field
            it.feedbackOptions.isShowName = false

            it.feedbackOptions.onSubmitError = SentryFeedbackCallback { anyCallbackCalled = true }
            it.feedbackOptions.onSubmitSuccess = SentryFeedbackCallback { anyCallbackCalled = true }
            it.feedbackOptions.onFormClose = Runnable { anyCallbackCalled = true }
        }

        // Name is required
        showDialogAndCheck {
            // So it shows the required label and force its visibility to VISIBLE
            onView(withId(R.id.sentry_dialog_user_feedback_txt_name))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                .check(matches(withText("Label of Name Test (Required)")))
            checkViewVisibility(R.id.sentry_dialog_user_feedback_edt_name)

            // And if not filled
            fillFormAndSend(fillName = false)

            // No callbacks should be called (onSubmitError, onSubmitSuccess, onFormClose)
            assertFalse(anyCallbackCalled)

            // And EditText shows the required label
            onView(withId(R.id.sentry_dialog_user_feedback_edt_name))
                .check(matches(withError("Label of Name Test (Required)")))
        }
    }

    @Test
    fun userFeedbackEmailRequired() {
        var anyCallbackCalled = false

        initSentry {
            it.feedbackOptions.isRequiredLabel = " Test (Required)"
            it.feedbackOptions.isEmailRequired = true
            it.feedbackOptions.emailLabel = "Label of Email"
            // Requiring name should show the name field
            it.feedbackOptions.isShowEmail = false

            it.feedbackOptions.onSubmitError = SentryFeedbackCallback { anyCallbackCalled = true }
            it.feedbackOptions.onSubmitSuccess = SentryFeedbackCallback { anyCallbackCalled = true }
            it.feedbackOptions.onFormClose = Runnable { anyCallbackCalled = true }
        }

        // Email is required
        showDialogAndCheck {
            // So it shows the required label and force its visibility to VISIBLE
            onView(withId(R.id.sentry_dialog_user_feedback_txt_email))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                .check(matches(withText("Label of Email Test (Required)")))
            checkViewVisibility(R.id.sentry_dialog_user_feedback_edt_email)

            // And if not filled
            fillFormAndSend(fillEmail = false)

            // No callbacks should be called (onSubmitError, onSubmitSuccess, onFormClose)
            assertFalse(anyCallbackCalled)

            // And EditText shows the required label
            onView(withId(R.id.sentry_dialog_user_feedback_edt_email))
                .check(matches(withError("Label of Email Test (Required)")))
        }
    }

    @Test
    fun userFeedbackMessageRequired() {
        var anyCallbackCalled = false

        initSentry {
            it.feedbackOptions.isRequiredLabel = " Test (Required)"
            it.feedbackOptions.messageLabel = "Label of Description"

            it.feedbackOptions.onSubmitError = SentryFeedbackCallback { anyCallbackCalled = true }
            it.feedbackOptions.onSubmitSuccess = SentryFeedbackCallback { anyCallbackCalled = true }
            it.feedbackOptions.onFormClose = Runnable { anyCallbackCalled = true }
        }

        // message is required
        showDialogAndCheck {
            // So it shows the required label and force its visibility to VISIBLE
            onView(withId(R.id.sentry_dialog_user_feedback_txt_description))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                .check(matches(withText("Label of Description Test (Required)")))
            checkViewVisibility(R.id.sentry_dialog_user_feedback_edt_description)

            // And if not filled
            fillFormAndSend(fillDescription = false)

            // No callbacks should be called (onSubmitError, onSubmitSuccess, onFormClose)
            assertFalse(anyCallbackCalled)

            // And EditText shows the required label
            onView(withId(R.id.sentry_dialog_user_feedback_edt_description))
                .check(matches(withError("Label of Description Test (Required)")))
        }
    }

    @Test
    fun userFeedbackSetOnDismissListenerNotOverwriteFormClose() {
        var customListenerCalled = false
        var formClose = false

        initSentry {
            it.feedbackOptions.onFormClose = Runnable { formClose = true }
            it.beforeSendFeedback = SentryOptions.BeforeSendCallback { _, _ -> null }
        }

        showDialogAndCheck { dialog ->
            // Set a custom listener. This should not overwrite the form close callback
            dialog.setOnDismissListener { customListenerCalled = true }

            assertFalse(formClose)
            assertFalse(customListenerCalled)
            // When the dialog is dismissed
            fillFormAndSend()
            // The form close callback should be called
            assertTrue(formClose)
            // The custom listener should be called too
            assertTrue(customListenerCalled)
        }
    }

    @Test
    fun userFeedbackCancel() {
        var formOpen = false
        var submitError = false
        var submitSuccess = false
        var formClose = false

        initSentry {
            it.feedbackOptions.onFormOpen = Runnable { formOpen = true }
            it.feedbackOptions.onSubmitError = SentryFeedbackCallback { submitError = true }
            it.feedbackOptions.onSubmitSuccess = SentryFeedbackCallback { submitSuccess = true }
            it.feedbackOptions.onFormClose = Runnable { formClose = true }

            // Let's drop the feedback to test the submit error callback
            it.beforeSendFeedback = SentryOptions.BeforeSendCallback { _, _ -> null }
        }

        assertFalse(formOpen)
        // Open the dialog, and call the onFormOpen callback
        showDialogAndCheck {
            assertTrue(formOpen)

            assertFalse(formClose)
            assertFalse(submitError)
            assertFalse(submitSuccess)

            // Let's click on the cancel button
            onView(withId(R.id.sentry_dialog_user_feedback_btn_cancel))
                .perform(click())

            // The form close callback should be called
            assertTrue(formClose)
            // But the submit callbacks should not
            assertFalse(submitError)
            assertFalse(submitSuccess)

            // And the dialog should be dismissed
            onView(withId(R.id.sentry_dialog_user_feedback_layout))
                .check(doesNotExist())
        }
    }

    @Test
    fun userFeedbackSendError() {
        var formOpen = false
        var submitError = false
        var submitSuccess = false
        var formClose = false

        initSentry {
            it.feedbackOptions.onFormOpen = Runnable { formOpen = true }
            it.feedbackOptions.onSubmitError = SentryFeedbackCallback { submitError = true }
            it.feedbackOptions.onSubmitSuccess = SentryFeedbackCallback { submitSuccess = true }
            it.feedbackOptions.onFormClose = Runnable { formClose = true }

            // Let's drop the feedback to test the submit error callback
            it.beforeSendFeedback = SentryOptions.BeforeSendCallback { _, _ -> null }
        }

        assertFalse(formOpen)
        // Open the dialog, and call the onFormOpen callback
        showDialogAndCheck {
            assertTrue(formOpen)

            assertFalse(formClose)
            assertFalse(submitError)
            assertFalse(submitSuccess)
            // Sending the feedback will close the dialog, regardless of success
            fillFormAndSend()
            assertTrue(formClose)
            // Sending the feedback failed because of beforeSendFeedback
            assertTrue(submitError)
            assertFalse(submitSuccess)
        }
    }

    @Test
    fun userFeedbackSendSuccess() {
        var formOpen = false
        var submitError = false
        var submitSuccess = false
        var formClose = false

        initSentry {
            it.feedbackOptions.onFormOpen = Runnable { formOpen = true }
            it.feedbackOptions.onSubmitError = SentryFeedbackCallback { submitError = true }
            it.feedbackOptions.onSubmitSuccess = SentryFeedbackCallback { submitSuccess = true }
            it.feedbackOptions.onFormClose = Runnable { formClose = true }

            it.feedbackOptions.successMessageText = "Test Thank you for your report!"
        }

        assertFalse(formOpen)
        // Open the dialog, and call the onFormOpen callback
        showDialogAndCheck {
            assertTrue(formOpen)

            assertFalse(formClose)
            assertFalse(submitError)
            assertFalse(submitSuccess)
            // Sending the feedback will close the dialog, regardless of success
            fillFormAndSend()
            assertTrue(formClose)
            assertFalse(submitError)
            // Sending the feedback succeeded
            assertTrue(submitSuccess)

            // Toasts are too much of an hassle to test
        }
    }

    @Test
    fun userFeedbackUseSentryUser() {
        initSentry {
            it.feedbackOptions.isUseSentryUser = true
            it.feedbackOptions.isShowEmail = false
        }
        // When a user is set in Sentry
        Sentry.setUser(
            User().apply {
                name = "Test User"
                email = "Test User Email"
            }
        )
        showDialogAndCheck {
            // Name and email are filled with Sentry user properties
            onView(withId(R.id.sentry_dialog_user_feedback_edt_name))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                .check(matches(withText("Test User")))

            // Even if the field is hidden
            onView(withId(R.id.sentry_dialog_user_feedback_edt_email))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))
                .check(matches(withText("Test User Email")))
        }
    }

    @Test
    fun userFeedbackUseSentryUserFalse() {
        initSentry {
            it.feedbackOptions.isUseSentryUser = false
        }
        // When a user is set in Sentry
        Sentry.setUser(
            User().apply {
                name = "Test User"
                email = "Test User Email"
            }
        )
        showDialogAndCheck {
            // Name and email are not filled
            onView(withId(R.id.sentry_dialog_user_feedback_edt_name))
                .check(matches(withText("")))

            onView(withId(R.id.sentry_dialog_user_feedback_edt_email))
                .check(matches(withText("")))
        }
    }

    @Test
    fun userFeedbackSendEnvelope() {
        // GH actions emulator don't allow capturing screenshots properly
        val enableReplay = BuildConfig.ENVIRONMENT != "github"
        initSentry(true) {
            // When sending the feedback we want to wait for relay to receive it.
            // We can't simply increment the idling resource,
            //  because it would block the espresso interactions (button click)
            it.feedbackOptions.onSubmitSuccess = SentryFeedbackCallback { relayIdlingResource.increment() }
            // Let's capture a replay, so we can check the replayId in the feedback
            if (enableReplay) {
                it.sessionReplay.sessionSampleRate = 1.0
            }
        }

        showDialogAndCheck {
            // Send the feedback
            fillFormAndSend()
        }

        relay.assert {
            findEnvelope {
                assertEnvelopeFeedback(
                    it.items.toList(),
                    AndroidLogger()
                ).contexts.feedback!!.message == "Description filled"
            }.assert {
                val event: SentryEvent = it.assertItem()
                val feedback = event.contexts.feedback!!
                it.assertNoOtherItems()
                assertEquals("Description filled", feedback.message)
                assertEquals("Name filled", feedback.name)
                assertEquals("Email filled", feedback.contactEmail)
                assertEquals("Description filled", feedback.message)
                // The screen name should be set in the url
                assertEquals("io.sentry.uitest.android.EmptyActivity", feedback.url)

                if (enableReplay) {
                    // The current replay should be set in the replayId
                    assertNotNull(feedback.replayId)
                    assertEquals(Sentry.getCurrentScopes().options.replayController.replayId, feedback.replayId)
                }
            }
        }
    }

    @Test
    fun userFeedbackWidgetDefaults() {
        initSentry()
        var widgetId = 0
        showWidgetAndCheck { widget ->
            widgetId = widget.id
            val densityScale = context.resources.displayMetrics.density
            assertEquals((densityScale * 4).toInt(), widget.compoundDrawablePadding)

            assertNotNull(widget.compoundDrawables[0]) // Drawable left
            assertNull(widget.compoundDrawables[1]) // Drawable top
            assertNull(widget.compoundDrawables[2]) // Drawable right
            assertNull(widget.compoundDrawables[3]) // Drawable bottom

            // Couldn't find a reliable way to check the drawable, so i'll skip it

            assertFalse(widget.isAllCaps)

            assertEquals(R.drawable.sentry_oval_button_ripple_background, widget.getProperty<Int>("mBackgroundResource"))

            assertEquals((densityScale * 12).toInt(), widget.paddingStart)
            assertEquals((densityScale * 12).toInt(), widget.paddingEnd)
            assertEquals((densityScale * 12).toInt(), widget.paddingTop)
            assertEquals((densityScale * 12).toInt(), widget.paddingBottom)

            val typedValue = TypedValue()
            widget.context.theme.resolveAttribute(android.R.attr.colorForeground, typedValue, true)
            assertEquals(typedValue.data, widget.currentTextColor)

            assertEquals("Report a Bug", widget.text)
        }

        onView(withId(widgetId)).perform(click())
        // Check that the user feedback dialog is shown
        checkViewVisibility(R.id.sentry_dialog_user_feedback_layout)
    }

    @Test
    fun userFeedbackWidgetDefaultsOverridden() {
        initSentry()
        showWidgetAndCheck({ widget ->
            widget.compoundDrawablePadding = 1
            widget.setCompoundDrawables(null, null, null, null)
            widget.isAllCaps = true
            widget.setBackgroundResource(R.drawable.sentry_edit_text_border)
            widget.setTextColor(Color.RED)
            widget.text = "My custom text"
            widget.setPadding(0, 0, 0, 0)
        }) { widget ->
            val densityScale = context.resources.displayMetrics.density
            assertEquals(1, widget.compoundDrawablePadding)

            assertNull(widget.compoundDrawables[0]) // Drawable left
            assertNull(widget.compoundDrawables[1]) // Drawable top
            assertNull(widget.compoundDrawables[2]) // Drawable right
            assertNull(widget.compoundDrawables[3]) // Drawable bottom

            assertTrue(widget.isAllCaps)

            assertEquals(R.drawable.sentry_edit_text_border, widget.getProperty<Int>("mBackgroundResource"))

            assertEquals((densityScale * 0).toInt(), widget.paddingStart)
            assertEquals((densityScale * 0).toInt(), widget.paddingEnd)
            assertEquals((densityScale * 0).toInt(), widget.paddingTop)
            assertEquals((densityScale * 0).toInt(), widget.paddingBottom)

            assertEquals(Color.RED, widget.currentTextColor)

            assertEquals("My custom text", widget.text)
        }
    }

    @Test
    fun userFeedbackWidgetShowsDialogOnClickOverridden() {
        initSentry()
        var widgetId = 0
        var customListenerCalled = false
        showWidgetAndCheck { widget ->
            widgetId = widget.id
            widget.setOnClickListener { customListenerCalled = true }
        }

        onView(withId(widgetId)).perform(click())
        // Check that the user feedback dialog is shown
        checkViewVisibility(R.id.sentry_dialog_user_feedback_layout)
        // And the custom listener is called, too
        assertTrue(customListenerCalled)
    }

    private fun checkViewVisibility(viewId: Int, isGone: Boolean = false) {
        onView(withId(viewId))
            .check(matches(withEffectiveVisibility(if (isGone) Visibility.GONE else Visibility.VISIBLE)))
    }

    private fun fillFormAndSend(
        fillName: Boolean = true,
        fillEmail: Boolean = true,
        fillDescription: Boolean = true
    ) {
        if (fillName) {
            onView(withId(R.id.sentry_dialog_user_feedback_edt_name))
                .perform(replaceText("Name filled"))
        }
        if (fillEmail) {
            onView(withId(R.id.sentry_dialog_user_feedback_edt_email))
                .perform(replaceText("Email filled"))
        }
        if (fillDescription) {
            onView(withId(R.id.sentry_dialog_user_feedback_edt_description))
                .perform(replaceText("Description filled"))
        }
        onView(withId(R.id.sentry_dialog_user_feedback_btn_send))
            .perform(click())
    }

    private fun showDialogAndCheck(checker: (dialog: SentryUserFeedbackDialog) -> Unit = {}) {
        lateinit var dialog: SentryUserFeedbackDialog
        val feedbackScenario = launchActivity<EmptyActivity>()
        feedbackScenario.onActivity {
            dialog = SentryUserFeedbackDialog.Builder(it).create()
            dialog.show()
        }

        onView(withId(R.id.sentry_dialog_user_feedback_layout))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        checker(dialog)
    }

    private fun showWidgetAndCheck(widgetConfig: ((widget: SentryUserFeedbackButton) -> Unit)? = null, checker: (widget: SentryUserFeedbackButton) -> Unit = {}) {
        val buttonId = Int.MAX_VALUE - 1
        val feedbackScenario = launchActivity<EmptyActivity>()
        feedbackScenario.onActivity {
            val view = LinearLayout(it).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    SentryUserFeedbackButton(it).apply {
                        id = buttonId
                        widgetConfig?.invoke(this)
                    }
                )
            }
            it.setContentView(view)
        }
        checkViewVisibility(buttonId)
        onView(withId(buttonId))
            .check(matches(isDisplayed()))
            .check { view, _ ->
                checker(view as SentryUserFeedbackButton)
            }
    }

    fun withError(expectedError: String): Matcher<View> {
        return object : BoundedMatcher<View, EditText>(EditText::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("with error: $expectedError")
            }

            override fun matchesSafely(editText: EditText): Boolean {
                val error = editText.error?.toString()
                return expectedError == error
            }
        }
    }
}
