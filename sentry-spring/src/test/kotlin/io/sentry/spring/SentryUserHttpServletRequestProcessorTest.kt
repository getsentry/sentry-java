package io.sentry.spring

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.core.SentryEvent
import io.sentry.core.SentryOptions
import java.security.Principal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SentryUserHttpServletRequestProcessorTest {

    @Test
    fun `attaches user's IP address to Sentry Event`() {
        val options = SentryOptions()
        options.isSendDefaultPii = true
        val eventProcessor = SentryUserHttpServletRequestProcessor(null, "192.168.0.1", options)
        val event = SentryEvent()

        eventProcessor.process(event, null)

        assertEquals("192.168.0.1", event.user.ipAddress)
    }

    @Test
    fun `attaches username to Sentry Event`() {
        val principal = mock<Principal>()
        whenever(principal.name).thenReturn("janesmith")

        val options = SentryOptions()
        options.isSendDefaultPii = true
        val eventProcessor = SentryUserHttpServletRequestProcessor(principal, null, options)
        val event = SentryEvent()

        eventProcessor.process(event, null)

        assertEquals("janesmith", event.user.username)
    }

    @Test
    fun `when sendDefaultPii is set to false, does not attach user data Sentry Event`() {
        val principal = mock<Principal>()
        whenever(principal.name).thenReturn("janesmith")

        val options = SentryOptions()
        options.isSendDefaultPii = false
        val eventProcessor = SentryUserHttpServletRequestProcessor(principal, null, options)
        val event = SentryEvent()

        eventProcessor.process(event, null)

        assertNull(event.user)
    }
}
