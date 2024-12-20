package io.sentry.openfeign

import feign.Client
import feign.Feign
import feign.FeignException
import feign.HeaderMap
import feign.RequestLine
import io.sentry.BaggageHeader
import io.sentry.Breadcrumb
import io.sentry.IScopes
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryOptions
import io.sentry.SentryTraceHeader
import io.sentry.SentryTracer
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.mockServerRequestTimeoutMillis
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class SentryFeignClientTest {

    class Fixture {
        val scopes = mock<IScopes>()
        val server = MockWebServer()
        val sentryTracer: SentryTracer
        val sentryOptions = SentryOptions().apply {
            dsn = "http://key@localhost/proj"
        }
        val scope = Scope(sentryOptions)

        init {
            whenever(scopes.options).thenReturn(sentryOptions)
            doAnswer { (it.arguments[0] as ScopeCallback).run(scope) }.whenever(scopes).configureScope(any())
            sentryTracer = SentryTracer(TransactionContext("name", "op"), scopes)
        }

        fun getSut(
            isSpanActive: Boolean = true,
            httpStatusCode: Int = 201,
            responseBody: String = "success",
            networkError: Boolean = false,
            beforeSpan: SentryFeignClient.BeforeSpanCallback? = null
        ): MockApi {
            if (isSpanActive) {
                whenever(scopes.span).thenReturn(sentryTracer)
            }
            server.enqueue(
                MockResponse()
                    .setBody(responseBody)
                    .setResponseCode(httpStatusCode)
            )
            server.start()

            return if (!networkError) {
                Feign.builder()
                    .addCapability(SentryCapability(scopes, beforeSpan))
            } else {
                val mockClient = mock<Client>()
                whenever(mockClient.execute(any(), any())).thenThrow(RuntimeException::class.java)
                Feign.builder()
                    .client(SentryFeignClient(mockClient, scopes, beforeSpan))
            }.target(MockApi::class.java, server.url("/").toUrl().toString())
        }
    }

    private lateinit var fixture: Fixture

    @BeforeTest
    fun setup() {
        fixture = Fixture()
    }

    @Test
    fun `when there is an active span, adds sentry trace header to the request`() {
        fixture.sentryOptions.isTraceSampling = true
        fixture.sentryOptions.dsn = "https://key@sentry.io/proj"
        val sut = fixture.getSut()
        sut.getOk()
        val recorderRequest = fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
        assertNotNull(recorderRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
        assertNotNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])
    }

    @Test
    fun `when there is an active span, existing baggage headers are merged with sentry baggage into single header`() {
        fixture.sentryOptions.isTraceSampling = true
        fixture.sentryOptions.dsn = "https://key@sentry.io/proj"
        val sut = fixture.getSut()

        sut.getOkWithBaggageHeader(mapOf("baggage" to listOf("thirdPartyBaggage=someValue", "secondThirdPartyBaggage=secondValue; property;propertyKey=propertyValue,anotherThirdPartyBaggage=anotherValue")))

        val recorderRequest = fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
        assertNotNull(recorderRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
        assertNotNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])

        val baggageHeaderValues = recorderRequest.headers.values(BaggageHeader.BAGGAGE_HEADER)
        assertEquals(baggageHeaderValues.size, 1)
        assertTrue(baggageHeaderValues[0].startsWith("thirdPartyBaggage=someValue,secondThirdPartyBaggage=secondValue; property;propertyKey=propertyValue,anotherThirdPartyBaggage=anotherValue"))
        assertTrue(baggageHeaderValues[0].contains("sentry-public_key=key"))
        assertTrue(baggageHeaderValues[0].contains("sentry-transaction=name"))
        assertTrue(baggageHeaderValues[0].contains("sentry-trace_id"))
    }

    @Test
    fun `when there is no active span, adds sentry trace header to the request from scope`() {
        fixture.sentryOptions.dsn = "https://key@sentry.io/proj"
        val sut = fixture.getSut(isSpanActive = false)
        sut.getOk()
        val recorderRequest = fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
        assertNotNull(recorderRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
        assertNotNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])
    }

    @Test
    fun `does not add sentry trace header when span origin is ignored`() {
        fixture.sentryOptions.dsn = "https://key@sentry.io/proj"
        fixture.sentryOptions.setIgnoredSpanOrigins(listOf("auto.http.openfeign"))
        val sut = fixture.getSut(isSpanActive = false)
        sut.getOk()
        val recorderRequest = fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
        assertNull(recorderRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
        assertNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])
    }

    @Test
    fun `when there is no active span, does not add sentry trace header to the request if host is disallowed`() {
        fixture.sentryOptions.setTracePropagationTargets(listOf("some-host-that-does-not-exist"))
        fixture.sentryOptions.dsn = "https://key@sentry.io/proj"
        val sut = fixture.getSut(isSpanActive = false)
        sut.getOk()
        val recorderRequest = fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
        assertNull(recorderRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
        assertNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])
    }

    @Test
    fun `when request url not in tracing origins, does not add sentry trace header to the request`() {
        fixture.sentryOptions.setTracePropagationTargets(listOf("http://some-other-url.sentry.io"))
        fixture.sentryOptions.isTraceSampling = true
        fixture.sentryOptions.dsn = "https://key@sentry.io/proj"
        val sut = fixture.getSut()
        sut.getOk()
        val recorderRequest = fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
        assertNull(recorderRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
        assertNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])
    }

    @Test
    fun `does not overwrite response body`() {
        val sut = fixture.getSut()
        val response = sut.getOk()
        assertEquals("success", response)
    }

    @Test
    fun `creates a span around the request`() {
        val sut = fixture.getSut()
        sut.getOk()
        assertEquals(1, fixture.sentryTracer.children.size)
        val httpClientSpan = fixture.sentryTracer.children.first()
        assertEquals("http.client", httpClientSpan.operation)
        assertEquals("GET ${fixture.server.url("/status/200")}", httpClientSpan.description)
        assertEquals(201, httpClientSpan.data[SpanDataConvention.HTTP_STATUS_CODE_KEY])
        assertEquals("GET", httpClientSpan.data[SpanDataConvention.HTTP_METHOD_KEY])
        assertEquals(SpanStatus.OK, httpClientSpan.status)
        assertEquals("auto.http.openfeign", httpClientSpan.spanContext.origin)
        assertTrue(httpClientSpan.isFinished)
    }

    @Test
    fun `maps http status code to SpanStatus`() {
        val sut = fixture.getSut(httpStatusCode = 400)
        try {
            sut.getOk()
        } catch (e: FeignException) {
            val httpClientSpan = fixture.sentryTracer.children.first()
            assertEquals(400, httpClientSpan.data[SpanDataConvention.HTTP_STATUS_CODE_KEY])
            assertEquals(SpanStatus.INVALID_ARGUMENT, httpClientSpan.status)
        }
    }

    @Test
    fun `does not map unmapped http status code to SpanStatus`() {
        val sut = fixture.getSut(httpStatusCode = 502)
        try {
            sut.getOk()
        } catch (e: FeignException) {
            val httpClientSpan = fixture.sentryTracer.children.first()
            assertEquals(502, httpClientSpan.data[SpanDataConvention.HTTP_STATUS_CODE_KEY])
            assertNull(httpClientSpan.status)
        }
    }

    @Test
    fun `adds breadcrumb when http calls succeeds`() {
        val sut = fixture.getSut(responseBody = "response body")
        sut.postWithBody("request-body")
        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("http", it.type)
                assertEquals(13, it.data["response_body_size"])
                assertEquals(12, it.data["request_body_size"])
            },
            anyOrNull()
        )
    }

    @Test
    fun `adds breadcrumb when http calls succeeds even though response body is null`() {
        val sut = fixture.getSut(responseBody = "")
        sut.postWithBody("request-body")
        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("http", it.type)
                assertEquals(0, it.data["response_body_size"])
                assertEquals(12, it.data["request_body_size"])
            },
            anyOrNull()
        )
    }

    @SuppressWarnings("SwallowedException")
    @Test
    fun `adds breadcrumb when http calls results in exception`() {
        val sut = fixture.getSut(networkError = true)

        try {
            sut.getOk()
            fail()
        } catch (e: Exception) {
            // ignore me
        }
        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("http", it.type)
            },
            anyOrNull()
        )
    }

    @SuppressWarnings("SwallowedException")
    @Test
    fun `sets status and throwable when call results in in exception`() {
        val sut = fixture.getSut(networkError = true)
        try {
            sut.getOk()
            fail()
        } catch (e: Exception) {
            // ignore
        }
        val httpClientSpan = fixture.sentryTracer.children.first()
        assertNull(httpClientSpan.data[SpanDataConvention.HTTP_STATUS_CODE_KEY])
        assertEquals(SpanStatus.INTERNAL_ERROR, httpClientSpan.status)
        assertTrue(httpClientSpan.throwable is Exception)
    }

    @Test
    fun `customizer modifies span`() {
        val sut = fixture.getSut { span, _, _ ->
            span.description = "overwritten description"
            span
        }
        sut.getOk()
        assertEquals(1, fixture.sentryTracer.children.size)
        val httpClientSpan = fixture.sentryTracer.children.first()
        assertEquals("overwritten description", httpClientSpan.description)
    }

    @Test
    fun `customizer receives request and response`() {
        val sut = fixture.getSut { span, request, response ->
            assertEquals(request.url(), request.url())
            assertEquals(request.httpMethod().name, request.httpMethod().name)
            assertNotNull(response) {
                assertEquals(201, it.status())
            }
            span
        }
        sut.getOk()
    }

    @Test
    fun `customizer can drop the span`() {
        val sut = fixture.getSut { _, _, _ -> null }
        sut.getOk()
        val httpClientSpan = fixture.sentryTracer.children.first()
        assertNotNull(httpClientSpan.spanContext.sampled) {
            assertFalse(it)
        }
    }

    interface MockApi {
        @RequestLine("GET /status/200")
        fun getOk(): String

        @RequestLine("GET /status/200")
        fun getOkWithBaggageHeader(@HeaderMap headers: Map<String, Any>): String

        @RequestLine("POST /post-with-body")
        fun postWithBody(body: String)
    }
}
