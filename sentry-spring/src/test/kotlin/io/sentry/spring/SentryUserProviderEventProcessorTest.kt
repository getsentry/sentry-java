package io.sentry.spring

import io.sentry.core.SentryEvent
import io.sentry.core.protocol.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SentryUserProviderEventProcessorTest {

    @Test
    fun `when event user is null, provider user data is set`() {

        val processor = SentryUserProviderEventProcessor {
            val user = User()
            user.username = "john.doe"
            user.id = "user-id"
            user.ipAddress = "192.168.0.1"
            user.email = "john.doe@example.com"
            user.others = mapOf("key" to "value")
            user
        }

        val event = SentryEvent()
        val result = processor.process(event, null)

        assertNotNull(result)
        assertNotNull(result.user)
        assertEquals("john.doe", result.user.username)
        assertEquals("user-id", result.user.id)
        assertEquals("192.168.0.1", result.user.ipAddress)
        assertEquals("john.doe@example.com", result.user.email)
        assertEquals(mapOf("key" to "value"), result.user.others)
    }

    @Test
    fun `when event user is empty, provider user data is set`() {
        val processor = SentryUserProviderEventProcessor {
            val user = User()
            user.username = "john.doe"
            user.id = "user-id"
            user.ipAddress = "192.168.0.1"
            user.email = "john.doe@example.com"
            user.others = mapOf("key" to "value")
            user
        }

        val event = SentryEvent()
        event.user = User()
        val result = processor.process(event, null)

        assertNotNull(result)
        assertNotNull(result.user)
        assertEquals("john.doe", result.user.username)
        assertEquals("user-id", result.user.id)
        assertEquals("192.168.0.1", result.user.ipAddress)
        assertEquals("john.doe@example.com", result.user.email)
        assertEquals(mapOf("key" to "value"), result.user.others)
    }

    @Test
    fun `when processor returns empty User, user data is not changed`() {
        val processor = SentryUserProviderEventProcessor {
            val user = User()
            user
        }

        val event = SentryEvent()
        event.user = User()
        event.user.username = "jane.smith"
        event.user.id = "jane-smith"
        event.user.ipAddress = "192.168.0.3"
        event.user.email = "jane.smith@example.com"
        event.user.others = mapOf("key" to "value")

        val result = processor.process(event, null)

        assertNotNull(result)
        assertNotNull(result.user)
        assertEquals("jane.smith", result.user.username)
        assertEquals("jane-smith", result.user.id)
        assertEquals("192.168.0.3", result.user.ipAddress)
        assertEquals("jane.smith@example.com", result.user.email)
        assertEquals(mapOf("key" to "value"), result.user.others)
    }

    @Test
    fun `when processor returns null, user data is not changed`() {
        val processor = SentryUserProviderEventProcessor {
            null
        }

        val event = SentryEvent()
        event.user = User()
        event.user.username = "jane.smith"
        event.user.id = "jane-smith"
        event.user.ipAddress = "192.168.0.3"
        event.user.email = "jane.smith@example.com"
        event.user.others = mapOf("key" to "value")

        val result = processor.process(event, null)

        assertNotNull(result)
        assertNotNull(result.user)
        assertEquals("jane.smith", result.user.username)
        assertEquals("jane-smith", result.user.id)
        assertEquals("192.168.0.3", result.user.ipAddress)
        assertEquals("jane.smith@example.com", result.user.email)
        assertEquals(mapOf("key" to "value"), result.user.others)
    }

    @Test
    fun `merges user#others with existing user#others set on SentryEvent`() {
        val processor = SentryUserProviderEventProcessor {
            val user = User()
            user.others = mapOf("key" to "value")
            user
        }

        val event = SentryEvent()
        event.user = User()
        event.user.others = mapOf("new-key" to "new-value")

        val result = processor.process(event, null)

        assertNotNull(result)
        assertNotNull(result.user)
        assertEquals(mapOf("key" to "value", "new-key" to "new-value"), result.user.others)
    }
}
