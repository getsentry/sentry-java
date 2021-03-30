package io.sentry

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kotlin.test.Test

class ShutdownHookIntegrationTest {

    private class Fixture {
        val runtime = mock<Runtime>()
        val options = mock<SentryOptions>()
        val hub = mock<IHub>()

        fun getSut(): ShutdownHookIntegration {
            return ShutdownHookIntegration(runtime)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `registration attaches shutdown hook to runtime`() {
        val integration = fixture.getSut()

        integration.register(fixture.hub, fixture.options)

        verify(fixture.runtime).addShutdownHook(any())
    }

    @Test
    fun `registration removes shutdown hook from runtime`() {
        val integration = fixture.getSut()

        integration.register(fixture.hub, fixture.options)
        integration.close()

        verify(fixture.runtime).removeShutdownHook(any())
    }

    @Test
    fun `hook calls close`() {
        val integration = fixture.getSut()

        integration.register(fixture.hub, fixture.options)
        integration.hook.start()
        integration.hook.join()

        verify(fixture.hub).close()
    }
}
