package io.sentry.core

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kotlin.test.Test

class HubTest {
    @Test
    fun `when hub is initialized, integrations are registed`() {
        val integrationMock = mock<Integration>()
        val options = SentryOptions()
        options.dsn = "https://key@sentry.io/proj"
        options.addIntegration(integrationMock)
        val expected = Hub(options)
        verify(integrationMock).register(expected, options)
    }
}
