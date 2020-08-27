package io.sentry.spring.boot

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.core.SentryEvent
import java.security.Principal
import kotlin.test.Test
import kotlin.test.assertEquals

class SentryUserHttpServletRequestProcessorTest {

    @Test
    fun `attaches user's IP address to Sentry Event`() {
        val eventProcessor = SentryUserHttpServletRequestProcessor(null, "192.168.0.1")
        val event = SentryEvent()

        eventProcessor.process(event, null)

        assertEquals("192.168.0.1", event.user.ipAddress)
    }

    @Test
    fun `attaches username to Sentry Event`() {
        val principal = mock<Principal>()
        whenever(principal.name).thenReturn("janesmith")

        val eventProcessor = SentryUserHttpServletRequestProcessor(principal, null)
        val event = SentryEvent()

        eventProcessor.process(event, null)

        assertEquals("janesmith", event.user.username)
    }
}
