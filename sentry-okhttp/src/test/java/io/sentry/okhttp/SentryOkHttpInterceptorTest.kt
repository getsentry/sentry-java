@file:Suppress("MaxLineLength")

package io.sentry.okhttp

import io.sentry.BaggageHeader
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.HttpStatusCodeRange
import io.sentry.IHub
import io.sentry.IScope
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryOptions
import io.sentry.SentryTraceHeader
import io.sentry.SentryTracer
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.TypeCheckHint
import io.sentry.exception.SentryHttpClientException
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class SentryOkHttpInterceptorTest {

    class Fixture {
        val hub = mock<IHub>()
        val server = MockWebServer()
        lateinit var sentryTracer: SentryTracer
        lateinit var options: SentryOptions
        lateinit var scope: IScope

        @SuppressWarnings("LongParameterList")
        fun getSut(
            isSpanActive: Boolean = true,
            httpStatusCode: Int = 201,
            responseBody: String = "success",
            socketPolicy: SocketPolicy = SocketPolicy.KEEP_OPEN,
            beforeSpan: SentryOkHttpInterceptor.BeforeSpanCallback? = null,
            includeMockServerInTracePropagationTargets: Boolean = true,
            keepDefaultTracePropagationTargets: Boolean = false,
            captureFailedRequests: Boolean? = false,
            failedRequestTargets: List<String> = listOf(".*"),
            failedRequestStatusCodes: List<HttpStatusCodeRange> = listOf(
                HttpStatusCodeRange(
                    HttpStatusCodeRange.DEFAULT_MIN,
                    HttpStatusCodeRange.DEFAULT_MAX
                )
            ),
            sendDefaultPii: Boolean = false
        ): OkHttpClient {
            options = SentryOptions().apply {
                dsn = "https://key@sentry.io/proj"
                if (includeMockServerInTracePropagationTargets) {
                    setTracePropagationTargets(listOf(server.hostName))
                } else if (!keepDefaultTracePropagationTargets) {
                    setTracePropagationTargets(listOf("other-api"))
                }
                isSendDefaultPii = sendDefaultPii
            }
            scope = Scope(options)
            whenever(hub.options).thenReturn(options)
            doAnswer { (it.arguments[0] as ScopeCallback).run(scope) }.whenever(hub).configureScope(any())

            sentryTracer = SentryTracer(TransactionContext("name", "op"), hub)

            if (isSpanActive) {
                whenever(hub.span).thenReturn(sentryTracer)
            }
            server.enqueue(
                MockResponse()
                    .setBody(responseBody)
                    .addHeader("myResponseHeader", "myValue")
                    .setSocketPolicy(socketPolicy)
                    .setResponseCode(httpStatusCode)
            )

            val interceptor = when (captureFailedRequests) {
                null -> SentryOkHttpInterceptor(
                    hub,
                    beforeSpan,
                    failedRequestTargets = failedRequestTargets,
                    failedRequestStatusCodes = failedRequestStatusCodes
                )

                else -> SentryOkHttpInterceptor(
                    hub,
                    beforeSpan,
                    captureFailedRequests = captureFailedRequests,
                    failedRequestTargets = failedRequestTargets,
                    failedRequestStatusCodes = failedRequestStatusCodes
                )
            }
            return OkHttpClient.Builder().addInterceptor(interceptor).build()
        }
    }

    private val fixture = Fixture()

    private fun getRequest(url: String = "/hello"): Request {
        return Request.Builder()
            .addHeader("myHeader", "myValue")
            .get()
            .url(fixture.server.url(url))
            .build()
    }

    private val getRequestWithBaggageHeader = {
        Request.Builder()
            .addHeader("baggage", "thirdPartyBaggage=someValue")
            .addHeader(
                "baggage",
                "secondThirdPartyBaggage=secondValue; " +
                    "property;propertyKey=propertyValue,anotherThirdPartyBaggage=anotherValue"
            )
            .get()
            .url(fixture.server.url("/hello"))
            .build()
    }

    private fun postRequest(
        body: RequestBody = "request-body"
            .toRequestBody(
                "text/plain"
                    .toMediaType()
            )
    ): Request {
        return Request.Builder().post(
            body
        ).url(fixture.server.url("/hello")).build()
    }

    @SuppressWarnings("MaxLineLength")
    @Test
    fun `when there is an active span and server is listed in tracing origins, adds sentry trace headers to the request`() {
        val sut = fixture.getSut()
        sut.newCall(getRequest()).execute()
        val recorderRequest = fixture.server.takeRequest()
        assertNotNull(recorderRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
        assertNotNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])
    }

    @SuppressWarnings("MaxLineLength")
    @Test
    fun `when there is an active span and tracing origins contains default regex, adds sentry trace headers to the request`() {
        val sut = fixture.getSut(keepDefaultTracePropagationTargets = true)

        sut.newCall(getRequest()).execute()
        val recorderRequest = fixture.server.takeRequest()
        assertNotNull(recorderRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
        assertNotNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])
    }

    @SuppressWarnings("MaxLineLength")
    @Test
    fun `when there is an active span and server is not listed in tracing origins, does not add sentry trace headers to the request`() {
        val sut = fixture.getSut(includeMockServerInTracePropagationTargets = false)
        sut.newCall(Request.Builder().get().url(fixture.server.url("/hello")).build()).execute()
        val recorderRequest = fixture.server.takeRequest()
        assertNull(recorderRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
        assertNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])
    }

    @SuppressWarnings("MaxLineLength")
    @Test
    fun `when there is an active span and server tracing origins is empty, does not add sentry trace headers to the request`() {
        val sut = fixture.getSut()
        fixture.options.setTracePropagationTargets(emptyList())
        sut.newCall(Request.Builder().get().url(fixture.server.url("/hello")).build()).execute()
        val recorderRequest = fixture.server.takeRequest()
        assertNull(recorderRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
        assertNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])
    }

    @Test
    fun `when there is no active span, adds sentry trace header to the request from scope`() {
        val sut = fixture.getSut(isSpanActive = false)
        sut.newCall(getRequest()).execute()
        val recorderRequest = fixture.server.takeRequest()
        assertNotNull(recorderRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
        assertNotNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])
    }

    @Test
    fun `when there is no active span and host if not allowed, does not add sentry trace header to the request`() {
        val sut = fixture.getSut(isSpanActive = false)
        fixture.options.setTracePropagationTargets(listOf("some-host-that-does-not-exist"))
        sut.newCall(getRequest()).execute()
        val recorderRequest = fixture.server.takeRequest()
        assertNull(recorderRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
        assertNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])
    }

    @Test
    fun `when there is an active span, existing baggage headers are merged with sentry baggage into single header`() {
        val sut = fixture.getSut()
        sut.newCall(getRequestWithBaggageHeader()).execute()
        val recorderRequest = fixture.server.takeRequest()
        assertNotNull(recorderRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
        assertNotNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])

        val baggageHeaderValues = recorderRequest.headers.values(BaggageHeader.BAGGAGE_HEADER)
        assertEquals(baggageHeaderValues.size, 1)
        assertTrue(
            baggageHeaderValues[0].startsWith(
                "thirdPartyBaggage=someValue," +
                    "secondThirdPartyBaggage=secondValue; " +
                    "property;propertyKey=propertyValue," +
                    "anotherThirdPartyBaggage=anotherValue"
            )
        )
        assertTrue(baggageHeaderValues[0].contains("sentry-public_key=key"))
        assertTrue(baggageHeaderValues[0].contains("sentry-transaction=name"))
        assertTrue(baggageHeaderValues[0].contains("sentry-trace_id"))
    }

    @Test
    fun `does not overwrite response body`() {
        val sut = fixture.getSut()
        val response = sut.newCall(getRequest()).execute()
        assertEquals("success", response.body?.string())
    }

    @Test
    fun `creates a span around the request`() {
        val sut = fixture.getSut()
        val request = getRequest()
        sut.newCall(request).execute()
        assertEquals(1, fixture.sentryTracer.children.size)
        val httpClientSpan = fixture.sentryTracer.children.first()
        assertEquals("http.client", httpClientSpan.operation)
        assertEquals("GET ${request.url}", httpClientSpan.description)
        assertEquals(201, httpClientSpan.data[SpanDataConvention.HTTP_STATUS_CODE_KEY])
        assertEquals("auto.http.okhttp", httpClientSpan.spanContext.origin)
        assertEquals(SpanStatus.OK, httpClientSpan.status)
        assertTrue(httpClientSpan.isFinished)
    }

    @Test
    fun `maps http status code to SpanStatus`() {
        val sut = fixture.getSut(httpStatusCode = 400)
        sut.newCall(getRequest()).execute()
        val httpClientSpan = fixture.sentryTracer.children.first()
        assertEquals(400, httpClientSpan.data[SpanDataConvention.HTTP_STATUS_CODE_KEY])
        assertEquals(SpanStatus.INVALID_ARGUMENT, httpClientSpan.status)
    }

    @Test
    fun `does not map unmapped http status code to SpanStatus`() {
        val sut = fixture.getSut(httpStatusCode = 502)
        sut.newCall(getRequest()).execute()
        val httpClientSpan = fixture.sentryTracer.children.first()
        assertEquals(502, httpClientSpan.data[SpanDataConvention.HTTP_STATUS_CODE_KEY])
        assertNull(httpClientSpan.status)
    }

    @Test
    fun `adds breadcrumb when http calls succeeds`() {
        val sut = fixture.getSut(responseBody = "response body")
        sut.newCall(postRequest()).execute()
        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("http", it.type)
                assertEquals(13L, it.data[SpanDataConvention.HTTP_RESPONSE_CONTENT_LENGTH_KEY])
                assertEquals(12L, it.data["http.request_content_length"])
            },
            anyOrNull()
        )
    }

    @SuppressWarnings("SwallowedException")
    @Test
    fun `adds breadcrumb when http calls results in exception`() {
        // to setup mocks
        fixture.getSut()
        val interceptor = SentryOkHttpInterceptor(fixture.hub)
        val chain = mock<Interceptor.Chain>()
        whenever(chain.call()).thenReturn(mock())
        whenever(chain.proceed(any())).thenThrow(IOException())
        whenever(chain.request()).thenReturn(getRequest())

        try {
            interceptor.intercept(chain)
            fail()
        } catch (e: IOException) {
            // ignore me
        }
        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("http", it.type)
            },
            anyOrNull()
        )
    }

    @SuppressWarnings("SwallowedException")
    @Test
    fun `sets status and throwable when call results in IOException`() {
        val sut = fixture.getSut(socketPolicy = SocketPolicy.DISCONNECT_AT_START)
        try {
            sut.newCall(getRequest()).execute()
            fail()
        } catch (e: IOException) {
            // ignore
        }
        val httpClientSpan = fixture.sentryTracer.children.first()
        assertNull(httpClientSpan.data[SpanDataConvention.HTTP_STATUS_CODE_KEY])
        assertEquals(SpanStatus.INTERNAL_ERROR, httpClientSpan.status)
        assertTrue(httpClientSpan.throwable is IOException)
    }

    @Test
    fun `customizer modifies span`() {
        val sut = fixture.getSut(
            beforeSpan = { span, _, _ ->
                span.description = "overwritten description"
                span
            }
        )
        val request = getRequest()
        sut.newCall(request).execute()
        assertEquals(1, fixture.sentryTracer.children.size)
        val httpClientSpan = fixture.sentryTracer.children.first()
        assertEquals("overwritten description", httpClientSpan.description)
        assertTrue(httpClientSpan.isFinished)
    }

    @Test
    fun `customizer receives request and response`() {
        val sut = fixture.getSut(
            beforeSpan = { span, request, response ->
                assertEquals(request.url, request.url)
                assertEquals(request.method, request.method)
                assertNotNull(response) {
                    assertEquals(201, it.code)
                }
                span
            }
        )
        val request = getRequest()
        sut.newCall(request).execute()
    }

    @Test
    fun `customizer can drop the span`() {
        val sut = fixture.getSut(
            beforeSpan = { _, _, _ -> null }
        )
        sut.newCall(getRequest()).execute()
        val httpClientSpan = fixture.sentryTracer.children.first()
        assertTrue(httpClientSpan.isFinished)
        assertNotNull(httpClientSpan.spanContext.sampled) {
            assertFalse(it)
        }
    }

    @Test
    fun `captures failed requests by default`() {
        val sut = fixture.getSut(
            httpStatusCode = 500,
            captureFailedRequests = null
        )
        sut.newCall(getRequest()).execute()

        verify(fixture.hub).captureEvent(any(), any<Hint>())
    }

    @Test
    fun `captures an event if captureFailedRequests is enabled and within the range`() {
        val sut = fixture.getSut(
            captureFailedRequests = true,
            httpStatusCode = 500
        )
        sut.newCall(getRequest()).execute()

        verify(fixture.hub).captureEvent(any(), any<Hint>())
    }

    @Test
    fun `does not capture an event if captureFailedRequests is disabled`() {
        val sut = fixture.getSut(
            httpStatusCode = 500
        )
        sut.newCall(getRequest()).execute()

        verify(fixture.hub, never()).captureEvent(any(), any<Hint>())
    }

    @Test
    fun `does not capture an event if captureFailedRequests is enabled and not within the range`() {
        // default status code 201
        val sut = fixture.getSut(
            captureFailedRequests = true
        )
        sut.newCall(getRequest()).execute()

        verify(fixture.hub, never()).captureEvent(any(), any<Hint>())
    }

    @Test
    fun `does not capture an event if captureFailedRequests is enabled and not within the targets`() {
        val sut = fixture.getSut(
            captureFailedRequests = true,
            httpStatusCode = 500,
            failedRequestTargets = listOf("myapi.com")
        )
        sut.newCall(getRequest()).execute()

        verify(fixture.hub, never()).captureEvent(any(), any<Hint>())
    }

    @Test
    fun `captures an error event with hints`() {
        val sut = fixture.getSut(
            captureFailedRequests = true,
            httpStatusCode = 500
        )
        sut.newCall(getRequest()).execute()

        verify(fixture.hub).captureEvent(
            any(),
            check<Hint> {
                assertNotNull(it.get(TypeCheckHint.OKHTTP_REQUEST))
                assertNotNull(it.get(TypeCheckHint.OKHTTP_RESPONSE))
            }
        )
    }

    @Test
    fun `captures an error event with request and response fields set`() {
        val statusCode = 500
        val sut = fixture.getSut(
            captureFailedRequests = true,
            httpStatusCode = statusCode,
            responseBody = "fail",
            sendDefaultPii = true
        )

        val request = getRequest(url = "/hello?myQuery=myValue#myFragment")
        val response = sut.newCall(request).execute()

        verify(fixture.hub).captureEvent(
            check {
                val sentryRequest = it.request!!
                assertEquals("http://localhost:${fixture.server.port}/hello", sentryRequest.url)
                assertEquals("myQuery=myValue", sentryRequest.queryString)
                assertEquals("myFragment", sentryRequest.fragment)
                assertEquals("GET", sentryRequest.method)

                // because of isSendDefaultPii
                assertNotNull(sentryRequest.headers)
                assertNull(sentryRequest.cookies)

                val sentryResponse = it.contexts.response!!
                assertEquals(statusCode, sentryResponse.statusCode)
                assertEquals(response.body!!.contentLength(), sentryResponse.bodySize)

                // because of isSendDefaultPii
                assertNotNull(sentryResponse.headers)
                assertNull(sentryResponse.cookies)

                assertTrue(it.throwable is SentryHttpClientException)
            },
            any<Hint>()
        )
    }

    @Test
    fun `captures an error event with request body size`() {
        val sut = fixture.getSut(
            captureFailedRequests = true,
            httpStatusCode = 500
        )

        val body = "fail"
            .toRequestBody(
                "text/plain"
                    .toMediaType()
            )

        sut.newCall(postRequest(body = body)).execute()

        verify(fixture.hub).captureEvent(
            check {
                val sentryRequest = it.request!!
                assertEquals(body.contentLength(), sentryRequest.bodySize)
            },
            any<Hint>()
        )
    }

    @Test
    fun `captures an error event with headers`() {
        val sut = fixture.getSut(
            captureFailedRequests = true,
            httpStatusCode = 500,
            sendDefaultPii = true
        )

        sut.newCall(getRequest()).execute()

        verify(fixture.hub).captureEvent(
            check {
                val sentryRequest = it.request!!
                assertEquals("myValue", sentryRequest.headers!!["myHeader"])

                val sentryResponse = it.contexts.response!!
                assertEquals("myValue", sentryResponse.headers!!["myResponseHeader"])
            },
            any<Hint>()
        )
    }

    @SuppressWarnings("SwallowedException")
    @Test
    fun `does not capture an error even if it throws`() {
        // to setup mocks
        fixture.getSut()
        val interceptor = SentryOkHttpInterceptor(
            fixture.hub,
            captureFailedRequests = true
        )
        val chain = mock<Interceptor.Chain>()
        whenever(chain.call()).thenReturn(mock())
        whenever(chain.proceed(any())).thenThrow(IOException())
        whenever(chain.request()).thenReturn(getRequest())

        try {
            interceptor.intercept(chain)
            fail()
        } catch (e: IOException) {
            // ignore me
        }
        verify(fixture.hub, never()).captureEvent(any(), any<Hint>())
    }

    @Test
    fun `when a call is captured by SentryOkHttpEventListener no span nor breadcrumb is created`() {
        val sut = fixture.getSut(responseBody = "response body")
        val call = sut.newCall(postRequest())
        SentryOkHttpEventListener.eventMap[call] = mock()
        call.execute()
        val httpClientSpan = fixture.sentryTracer.children.firstOrNull()
        assertNull(httpClientSpan)
        verify(fixture.hub, never()).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
    }

    @Test
    fun `when a call is not captured by SentryOkHttpEventListener, client error is reported`() {
        val sut = fixture.getSut(captureFailedRequests = true, httpStatusCode = 500)
        val call = sut.newCall(getRequest())
        call.execute()
        verify(fixture.hub).captureEvent(any(), any<Hint>())
    }

    @Test
    fun `when a call is captured by SentryOkHttpEventListener no client error is reported`() {
        val sut = fixture.getSut(captureFailedRequests = true, httpStatusCode = 500)
        val call = sut.newCall(getRequest())
        SentryOkHttpEventListener.eventMap[call] = mock()
        call.execute()
        verify(fixture.hub, never()).captureEvent(any(), any<Hint>())
    }
}
