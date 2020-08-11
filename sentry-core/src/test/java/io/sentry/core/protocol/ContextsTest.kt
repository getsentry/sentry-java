package io.sentry.core.protocol

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import org.junit.Test

class ContextsTest {
    @Test
    fun `cloning contexts wont have the same references`() {
        val contexts = Contexts()
        contexts.app = App()
        contexts.browser = Browser()
        contexts.device = Device()
        contexts.operatingSystem = OperatingSystem()
        contexts.runtime = SentryRuntime()
        contexts.gpu = Gpu()

        val clone = contexts.clone()

        assertNotNull(clone)
        assertNotSame(contexts, clone)
        assertNotSame(contexts.app, clone.app)
        assertNotSame(contexts.browser, clone.browser)
        assertNotSame(contexts.device, clone.device)
        assertNotSame(contexts.operatingSystem, clone.operatingSystem)
        assertNotSame(contexts.runtime, clone.runtime)
        assertNotSame(contexts.gpu, clone.gpu)
    }

    @Test
    fun `cloning contexts will have the same values`() {
        val contexts = Contexts()
        contexts["some-property"] = "some-value"

        val clone = contexts.clone()

        assertNotNull(clone)
        assertNotSame(contexts, clone)
        assertEquals(contexts["some-property"], clone["some-property"])
    }
}
