package io.sentry.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

class SentryRuntimeTest {
    @Test
    fun `cloning Sentry runtime wont have the same references`() {
        val runtime = SentryRuntime()
        val unknown = mapOf(Pair("unknown", "unknown"))
        runtime.acceptUnknownProperties(unknown)

        val clone = runtime.clone()

        assertNotNull(clone)
        assertNotSame(runtime, clone)

        assertNotSame(runtime.unknown, clone.unknown)
    }

    @Test
    fun `cloning Sentry runtime system will have the same values`() {
        val runtime = SentryRuntime()
        runtime.name = "name"
        runtime.version = "version"
        runtime.rawDescription = "raw description"
        val unknown = mapOf(Pair("unknown", "unknown"))
        runtime.acceptUnknownProperties(unknown)

        val clone = runtime.clone()

        assertEquals("name", clone.name)
        assertEquals("version", clone.version)
        assertEquals("raw description", clone.rawDescription)
        assertEquals("unknown", clone.unknown["unknown"])
    }
}
