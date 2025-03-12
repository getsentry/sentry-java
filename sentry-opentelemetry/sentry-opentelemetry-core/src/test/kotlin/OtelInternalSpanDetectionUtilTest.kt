package io.sentry.opentelemetry

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.internal.AttributesMap
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.SemanticAttributes
import io.opentelemetry.semconv.ServerAttributes
import io.opentelemetry.semconv.UrlAttributes
import io.sentry.IScopes
import io.sentry.SentryOptions
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OtelInternalSpanDetectionUtilTest {

    private class Fixture {
        val scopes = mock<IScopes>()
        val attributes = AttributesMap.create(100, 100)
        val options = SentryOptions.empty()
        var spanKind: SpanKind = SpanKind.INTERNAL

        init {
            whenever(scopes.options).thenReturn(options)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `detects split url as internal (span kind client)`() {
        givenDsn("https://publicKey:secretKey@io.sentry:8081/path/id?sample.rate=0.1")
        givenSpanKind(SpanKind.CLIENT)
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

        thenRequestIsConsideredInternal()
    }

    @Test
    fun `detects full url as internal (span kind client)`() {
        givenDsn("https://publicKey:secretKey@io.sentry:8081/path/id?sample.rate=0.1")
        givenSpanKind(SpanKind.CLIENT)
        givenAttributes(
            mapOf(
                UrlAttributes.URL_FULL to "https://io.sentry:8081"
            )
        )

        thenRequestIsConsideredInternal()
    }

    @Test
    fun `detects deprecated url as internal (span kind client)`() {
        givenDsn("https://publicKey:secretKey@io.sentry:8081/path/id?sample.rate=0.1")
        givenSpanKind(SpanKind.CLIENT)
        givenAttributes(
            mapOf(
                SemanticAttributes.HTTP_URL to "https://io.sentry:8081"
            )
        )

        thenRequestIsConsideredInternal()
    }

    @Test
    fun `detects split url as internal (span kind internal)`() {
        givenDsn("https://publicKey:secretKey@io.sentry:8081/path/id?sample.rate=0.1")
        givenSpanKind(SpanKind.INTERNAL)
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

        thenRequestIsConsideredInternal()
    }

    @Test
    fun `detects full url as internal (span kind internal)`() {
        givenDsn("https://publicKey:secretKey@io.sentry:8081/path/id?sample.rate=0.1")
        givenSpanKind(SpanKind.INTERNAL)
        givenAttributes(
            mapOf(
                UrlAttributes.URL_FULL to "https://io.sentry:8081"
            )
        )

        thenRequestIsConsideredInternal()
    }

    @Test
    fun `detects deprecated url as internal (span kind internal)`() {
        givenDsn("https://publicKey:secretKey@io.sentry:8081/path/id?sample.rate=0.1")
        givenSpanKind(SpanKind.INTERNAL)
        givenAttributes(
            mapOf(
                SemanticAttributes.HTTP_URL to "https://io.sentry:8081"
            )
        )

        thenRequestIsConsideredInternal()
    }

    @Test
    fun `does not detect full url as internal (span kind server)`() {
        givenDsn("https://publicKey:secretKey@io.sentry:8081/path/id?sample.rate=0.1")
        givenSpanKind(SpanKind.SERVER)
        givenAttributes(
            mapOf(
                UrlAttributes.URL_FULL to "https://io.sentry:8081"
            )
        )

        thenRequestIsNotConsideredInternal()
    }

    @Test
    fun `does not detect full url as internal (span kind producer)`() {
        givenDsn("https://publicKey:secretKey@io.sentry:8081/path/id?sample.rate=0.1")
        givenSpanKind(SpanKind.PRODUCER)
        givenAttributes(
            mapOf(
                UrlAttributes.URL_FULL to "https://io.sentry:8081"
            )
        )

        thenRequestIsNotConsideredInternal()
    }

    @Test
    fun `does not detect full url as internal (span kind consumer)`() {
        givenDsn("https://publicKey:secretKey@io.sentry:8081/path/id?sample.rate=0.1")
        givenSpanKind(SpanKind.CONSUMER)
        givenAttributes(
            mapOf(
                UrlAttributes.URL_FULL to "https://io.sentry:8081"
            )
        )

        thenRequestIsNotConsideredInternal()
    }

    @Test
    fun `detects full spotlight url as internal`() {
        givenDsn("https://publicKey:secretKey@io.sentry:8081/path/id?sample.rate=0.1")
        givenSpotlightEnabled(true)
        givenSpanKind(SpanKind.CLIENT)
        givenAttributes(
            mapOf(
                UrlAttributes.URL_FULL to "http://localhost:8969/stream"
            )
        )

        thenRequestIsConsideredInternal()
    }

    @Test
    fun `detects full spotlight url as internal with custom spotlight url`() {
        givenDsn("https://publicKey:secretKey@io.sentry:8081/path/id?sample.rate=0.1")
        givenSpotlightEnabled(true)
        givenSpotlightUrl("http://localhost:8090/stream")
        givenSpanKind(SpanKind.CLIENT)
        givenAttributes(
            mapOf(
                UrlAttributes.URL_FULL to "http://localhost:8090/stream"
            )
        )

        thenRequestIsConsideredInternal()
    }

    @Test
    fun `does not detect mismatching full spotlight url as internal`() {
        givenDsn("https://publicKey:secretKey@io.sentry:8081/path/id?sample.rate=0.1")
        givenSpotlightEnabled(true)
        givenSpanKind(SpanKind.CLIENT)
        givenAttributes(
            mapOf(
                UrlAttributes.URL_FULL to "http://localhost:8080/stream"
            )
        )

        thenRequestIsNotConsideredInternal()
    }

    @Test
    fun `does not detect mismatching full customized spotlight url as internal`() {
        givenDsn("https://publicKey:secretKey@io.sentry:8081/path/id?sample.rate=0.1")
        givenSpotlightEnabled(true)
        givenSpotlightUrl("http://localhost:8090/stream")
        givenSpanKind(SpanKind.CLIENT)
        givenAttributes(
            mapOf(
                UrlAttributes.URL_FULL to "http://localhost:8091/stream"
            )
        )

        thenRequestIsNotConsideredInternal()
    }

    private fun givenAttributes(map: Map<AttributeKey<out Any>, Any>) {
        map.forEach { k, v ->
            fixture.attributes.put(k, v)
        }
    }

    private fun givenDsn(dsn: String) {
        fixture.options.dsn = dsn
    }

    private fun givenSpotlightEnabled(enabled: Boolean) {
        fixture.options.isEnableSpotlight = enabled
    }

    private fun givenSpotlightUrl(url: String) {
        fixture.options.spotlightConnectionUrl = url
    }

    private fun givenSpanKind(spanKind: SpanKind) {
        fixture.spanKind = spanKind
    }

    private fun thenRequestIsConsideredInternal() {
        assertTrue(checkIfInternal())
    }

    private fun thenRequestIsNotConsideredInternal() {
        assertFalse(checkIfInternal())
    }

    private fun checkIfInternal(): Boolean {
        return OtelInternalSpanDetectionUtil.isSentryRequest(fixture.scopes, fixture.spanKind, fixture.attributes)
    }
}
