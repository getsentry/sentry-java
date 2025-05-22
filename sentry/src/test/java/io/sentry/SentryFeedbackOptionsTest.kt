package io.sentry

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
}
