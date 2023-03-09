package io.sentry

import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ShutdownHookIntegrationTest {

    private class Fixture {
        val runtime = mock<Runtime>()
        val options = SentryOptions()
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
    fun `registration does not attach shutdown hook to runtime if disabled`() {
        val integration = fixture.getSut()
        fixture.options.isEnableShutdownHook = false

        integration.register(fixture.hub, fixture.options)

        verify(fixture.runtime, never()).addShutdownHook(any())
    }

    @Test
    fun `registration removes shutdown hook from runtime`() {
        val integration = fixture.getSut()

        integration.register(fixture.hub, fixture.options)
        integration.close()

        verify(fixture.runtime).removeShutdownHook(any())
    }

    @Test
    fun `hook calls flush`() {
        val integration = fixture.getSut()

        integration.register(fixture.hub, fixture.options)
        assertNotNull(integration.hook) {
            it.start()
            it.join()
        }

        verify(fixture.hub).flush(any())
    }

    @Test
    fun `hook calls flush with given timeout`() {
        val integration = fixture.getSut()
        fixture.options.flushTimeoutMillis = 10000

        integration.register(fixture.hub, fixture.options)
        assertNotNull(integration.hook) {
            it.start()
            it.join()
        }

        verify(fixture.hub).flush(eq(10000))
    }

    @Test
    fun `shutdown in progress is handled gracefully`() {
        val integration = fixture.getSut()
        whenever(fixture.runtime.removeShutdownHook(any())).thenThrow(java.lang.IllegalStateException("Shutdown in progress"))

        integration.register(fixture.hub, fixture.options)
        integration.close()

        verify(fixture.runtime).removeShutdownHook(any())
    }

    @Test
    fun `non shutdown in progress during removeShutdownHook is rethrown`() {
        val integration = fixture.getSut()
        whenever(fixture.runtime.removeShutdownHook(any())).thenThrow(java.lang.IllegalStateException())

        integration.register(fixture.hub, fixture.options)

        assertFails {
            integration.close()
        }

        verify(fixture.runtime).removeShutdownHook(any())
    }

    @Test
    fun `Integration adds itself to integration list`() {
        val integration = fixture.getSut()

        integration.register(fixture.hub, fixture.options)

        assertTrue(
            fixture.options.sdkVersion!!.integrationSet.contains("ShutdownHook")
        )
    }
}
