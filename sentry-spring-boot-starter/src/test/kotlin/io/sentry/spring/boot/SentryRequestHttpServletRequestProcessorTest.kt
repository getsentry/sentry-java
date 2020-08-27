package io.sentry.spring.boot

import io.sentry.core.SentryEvent
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.http.MediaType
import org.springframework.mock.web.MockServletContext
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders

class SentryRequestHttpServletRequestProcessorTest {

    @Test
    fun `attaches basic information from HTTP request to SentryEvent`() {
        val request = MockMvcRequestBuilders
            .get(URI.create("http://example.com?param1=xyz"))
            .header("some-header", "some-header value")
            .accept(MediaType.APPLICATION_JSON)
            .buildRequest(MockServletContext())
        val eventProcessor = SentryRequestHttpServletRequestProcessor(request)
        val event = SentryEvent()

        eventProcessor.process(event, null)

        assertEquals("GET", event.request.method)
        assertEquals(mapOf(
            "some-header" to "some-header value",
            "Accept" to "application/json"
        ), event.request.headers)
        assertEquals("http://example.com", event.request.url)
        assertEquals("param1=xyz", event.request.queryString)
    }

    @Test
    fun `attaches header with multiple values`() {
        val request = MockMvcRequestBuilders
            .get(URI.create("http://example.com?param1=xyz"))
            .header("another-header", "another value")
            .header("another-header", "another value2")
            .buildRequest(MockServletContext())
        val eventProcessor = SentryRequestHttpServletRequestProcessor(request)
        val event = SentryEvent()

        eventProcessor.process(event, null)

        assertEquals(mapOf(
            "another-header" to "another value,another value2"
        ), event.request.headers)
    }

    @Test
    fun `attaches cookies information`() {
        val request = MockMvcRequestBuilders
            .get(URI.create("http://example.com?param1=xyz"))
            .header("Cookie", "name=value")
            .header("Cookie", "name2=value2")
            .buildRequest(MockServletContext())
        val eventProcessor = SentryRequestHttpServletRequestProcessor(request)
        val event = SentryEvent()

        eventProcessor.process(event, null)

        assertEquals("name=value,name2=value2", event.request.cookies)
    }
}
