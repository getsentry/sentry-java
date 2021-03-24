package io.sentry.spring

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.IHub
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import java.net.URI
import javax.servlet.http.HttpServletRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.springframework.http.MediaType
import org.springframework.mock.web.MockServletContext
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.web.servlet.HandlerMapping

class SentryRequestHttpServletRequestProcessorTest {

    private class Fixture {
        val hub = mock<IHub>()

        fun getSut(request: HttpServletRequest, options: SentryOptions = SentryOptions()): SentryRequestHttpServletRequestProcessor {
            whenever(hub.options).thenReturn(options)
            return SentryRequestHttpServletRequestProcessor(request, SentryRequestResolver(hub))
        }
    }

    private val fixture = Fixture()

    @Test
    fun `attaches basic information from HTTP request to SentryEvent`() {
        val request = MockMvcRequestBuilders
            .get(URI.create("http://example.com?param1=xyz"))
            .header("some-header", "some-header value")
            .accept(MediaType.APPLICATION_JSON)
            .buildRequest(MockServletContext())
        val eventProcessor = fixture.getSut(request)
        val event = SentryEvent()

        eventProcessor.process(event, null)

        assertNotNull(event.request) {
            assertEquals("GET", it.method)
            assertEquals(mapOf(
                "some-header" to "some-header value",
                "Accept" to "application/json"
            ), it.headers)
            assertEquals("http://example.com", it.url)
            assertEquals("param1=xyz", it.queryString)
        }
    }

    @Test
    fun `attaches header with multiple values`() {
        val request = MockMvcRequestBuilders
            .get(URI.create("http://example.com?param1=xyz"))
            .header("another-header", "another value")
            .header("another-header", "another value2")
            .buildRequest(MockServletContext())
        val eventProcessor = fixture.getSut(request)
        val event = SentryEvent()

        eventProcessor.process(event, null)

        assertNotNull(event.request) {
            assertEquals(mapOf(
                "another-header" to "another value,another value2"
            ), it.headers)
        }
    }

    @Test
    fun `when sendDefaultPii is set to true, attaches cookies information`() {
        val request = MockMvcRequestBuilders
            .get(URI.create("http://example.com?param1=xyz"))
            .header("Cookie", "name=value")
            .header("Cookie", "name2=value2")
            .buildRequest(MockServletContext())
        val sentryOptions = SentryOptions()
        sentryOptions.isSendDefaultPii = true
        val eventProcessor = fixture.getSut(request, sentryOptions)
        val event = SentryEvent()

        eventProcessor.process(event, null)

        assertNotNull(event.request) {
            assertEquals("name=value,name2=value2", it.cookies)
        }
    }

    @Test
    fun `when sendDefaultPii is set to false, does not attach cookies`() {
        val request = MockMvcRequestBuilders
            .get(URI.create("http://example.com?param1=xyz"))
            .header("Cookie", "name=value")
            .buildRequest(MockServletContext())
        val sentryOptions = SentryOptions()
        sentryOptions.isSendDefaultPii = false
        val eventProcessor = fixture.getSut(request, sentryOptions)
        val event = SentryEvent()

        eventProcessor.process(event, null)

        assertNotNull(event.request) {
            assertNull(it.cookies)
        }
    }

    @Test
    fun `when sendDefaultPii is set to false, does not attach sensitive headers`() {
        val request = MockMvcRequestBuilders
            .get(URI.create("http://example.com?param1=xyz"))
            .header("some-header", "some-header value")
            .header("X-FORWARDED-FOR", "192.168.0.1")
            .header("authorization", "Token")
            .header("Authorization", "Token")
            .header("Cookie", "some cookies")
            .buildRequest(MockServletContext())
        val sentryOptions = SentryOptions()
        sentryOptions.isSendDefaultPii = false
        val eventProcessor = fixture.getSut(request, sentryOptions)
        val event = SentryEvent()

        eventProcessor.process(event, null)

        assertNotNull(event.request) {
            assertFalse(it.headers.containsKey("X-FORWARDED-FOR"))
            assertFalse(it.headers.containsKey("Authorization"))
            assertFalse(it.headers.containsKey("authorization"))
            assertFalse(it.headers.containsKey("Cookie"))
            assertTrue(it.headers.containsKey("some-header"))
        }
    }

    @Test
    fun `when event does not have transaction name, sets the transaction name from the current request`() {
        val request = MockMvcRequestBuilders
            .get(URI.create("http://example.com?param1=xyz"))
            .requestAttr(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/some-path")
            .buildRequest(MockServletContext())
        val eventProcessor = fixture.getSut(request)
        val event = SentryEvent()

        eventProcessor.process(event, null)

        assertNotNull(event.transaction)
        assertEquals("GET /some-path", event.transaction)
    }

    @Test
    fun `when event has transaction name set, does not overwrite transaction name with value from the current request`() {
        val request = MockMvcRequestBuilders
            .get(URI.create("http://example.com?param1=xyz"))
            .requestAttr(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/some-path")
            .buildRequest(MockServletContext())
        val eventProcessor = fixture.getSut(request)
        val event = SentryEvent()
        event.transaction = "some-transaction"

        eventProcessor.process(event, null)

        assertNotNull(event.transaction)
        assertEquals("some-transaction", event.transaction)
    }
}
