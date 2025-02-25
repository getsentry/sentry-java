package io.sentry.protocol

import io.sentry.SpanContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

class ContextsTest {

    @Test
    fun `copying contexts wont have the same references`() {
        val contexts = Contexts()
        contexts.setApp(App())
        contexts.setBrowser(Browser())
        contexts.setDevice(Device())
        contexts.setOperatingSystem(OperatingSystem())
        contexts.setRuntime(SentryRuntime())
        contexts.setGpu(Gpu())
        contexts.setResponse(Response())
        contexts.setTrace(SpanContext("op"))
        contexts.setSpring(Spring())

        val clone = Contexts(contexts)

        assertNotNull(clone)
        assertNotSame(contexts, clone)
        assertNotSame(contexts.app, clone.app)
        assertNotSame(contexts.browser, clone.browser)
        assertNotSame(contexts.device, clone.device)
        assertNotSame(contexts.operatingSystem, clone.operatingSystem)
        assertNotSame(contexts.runtime, clone.runtime)
        assertNotSame(contexts.gpu, clone.gpu)
        assertNotSame(contexts.trace, clone.trace)
        assertNotSame(contexts.response, clone.response)
        assertNotSame(contexts.spring, clone.spring)
    }

    @Test
    fun `copying contexts will have the same values`() {
        val contexts = Contexts()
        contexts["some-property"] = "some-value"
        contexts.setTrace(SpanContext("op"))
        contexts.trace!!.description = "desc"

        val clone = Contexts(contexts)

        assertNotNull(clone)
        assertNotSame(contexts, clone)
        assertEquals(contexts["some-property"], clone["some-property"])
        assertEquals(contexts.trace!!.description, clone.trace!!.description)
    }
}
