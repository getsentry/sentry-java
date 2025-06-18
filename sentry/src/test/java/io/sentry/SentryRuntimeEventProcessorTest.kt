package io.sentry

import io.sentry.protocol.SentryRuntime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SentryRuntimeEventProcessorTest {
    private val eventProcessor = SentryRuntimeEventProcessor("16", "OpenJDK")

    @Test
    fun `when event does not have a runtime, sets runtime`() {
        val event = eventProcessor.process(SentryEvent(), null)
        assertNotNull(event) {
            assertNotNull(it.contexts.runtime) { runtime ->
                assertEquals("OpenJDK", runtime.name)
                assertEquals("16", runtime.version)
            }
        }
    }

    @Test
    fun `when event has runtime with null name and null version, sets runtime`() {
        val event = SentryEvent()
        event.contexts.setRuntime(SentryRuntime())
        val result = eventProcessor.process(event, null)
        assertNotNull(result) {
            assertNotNull(it.contexts.runtime) { runtime ->
                assertEquals("OpenJDK", runtime.name)
                assertEquals("16", runtime.version)
            }
        }
    }

    @Test
    fun `when event has runtime with null name and version set, does not change runtime`() {
        val event = SentryEvent()
        val runtime = SentryRuntime()
        runtime.version = "1.1"
        event.contexts.setRuntime(runtime)
        val result = eventProcessor.process(event, null)
        assertNotNull(result) {
            assertNotNull(it.contexts.runtime) { runtime ->
                assertNull(runtime.name)
                assertEquals("1.1", runtime.version)
            }
        }
    }

    @Test
    fun `when event has runtime with null version and name set, does not change runtime`() {
        val event = SentryEvent()
        val runtime = SentryRuntime()
        runtime.name = "Java"
        event.contexts.setRuntime(runtime)
        val result = eventProcessor.process(event, null)
        assertNotNull(result) {
            assertNotNull(it.contexts.runtime) { runtime ->
                assertNull(runtime.version)
                assertEquals("Java", runtime.name)
            }
        }
    }
}
