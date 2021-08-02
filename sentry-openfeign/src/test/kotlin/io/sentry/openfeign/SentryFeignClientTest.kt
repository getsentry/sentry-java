package io.sentry.openfeign

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import feign.Client
import feign.Feign
import feign.FeignException
import feign.RequestLine
import io.sentry.Breadcrumb
import io.sentry.IHub
import io.sentry.SentryOptions
import io.sentry.SentryTraceHeader
import io.sentry.SentryTracer
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class SentryFeignClientTest {

    class Fixture {
        val hub = mock<IHub>()
        val server = MockWebServer()
        val sentryTracer = SentryTracer(TransactionContext("name", "op"), hub)

        init {
            whenever(hub.options).thenReturn(SentryOptions())
        }

        fun getSut(
            isSpanActive: Boolean = true,
            httpStatusCode: Int = 201,
            responseBody: String = "success",
            networkError: Boolean = false,
            beforeSpan: SentryFeignClient.BeforeSpanCallback? = null
        ): MockApi {
            if (isSpanActive) {
                whenever(hub.span).thenReturn(sentryTracer)
            }
            server.enqueue(MockResponse()
                .setBody(responseBody)
                .setResponseCode(httpStatusCode))
            server.start()

            return if (!networkError) {
                Feign.builder()
                    .addCapability(SentryCapability(hub, beforeSpan))
            } else {
                val mockClient = mock<Client>()
                whenever(mockClient.execute(any(), any())).thenThrow(RuntimeException::class.java)
                Feign.builder()
                    .client(SentryFeignClient(mockClient, hub, beforeSpan))
            }.target(MockApi::class.java, server.url("/").toUrl().toString())
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when there is an active span, adds sentry trace header to the request`() {
        val sut = fixture.getSut()
        sut.getOk()
        val recorderRequest = fixture.server.takeRequest()
        assertNotNull(recorderRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
    }

    @Test
    fun `when there is no active span, does not add sentry trace header to the request`() {
        val sut = fixture.getSut(isSpanActive = false)
        sut.getOk()
        val recorderRequest = fixture.server.takeRequest()
        assertNull(recorderRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
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
        assertEquals(SpanStatus.OK, httpClientSpan.status)
        assertTrue(httpClientSpan.isFinished)
    }

    @Test
    fun `maps http status code to SpanStatus`() {
        val sut = fixture.getSut(httpStatusCode = 400)
        try {
            sut.getOk()
        } catch (e: FeignException) {
            val httpClientSpan = fixture.sentryTracer.children.first()
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
            assertNull(httpClientSpan.status)
        }
    }

    @Test
    fun `adds breadcrumb when http calls succeeds`() {
        val sut = fixture.getSut(responseBody = "response body")
        sut.postWithBody("request-body")
        verify(fixture.hub).addBreadcrumb(check<Breadcrumb> {
            assertEquals("http", it.type)
            assertEquals(13, it.data["response_body_size"])
            assertEquals(12, it.data["request_body_size"])
        })
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
        verify(fixture.hub).addBreadcrumb(check<Breadcrumb> {
            assertEquals("http", it.type)
        })
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
        assertFalse(httpClientSpan.isFinished)
    }

    interface MockApi {
        @RequestLine("GET /status/200")
        fun getOk(): String

        @RequestLine("POST /post-with-body")
        fun postWithBody(body: String)
    }
}
