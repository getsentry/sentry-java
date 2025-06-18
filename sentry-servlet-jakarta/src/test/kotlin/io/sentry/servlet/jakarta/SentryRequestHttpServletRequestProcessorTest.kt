package io.sentry.servlet.jakarta

import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import jakarta.servlet.http.HttpServletRequest
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.net.URI
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentryRequestHttpServletRequestProcessorTest {
    @Test
    fun `attaches basic information from HTTP request to SentryEvent`() {
        val request =
            mockRequest(
                url = "http://example.com?param1=xyz",
                headers =
                    mapOf(
                        "some-header" to "some-header value",
                        "Accept" to "application/json",
                    ),
            )
        val eventProcessor = SentryRequestHttpServletRequestProcessor(request)
        val event = SentryEvent()

        eventProcessor.process(event, Hint())

        assertNotNull(event.request)
        val eventRequest = event.request!!
        assertEquals("GET", eventRequest.method)
        assertEquals(
            mapOf(
                "some-header" to "some-header value",
                "Accept" to "application/json",
            ),
            eventRequest.headers,
        )
        assertEquals("http://example.com", eventRequest.url)
        assertEquals("param1=xyz", eventRequest.queryString)
    }

    @Test
    fun `attaches header with multiple values`() {
        val request =
            mockRequest(
                url = "http://example.com?param1=xyz",
                headers =
                    mapOf(
                        "another-header" to listOf("another value", "another value2"),
                    ),
            )
        val eventProcessor = SentryRequestHttpServletRequestProcessor(request)
        val event = SentryEvent()

        eventProcessor.process(event, Hint())

        assertNotNull(event.request) {
            assertEquals(
                mapOf(
                    "another-header" to "another value,another value2",
                ),
                it.headers,
            )
        }
    }

    @Test
    fun `does not attach cookies`() {
        val request =
            mockRequest(
                url = "http://example.com?param1=xyz",
                headers =
                    mapOf(
                        "Cookie" to "name=value",
                    ),
            )
        val sentryOptions = SentryOptions()
        sentryOptions.isSendDefaultPii = false
        val eventProcessor = SentryRequestHttpServletRequestProcessor(request)
        val event = SentryEvent()

        eventProcessor.process(event, Hint())

        assertNotNull(event.request) {
            assertNull(it.cookies)
        }
    }

    @Test
    fun `does not attach sensitive headers`() {
        val request =
            mockRequest(
                url = "http://example.com?param1=xyz",
                headers =
                    mapOf(
                        "some-header" to "some-header value",
                        "X-FORWARDED-FOR" to "192.168.0.1",
                        "authorization" to "Token",
                        "Authorization" to "Token",
                        "Cookie" to "some cookies",
                    ),
            )
        val sentryOptions = SentryOptions()
        sentryOptions.isSendDefaultPii = false
        val eventProcessor = SentryRequestHttpServletRequestProcessor(request)
        val event = SentryEvent()

        eventProcessor.process(event, Hint())

        assertNotNull(event.request) { req ->
            assertNotNull(req.headers) {
                assertFalse(it.containsKey("X-FORWARDED-FOR"))
                assertFalse(it.containsKey("Authorization"))
                assertFalse(it.containsKey("authorization"))
                assertFalse(it.containsKey("Cookies"))
                assertTrue(it.containsKey("some-header"))
            }
        }
    }
}

fun mockRequest(
    url: String,
    method: String = "GET",
    headers: Map<String, Any?> = emptyMap(),
): HttpServletRequest {
    val uri = URI(url)
    val request = mock<HttpServletRequest>()
    whenever(request.method).thenReturn(method)
    whenever(request.scheme).thenReturn(uri.scheme)
    whenever(request.serverName).thenReturn(uri.host)
    whenever(request.serverPort).thenReturn(uri.port)
    whenever(request.requestURI).thenReturn(uri.rawPath)
    whenever(request.queryString).thenReturn(uri.rawQuery)

    whenever(request.headerNames).thenReturn(Collections.enumeration(headers.keys))
    whenever(request.getHeaders(any())).then { invocation ->
        when (val headerValue = headers[invocation.arguments.first()]) {
            is Collection<*> -> Collections.enumeration(headerValue)
            else -> Collections.enumeration(listOf(headerValue))
        }
    }

    whenever(request.requestURL).thenReturn(toRequestUrl(uri))

    return request
}

fun toRequestUrl(uri: URI): StringBuffer? {
    val scheme: String = uri.scheme
    val server: String = uri.host
    val port: Int = uri.port
    val uri: String = uri.rawPath
    val url = StringBuffer(scheme).append("://").append(server)
    if (port > 0 &&
        (
            "http".equals(scheme, ignoreCase = true) &&
                port != 80 ||
                "https".equals(scheme, ignoreCase = true) &&
                port != 443
        )
    ) {
        url.append(':').append(port)
    }

    if (uri?.isNotBlank()) {
        url.append(uri)
    }
    return url
}
