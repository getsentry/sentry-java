package io.sentry.protocol

import io.sentry.ProfileContext
import io.sentry.SpanContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        contexts.profile = ProfileContext(SentryId())
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
        assertNotSame(contexts.profile, clone.profile)
        assertNotSame(contexts.response, clone.response)
        assertNotSame(contexts.spring, clone.spring)
    }

    @Test
    fun `copying contexts will have the same values`() {
        val contexts = Contexts()
        val id = SentryId()
        contexts["some-property"] = "some-value"
        contexts.setTrace(SpanContext("op"))
        contexts.trace!!.description = "desc"
        contexts.profile = ProfileContext(id)

        val clone = Contexts(contexts)

        assertNotNull(clone)
        assertNotSame(contexts, clone)
        assertEquals(contexts["some-property"], clone["some-property"])
        assertEquals(contexts.trace!!.description, clone.trace!!.description)
        assertEquals(contexts.profile!!.profilerId, clone.profile!!.profilerId)
    }

    @Test
    fun `set null value on context does not cause exception`() {
        val contexts = Contexts()
        contexts.set("k", null)
        assertFalse(contexts.containsKey("k"))
    }

    @Test
    fun `set null key on context does not cause exception`() {
        val contexts = Contexts()
        contexts.set(null, "v")
        assertFalse(contexts.containsKey(null))
    }

    @Test
    fun `set null key and value on context does not cause exception`() {
        val contexts = Contexts()
        contexts.set(null, null)
        assertFalse(contexts.containsKey(null))
    }

    @Test
    fun `put null value on context does not cause exception`() {
        val contexts = Contexts()
        contexts.put("k", null)
        assertFalse(contexts.containsKey("k"))
    }

    @Test
    fun `put null value on context removes previous value`() {
        val contexts = Contexts()
        contexts.put("k", "v")
        contexts.put("k", null)
        assertFalse(contexts.containsKey("k"))
    }

    @Test
    fun `put null key on context does not cause exception`() {
        val contexts = Contexts()
        contexts.put(null, "v")
        assertFalse(contexts.containsKey(null))
    }

    @Test
    fun `put null key and value on context does not cause exception`() {
        val contexts = Contexts()
        contexts.put(null, null)
        assertFalse(contexts.containsKey(null))
    }

    @Test
    fun `remove null key from context does not cause exception`() {
        val contexts = Contexts()
        contexts.remove(null)
    }

    @Test
    fun `putAll(null) contexts does not throw`() {
        val contexts = Contexts()
        val nullContexts: Contexts? = null
        contexts.putAll(nullContexts)
    }

    @Test
    fun `putAll(null) map does not throw`() {
        val contexts = Contexts()
        val nullMap: Map<String, Any>? = null
        contexts.putAll(nullMap)
    }

    @Test
    fun `putAll map with null key and value does not throw`() {
        val contexts = Contexts()
        val map = mutableMapOf(
            null to null,
            "k" to null,
            "a" to 1
        )
        contexts.putAll(map)

        assertEquals(listOf("a"), contexts.keys().toList())
    }
}
