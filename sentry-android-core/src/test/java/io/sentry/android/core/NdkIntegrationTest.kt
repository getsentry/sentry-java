package io.sentry.android.core

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import io.sentry.core.ILogger
import io.sentry.core.SentryLevel
import io.sentry.core.SentryOptions
import kotlin.test.Test

class NdkIntegrationTest {

    @Test
    fun `NdkIntegration won't throw exception`() {
        // hard to test, lets just check that its not throwing anything
        val integration = NdkIntegration()
        val logger = mock<ILogger>()
        val options = SentryOptions()
        options.setLogger(logger)
        integration.register(mock(), options)
        verify(logger, never()).log(eq(SentryLevel.ERROR), eq("Failed to initialize SentryNdk."), any())
    }
}
