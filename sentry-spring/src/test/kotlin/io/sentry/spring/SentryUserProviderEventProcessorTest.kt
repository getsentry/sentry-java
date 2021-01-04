package io.sentry.spring

import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.protocol.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SentryUserProviderEventProcessorTest {

    class Fixture {

        fun getSut(isSendDefaultPii: Boolean = false, userProvider: () -> User?): SentryUserProviderEventProcessor {
            val options = SentryOptions()
            options.isSendDefaultPii = isSendDefaultPii
            return SentryUserProviderEventProcessor(options, userProvider)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when event user is null, provider user data is set`() {
        val processor = fixture.getSut {
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
        val processor = fixture.getSut {
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
        val processor = fixture.getSut {
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
        val processor = fixture.getSut {
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
        val processor = fixture.getSut {
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

    @Test
    fun `when isSendDefaultPii is true and user is not set, user remains null`() {
        val processor = fixture.getSut(isSendDefaultPii = true) {
            null
        }

        val event = SentryEvent()
        event.user = null

        val result = processor.process(event, null)

        assertNotNull(result)
        assertNull(result.user)
    }

    @Test
    fun `when isSendDefaultPii is true and user is set with custom ip address, user ip is unchanged`() {
        val processor = fixture.getSut(isSendDefaultPii = true) {
            null
        }

        val event = SentryEvent()
        val user = User()
        user.ipAddress = "192.168.0.1"
        event.user = user

        val result = processor.process(event, null)

        assertNotNull(result)
        assertEquals(user.ipAddress, result.user.ipAddress)
    }

    @Test
    fun `when isSendDefaultPii is true and user is set with {{auto}} ip address, user ip is set to null`() {
        val processor = fixture.getSut(isSendDefaultPii = true) {
            null
        }

        val event = SentryEvent()
        val user = User()
        user.ipAddress = "{{auto}}"
        event.user = user

        val result = processor.process(event, null)

        assertNotNull(result)
        assertNull(result.user.ipAddress)
    }
}
