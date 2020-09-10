package io.sentry

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kotlin.test.Test

class ShutdownHookIntegrationTest {

    @Test
    fun `registration attaches shutdown hook to runtime`() {
        val runtime = mock<Runtime>()
        val integration = ShutdownHookIntegration(runtime)

        integration.register(NoOpHub.getInstance(), SentryOptions())

        verify(runtime).addShutdownHook(any())
    }
}
