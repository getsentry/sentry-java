package io.sentry

import kotlin.test.Test
import kotlin.test.assertTrue

class OptionsContainerTest {
    @Test
    fun `When passing class to OptionsContainer, instance is created based on the type`() {
        assertTrue((OptionsContainer.create(SentryOptions::class.java)).createInstance() is SentryOptions)
    }
}
