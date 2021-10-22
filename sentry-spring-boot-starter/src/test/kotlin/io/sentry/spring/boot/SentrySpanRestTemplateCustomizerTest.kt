package io.sentry.spring.boot

import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.Breadcrumb
import io.sentry.IHub
import io.sentry.SentryOptions
import io.sentry.SentryTraceHeader
import io.sentry.SentryTracer
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestTemplate
import kotlin.test.Test
import kotlin.test.assertEquals

class SentrySpanRestTemplateCustomizerTest {
    class Fixture {
        val sentryOptions = SentryOptions()
        val hub = mock<IHub>()
        val restTemplate = RestTemplateBuilder().build()
        var mockServer = MockWebServer()
        val transaction = SentryTracer(TransactionContext("aTransaction", "op", true), hub)
        internal val customizer = SentrySpanRestTemplateCustomizer(hub)
        val url = mockServer.url("/test/123").toString()

        init {
            whenever(hub.options).thenReturn(sentryOptions)
        }

        fun getSut(isTransactionActive: Boolean, status: HttpStatus = HttpStatus.OK, socketPolicy: SocketPolicy = SocketPolicy.KEEP_OPEN, includeMockServerInTracingOrigins: Boolean = true): RestTemplate {
            customizer.customize(restTemplate)

            if (includeMockServerInTracingOrigins) {
                sentryOptions.tracingOrigins.add(mockServer.hostName)
            } else {
                sentryOptions.tracingOrigins.add("other-api")
            }

            mockServer.enqueue(
                MockResponse()
                    .setBody("OK")
                    .setSocketPolicy(socketPolicy)
                    .setResponseCode(status.value())
            )

            if (isTransactionActive) {
                whenever(hub.span).thenReturn(transaction)
            }

            return restTemplate
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when transaction is active, creates span around RestTemplate HTTP call`() {
        val result = fixture.getSut(isTransactionActive = true).getForObject(fixture.url, String::class.java)

        assertThat(result).isEqualTo("OK")
        assertThat(fixture.transaction.spans).hasSize(1)
        val span = fixture.transaction.spans.first()
        assertThat(span.operation).isEqualTo("http.client")
        assertThat(span.description).isEqualTo("GET ${fixture.url}")
        assertThat(span.status).isEqualTo(SpanStatus.OK)

        val recordedRequest = fixture.mockServer.takeRequest()
        assertThat(recordedRequest.headers["sentry-trace"]!!).startsWith(fixture.transaction.spanContext.traceId.toString())
            .endsWith("-1")
            .doesNotContain(fixture.transaction.spanContext.spanId.toString())
    }

    @Test
    fun `when transaction is active and server is not listed in tracing origins, does not add sentry trace header to the request`() {
        fixture.getSut(isTransactionActive = true, includeMockServerInTracingOrigins = false)
            .getForObject(fixture.url, String::class.java)
        val recordedRequest = fixture.mockServer.takeRequest()
        assertThat(recordedRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER]).isNull()
    }

    @Test
    fun `when transaction is active and server is listed in tracing origins, adds sentry trace header to the request`() {
        fixture.getSut(isTransactionActive = true, includeMockServerInTracingOrigins = true)
            .getForObject(fixture.url, String::class.java)
        val recordedRequest = fixture.mockServer.takeRequest()
        assertThat(recordedRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER]).isNotNull()
    }

    @Test
    fun `when transaction is active and response code is not 2xx, creates span with error status around RestTemplate HTTP call`() {
        try {
            fixture.getSut(isTransactionActive = true, status = HttpStatus.INTERNAL_SERVER_ERROR).getForObject(fixture.url, String::class.java)
        } catch (e: Throwable) {
        }
        assertThat(fixture.transaction.spans).hasSize(1)
        val span = fixture.transaction.spans.first()
        assertThat(span.operation).isEqualTo("http.client")
        assertThat(span.description).isEqualTo("GET ${fixture.url}")
        assertThat(span.status).isEqualTo(SpanStatus.INTERNAL_ERROR)
    }

    @Test
    fun `when transaction is active and throws IO exception, creates span with error status around RestTemplate HTTP call`() {
        try {
            fixture.getSut(isTransactionActive = true, socketPolicy = SocketPolicy.DISCONNECT_AT_START).getForObject(fixture.url, String::class.java)
        } catch (e: Throwable) {
        }
        assertThat(fixture.transaction.spans).hasSize(1)
        val span = fixture.transaction.spans.first()
        assertThat(span.operation).isEqualTo("http.client")
        assertThat(span.description).isEqualTo("GET ${fixture.url}")
        assertThat(span.status).isEqualTo(SpanStatus.INTERNAL_ERROR)
    }

    @Test
    fun `when transaction is not active, does not create span around RestTemplate HTTP call`() {
        val result = fixture.getSut(isTransactionActive = false).getForObject(fixture.url, String::class.java)

        assertThat(result).isEqualTo("OK")
        assertThat(fixture.transaction.spans).isEmpty()
    }

    @Test
    fun `avoids duplicate registration`() {
        val restTemplate = fixture.getSut(isTransactionActive = true)

        fixture.customizer.customize(restTemplate)
        assertThat(restTemplate.interceptors).hasSize(1)
        fixture.customizer.customize(restTemplate)
        assertThat(restTemplate.interceptors).hasSize(1)
    }

    @Test
    fun `when transaction is active adds breadcrumb when http calls succeeds`() {
        fixture.getSut(isTransactionActive = true).postForObject(fixture.url, "content", String::class.java)
        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("http", it.type)
                assertEquals(fixture.url, it.data["url"])
                assertEquals("POST", it.data["method"])
                assertEquals(7, it.data["request_body_size"])
            }
        )
    }

    @SuppressWarnings("SwallowedException")
    @Test
    fun `when transaction is active adds breadcrumb when http calls results in exception`() {
        try {
            fixture.getSut(isTransactionActive = true, status = HttpStatus.INTERNAL_SERVER_ERROR).getForObject(fixture.url, String::class.java)
        } catch (e: Throwable) {
        }
        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("http", it.type)
                assertEquals(fixture.url, it.data["url"])
                assertEquals("GET", it.data["method"])
            }
        )
    }

    @Test
    fun `when transaction is not active adds breadcrumb when http calls succeeds`() {
        fixture.getSut(isTransactionActive = false).postForObject(fixture.url, "content", String::class.java)
        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("http", it.type)
                assertEquals(fixture.url, it.data["url"])
                assertEquals("POST", it.data["method"])
                assertEquals(7, it.data["request_body_size"])
            }
        )
    }

    @SuppressWarnings("SwallowedException")
    @Test
    fun `when transaction is not active adds breadcrumb when http calls results in exception`() {
        try {
            fixture.getSut(isTransactionActive = false, status = HttpStatus.INTERNAL_SERVER_ERROR).getForObject(fixture.url, String::class.java)
        } catch (e: Throwable) {
        }
        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("http", it.type)
                assertEquals(fixture.url, it.data["url"])
                assertEquals("GET", it.data["method"])
            }
        )
    }
}
