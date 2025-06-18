package io.sentry

import org.mockito.kotlin.mock
import kotlin.test.Test
import kotlin.test.assertEquals

class SentryFeedbackOptionsTest {
    @Test
    fun `feedback options is initialized with default values`() {
        val options = SentryFeedbackOptions()
        assertEquals(false, options.isNameRequired)
        assertEquals(true, options.isShowName)
        assertEquals(false, options.isEmailRequired)
        assertEquals(true, options.isShowEmail)
        assertEquals(true, options.isUseSentryUser)
        assertEquals(true, options.isShowBranding)
        assertEquals("Report a Bug", options.formTitle)
        assertEquals("Send Bug Report", options.submitButtonLabel)
        assertEquals("Cancel", options.cancelButtonLabel)
        assertEquals("Name", options.nameLabel)
        assertEquals("Your Name", options.namePlaceholder)
        assertEquals("Email", options.emailLabel)
        assertEquals("your.email@example.org", options.emailPlaceholder)
        assertEquals(" (Required)", options.isRequiredLabel)
        assertEquals("Description", options.messageLabel)
        assertEquals("What's the bug? What did you expect?", options.messagePlaceholder)
        assertEquals("Thank you for your report!", options.successMessageText)
        assertEquals(null, options.onFormOpen)
        assertEquals(null, options.onFormClose)
        assertEquals(null, options.onSubmitSuccess)
        assertEquals(null, options.onSubmitError)
    }

    @Test
    fun `feedback options copy constructor`() {
        val options =
            SentryFeedbackOptions().apply {
                isNameRequired = true
                isShowName = false
                isEmailRequired = true
                isShowEmail = false
                isUseSentryUser = false
                isShowBranding = false
                formTitle = "Title"
                submitButtonLabel = "Submit"
                cancelButtonLabel = "Cancel Label"
                nameLabel = "Name Label"
                namePlaceholder = "Name Placeholder"
                emailLabel = "Email Label"
                emailPlaceholder = "Email Placeholder"
                isRequiredLabel = "Required Label"
                messageLabel = "Message Label"
                messagePlaceholder = "Message Placeholder"
                successMessageText = "Success Message"
                onFormOpen = mock()
                onFormClose = mock()
                onSubmitSuccess = mock()
                onSubmitError = mock()
            }
        val optionsCopy = SentryFeedbackOptions(options)
        assertEquals(true, optionsCopy.isNameRequired)
        assertEquals(false, optionsCopy.isShowName)
        assertEquals(true, optionsCopy.isEmailRequired)
        assertEquals(false, optionsCopy.isShowEmail)
        assertEquals(false, optionsCopy.isUseSentryUser)
        assertEquals(false, optionsCopy.isShowBranding)
        assertEquals("Title", optionsCopy.formTitle)
        assertEquals("Submit", optionsCopy.submitButtonLabel)
        assertEquals("Cancel Label", optionsCopy.cancelButtonLabel)
        assertEquals("Name Label", optionsCopy.nameLabel)
        assertEquals("Name Placeholder", optionsCopy.namePlaceholder)
        assertEquals("Email Label", optionsCopy.emailLabel)
        assertEquals("Email Placeholder", optionsCopy.emailPlaceholder)
        assertEquals("Required Label", optionsCopy.isRequiredLabel)
        assertEquals("Message Label", optionsCopy.messageLabel)
        assertEquals("Message Placeholder", optionsCopy.messagePlaceholder)
        assertEquals("Success Message", optionsCopy.successMessageText)
        assertEquals(options.onFormOpen, optionsCopy.onFormOpen)
        assertEquals(options.onFormClose, optionsCopy.onFormClose)
        assertEquals(options.onSubmitSuccess, optionsCopy.onSubmitSuccess)
        assertEquals(options.onSubmitError, optionsCopy.onSubmitError)
    }
}
