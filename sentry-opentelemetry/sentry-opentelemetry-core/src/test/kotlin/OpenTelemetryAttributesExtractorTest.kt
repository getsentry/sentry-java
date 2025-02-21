package io.sentry.opentelemetry

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.internal.AttributesMap
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.ServerAttributes
import io.opentelemetry.semconv.UrlAttributes
import io.sentry.ISpan
import io.sentry.Scope
import io.sentry.SentryOptions
import io.sentry.protocol.Request
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OpenTelemetryAttributesExtractorTest {

    private class Fixture {
        val spanData = mock<SpanData>()
        val attributes = AttributesMap.create(100, 100)
        val sentrySpan = mock<ISpan>()
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

    private fun givenAttributes(map: Map<AttributeKey<out Any>, Any>) {
        map.forEach { k, v ->
            fixture.attributes.put(k, v)
        }
    }

    private fun whenExtractingAttributes() {
        OpenTelemetryAttributesExtractor().extract(fixture.spanData, fixture.sentrySpan, fixture.scope)
    }

    private fun whenExtractingUrl(): String? {
        return OpenTelemetryAttributesExtractor().extractUrl(fixture.attributes)
    }

    private fun thenRequestIsSet() {
        assertNotNull(fixture.scope.request)
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
}
