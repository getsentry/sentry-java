package io.sentry.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

class SentryRuntimeTest {
    @Test
    fun `copying Sentry runtime wont have the same references`() {
        val runtime = SentryRuntime()
        val unknown = mapOf(Pair("unknown", "unknown"))
        runtime.setUnknown(unknown)

        val clone = SentryRuntime(runtime)

        assertNotNull(clone)
        assertNotSame(runtime, clone)

        assertNotSame(runtime.unknown, clone.unknown)
    }

    @Test
    fun `copying Sentry runtime system will have the same values`() {
        val runtime = SentryRuntime()
        runtime.name = "name"
        runtime.version = "version"
        runtime.rawDescription = "raw description"
        val unknown = mapOf(Pair("unknown", "unknown"))
        runtime.setUnknown(unknown)

        val clone = SentryRuntime(runtime)

        assertEquals("name", clone.name)
        assertEquals("version", clone.version)
        assertEquals("raw description", clone.rawDescription)
        assertNotNull(clone.unknown) {
            assertEquals("unknown", it["unknown"])
        }
    }
}
