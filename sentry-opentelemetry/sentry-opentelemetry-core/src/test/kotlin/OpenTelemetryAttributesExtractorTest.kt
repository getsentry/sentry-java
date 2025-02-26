package io.sentry.opentelemetry

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.internal.AttributesMap
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.ServerAttributes
import io.opentelemetry.semconv.UrlAttributes
import io.sentry.Scope
import io.sentry.SentryOptions
import io.sentry.protocol.Request
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OpenTelemetryAttributesExtractorTest {

    private class Fixture {
        val spanData = mock<SpanData>()
        val attributes = AttributesMap.create(100, 100)
        val options = SentryOptions.empty()
        val scope = Scope(options)

        init {
            whenever(spanData.attributes).thenReturn(attributes)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `sets URL based on OTel attributes`() {
        givenAttributes(
            mapOf(
                HttpAttributes.HTTP_REQUEST_METHOD to "GET",
                UrlAttributes.URL_SCHEME to "https",
                UrlAttributes.URL_PATH to "/path/to/123",
                UrlAttributes.URL_QUERY to "q=123456&b=X",
                ServerAttributes.SERVER_ADDRESS to "io.sentry",
                ServerAttributes.SERVER_PORT to 8081L
            )
        )

        whenExtractingAttributes()

        thenRequestIsSet()
        thenUrlIsSetTo("https://io.sentry:8081/path/to/123")
        thenQueryIsSetTo("q=123456&b=X")
    }

    @Test
    fun `when there is an existing request on scope it is filled with more details`() {
        fixture.scope.request = Request().also { it.bodySize = 123L }
        givenAttributes(
            mapOf(
                HttpAttributes.HTTP_REQUEST_METHOD to "GET",
                UrlAttributes.URL_SCHEME to "https",
                UrlAttributes.URL_PATH to "/path/to/123",
                UrlAttributes.URL_QUERY to "q=123456&b=X",
                ServerAttributes.SERVER_ADDRESS to "io.sentry",
                ServerAttributes.SERVER_PORT to 8081L
            )
        )

        whenExtractingAttributes()

        thenRequestIsSet()
        thenUrlIsSetTo("https://io.sentry:8081/path/to/123")
        thenQueryIsSetTo("q=123456&b=X")
        assertEquals(123L, fixture.scope.request!!.bodySize)
    }

    @Test
    fun `when there is an existing request with url on scope it is kept`() {
        fixture.scope.request = Request().also {
            it.url = "http://docs.sentry.io:3000/platform"
            it.queryString = "s=abc"
        }
        givenAttributes(
            mapOf(
                HttpAttributes.HTTP_REQUEST_METHOD to "GET",
                UrlAttributes.URL_SCHEME to "https",
                UrlAttributes.URL_PATH to "/path/to/123",
                UrlAttributes.URL_QUERY to "q=123456&b=X",
                ServerAttributes.SERVER_ADDRESS to "io.sentry",
                ServerAttributes.SERVER_PORT to 8081L
            )
        )

        whenExtractingAttributes()

        thenRequestIsSet()
        thenUrlIsSetTo("http://docs.sentry.io:3000/platform")
        thenQueryIsSetTo("s=abc")
    }

    @Test
    fun `when there is an existing request with url on scope it is kept with URL_FULL`() {
        fixture.scope.request = Request().also {
            it.url = "http://docs.sentry.io:3000/platform"
            it.queryString = "s=abc"
        }
        givenAttributes(
            mapOf(
                UrlAttributes.URL_FULL to "https://io.sentry:8081/path/to/123?q=123456&b=X"
            )
        )

        whenExtractingAttributes()

        thenRequestIsSet()
        thenUrlIsSetTo("http://docs.sentry.io:3000/platform")
        thenQueryIsSetTo("s=abc")
    }

    @Test
    fun `sets URL based on OTel attributes without port`() {
        givenAttributes(
            mapOf(
                HttpAttributes.HTTP_REQUEST_METHOD to "GET",
                UrlAttributes.URL_SCHEME to "https",
                UrlAttributes.URL_PATH to "/path/to/123",
                ServerAttributes.SERVER_ADDRESS to "io.sentry"
            )
        )

        whenExtractingAttributes()

        thenRequestIsSet()
        thenUrlIsSetTo("https://io.sentry/path/to/123")
    }

    @Test
    fun `sets URL based on OTel attributes without path`() {
        givenAttributes(
            mapOf(
                HttpAttributes.HTTP_REQUEST_METHOD to "GET",
                UrlAttributes.URL_SCHEME to "https",
                ServerAttributes.SERVER_ADDRESS to "io.sentry"
            )
        )

        whenExtractingAttributes()

        thenRequestIsSet()
        thenUrlIsSetTo("https://io.sentry")
    }

    @Test
    fun `does not set URL if server address is missing`() {
        givenAttributes(
            mapOf(
                HttpAttributes.HTTP_REQUEST_METHOD to "GET",
                UrlAttributes.URL_SCHEME to "https"
            )
        )

        whenExtractingAttributes()

        thenRequestIsSet()
        thenUrlIsNotSet()
    }

    @Test
    fun `does not set URL if scheme is missing`() {
        givenAttributes(
            mapOf(
                HttpAttributes.HTTP_REQUEST_METHOD to "GET",
                ServerAttributes.SERVER_ADDRESS to "io.sentry"
            )
        )

        whenExtractingAttributes()

        thenRequestIsSet()
        thenUrlIsNotSet()
    }

    @Test
    fun `returns null if no URL in attributes`() {
        givenAttributes(mapOf())

        val url = whenExtractingUrl()

        assertNull(url)
    }

    @Test
    fun `returns full URL if present`() {
        givenAttributes(
            mapOf(
                UrlAttributes.URL_FULL to "https://sentry.io/some/path"
            )
        )

        val url = whenExtractingUrl()

        assertEquals("https://sentry.io/some/path", url)
    }

    @Test
    fun `returns reconstructed URL if attributes present`() {
        givenAttributes(
            mapOf(
                UrlAttributes.URL_SCHEME to "https",
                ServerAttributes.SERVER_ADDRESS to "sentry.io",
                ServerAttributes.SERVER_PORT to 8082L,
                UrlAttributes.URL_PATH to "/some/path"
            )
        )

        val url = whenExtractingUrl()

        assertEquals("https://sentry.io:8082/some/path", url)
    }

    @Test
    fun `returns reconstructed URL if attributes present without port`() {
        givenAttributes(
            mapOf(
                UrlAttributes.URL_SCHEME to "https",
                ServerAttributes.SERVER_ADDRESS to "sentry.io",
                UrlAttributes.URL_PATH to "/some/path"
            )
        )

        val url = whenExtractingUrl()

        assertEquals("https://sentry.io/some/path", url)
    }

    @Test
    fun `returns null URL if scheme missing`() {
        givenAttributes(
            mapOf(
                ServerAttributes.SERVER_ADDRESS to "sentry.io",
                ServerAttributes.SERVER_PORT to 8082L,
                UrlAttributes.URL_PATH to "/some/path"
            )
        )

        val url = whenExtractingUrl()

        assertNull(url)
    }

    @Test
    fun `returns null URL if server address missing`() {
        givenAttributes(
            mapOf(
                UrlAttributes.URL_SCHEME to "https",
                ServerAttributes.SERVER_PORT to 8082L,
                UrlAttributes.URL_PATH to "/some/path"
            )
        )

        val url = whenExtractingUrl()

        assertNull(url)
    }

    @Test
    fun `returns reconstructed URL if attributes present without port and path`() {
        givenAttributes(
            mapOf(
                UrlAttributes.URL_SCHEME to "https",
                ServerAttributes.SERVER_ADDRESS to "sentry.io"
            )
        )

        val url = whenExtractingUrl()

        assertEquals("https://sentry.io", url)
    }

    @Test
    fun `returns reconstructed URL if attributes present without path`() {
        givenAttributes(
            mapOf(
                UrlAttributes.URL_SCHEME to "https",
                ServerAttributes.SERVER_ADDRESS to "sentry.io",
                ServerAttributes.SERVER_PORT to 8082L
            )
        )

        val url = whenExtractingUrl()

        assertEquals("https://sentry.io:8082", url)
    }

    @Test
    fun `sets server request headers based on OTel attributes and merges list of values`() {
        givenAttributes(
            mapOf(
                HttpAttributes.HTTP_REQUEST_METHOD to "GET",
                AttributeKey.stringArrayKey("http.request.header.baggage") to listOf("sentry-environment=production,sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rate=1,sentry-sampled=true,sentry-trace_id=df71f5972f754b4c85af13ff5c07017d", "another-baggage=abc,more=def"),
                AttributeKey.stringArrayKey("http.request.header.sentry-trace") to listOf("f9118105af4a2d42b4124532cd176588-4542d085bb0b4de5"),
                AttributeKey.stringArrayKey("http.response.header.some-header") to listOf("some-value")
            )
        )

        whenExtractingAttributes()

        thenRequestIsSet()
        thenHeaderIsPresentOnRequest("baggage", "sentry-environment=production,sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rate=1,sentry-sampled=true,sentry-trace_id=df71f5972f754b4c85af13ff5c07017d,another-baggage=abc,more=def")
        thenHeaderIsPresentOnRequest("sentry-trace", "f9118105af4a2d42b4124532cd176588-4542d085bb0b4de5")
        thenHeaderIsNotPresentOnRequest("some-header")
    }

    @Test
    fun `if there are no header attributes does not set headers on request`() {
        givenAttributes(mapOf(HttpAttributes.HTTP_REQUEST_METHOD to "GET"))

        whenExtractingAttributes()

        thenRequestIsSet()
        assertNull(fixture.scope.request!!.headers)
    }

    @Test
    fun `if there is no request method attribute does not set request on scope`() {
        givenAttributes(
            mapOf(
                UrlAttributes.URL_SCHEME to "https",
                ServerAttributes.SERVER_ADDRESS to "io.sentry"
            )
        )

        whenExtractingAttributes()

        thenRequestIsNotSet()
    }

    private fun givenAttributes(map: Map<AttributeKey<out Any>, Any>) {
        map.forEach { k, v ->
            fixture.attributes.put(k, v)
        }
    }

    private fun whenExtractingAttributes() {
        OpenTelemetryAttributesExtractor().extract(fixture.spanData, fixture.scope, fixture.options)
    }

    private fun whenExtractingUrl(): String? {
        return OpenTelemetryAttributesExtractor().extractUrl(fixture.attributes, fixture.options)
    }

    private fun thenRequestIsSet() {
        assertNotNull(fixture.scope.request)
    }

    private fun thenRequestIsNotSet() {
        assertNull(fixture.scope.request)
    }

    private fun thenUrlIsSetTo(expected: String) {
        assertEquals(expected, fixture.scope.request!!.url)
    }

    private fun thenUrlIsNotSet() {
        assertNull(fixture.scope.request!!.url)
    }

    private fun thenQueryIsSetTo(expected: String) {
        assertEquals(expected, fixture.scope.request!!.queryString)
    }

    private fun thenHeaderIsPresentOnRequest(headerName: String, expectedValue: String) {
        assertEquals(expectedValue, fixture.scope.request!!.headers!!.get(headerName))
    }

    private fun thenHeaderIsNotPresentOnRequest(headerName: String) {
        assertFalse(fixture.scope.request!!.headers!!.containsKey(headerName))
    }
}
