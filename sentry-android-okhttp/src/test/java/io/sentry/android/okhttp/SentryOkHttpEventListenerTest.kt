package io.sentry.android.okhttp

import io.sentry.BaggageHeader
import io.sentry.IHub
import io.sentry.SentryOptions
import io.sentry.SentryTraceHeader
import io.sentry.SentryTracer
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentryOkHttpEventListenerTest {

    class Fixture {
        val hub = mock<IHub>()
        val server = MockWebServer()
        lateinit var sentryTracer: SentryTracer
        lateinit var options: SentryOptions

        @SuppressWarnings("LongParameterList")
        fun getSut(
            isSpanActive: Boolean = true,
            useInterceptor: Boolean = false,
            httpStatusCode: Int = 201,
            responseBody: String = "success",
            socketPolicy: SocketPolicy = SocketPolicy.KEEP_OPEN,
            sendDefaultPii: Boolean = false
        ): OkHttpClient {
            options = SentryOptions().apply {
                dsn = "https://key@sentry.io/proj"
                isSendDefaultPii = sendDefaultPii
            }
            whenever(hub.options).thenReturn(options)

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

            val builder = OkHttpClient.Builder()
            if (useInterceptor) {
                builder.addInterceptor(SentryOkHttpInterceptor(hub))
            }
            return builder.eventListener(SentryOkHttpEventListener(hub)).build()
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

    private fun postRequest(url: String = "/hello", body: String): Request {
        return Request.Builder()
            .addHeader("myHeader", "myValue")
            .post(body.toRequestBody())
            .url(fixture.server.url(url))
            .build()
    }

    @Test
    fun `when there is an active span and the SentryOkHttpInterceptor, adds sentry trace headers to the request`() {
        val sut = fixture.getSut(useInterceptor = true)
        sut.newCall(getRequest()).execute()
        val recorderRequest = fixture.server.takeRequest()
        assertNotNull(recorderRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
        assertNotNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])
    }

    @Suppress("MaxLineLength")
    @Test
    fun `when there is an active span but no SentryOkHttpInterceptor, sentry trace headers are not added to the request`() {
        val sut = fixture.getSut()
        sut.newCall(getRequest()).execute()
        val recorderRequest = fixture.server.takeRequest()
        assertNull(recorderRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
        assertNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])
    }

    @Test
    fun `creates a span around the request`() {
        val sut = fixture.getSut()
        val request = getRequest()
        val call = sut.newCall(request)
        val response = call.execute()
        val okHttpEvent = SentryOkHttpEventListener.eventMap[call]
        val callSpan = okHttpEvent?.callRootSpan
        response.close()
        assertNotNull(callSpan)
        assertEquals(callSpan, fixture.sentryTracer.children.first())
        assertEquals("http.client", callSpan.operation)
        assertEquals("GET ${request.url}", callSpan.description)
        assertEquals(SpanStatus.OK, callSpan.status)
        assertTrue(callSpan.isFinished)
    }

    @Test
    fun `creates a span for each event`() {
        val sut = fixture.getSut()
        val request = getRequest()
        val call = sut.newCall(request)
        val response = call.execute()
        val okHttpEvent = SentryOkHttpEventListener.eventMap[call]
        val callSpan = okHttpEvent?.callRootSpan
        response.close()
        assertEquals(8, fixture.sentryTracer.children.size)
        fixture.sentryTracer.children.forEachIndexed { index, span ->
            assertTrue(span.isFinished)
            when (index) {
                0 -> {
                    assertEquals(callSpan, span)
                    assertEquals("GET ${request.url}", span.description)
                    assertNotNull(span.data["proxies"])
                    assertNotNull(span.data["domain_name"])
                    assertNotNull(span.data["dns_addresses"])
                    assertEquals(201, span.data["status_code"])
                }
                1 -> {
                    assertEquals("proxySelect", span.description)
                    assertNotNull(span.data["proxies"])
                }
                2 -> {
                    assertEquals("dns", span.description)
                    assertNotNull(span.data["domain_name"])
                    assertNotNull(span.data["dns_addresses"])
                }
                3 -> {
                    assertEquals("connect", span.description)
                }
                4 -> {
                    assertEquals("connection", span.description)
                }
                5 -> {
                    assertEquals("requestHeaders", span.description)
                }
                6 -> {
                    assertEquals("responseHeaders", span.description)
                    assertEquals(201, span.data["status_code"])
                }
                7 -> {
                    assertEquals("responseBody", span.description)
                }
            }
        }
    }

    @Test
    fun `has requestBody span for requests with body`() {
        val sut = fixture.getSut()
        val requestBody = "request body sent in the request"
        val request = postRequest(body = requestBody)
        val call = sut.newCall(request)
        val response = call.execute()
        val okHttpEvent = SentryOkHttpEventListener.eventMap[call]
        val callSpan = okHttpEvent?.callRootSpan
        response.close()
        assertEquals(9, fixture.sentryTracer.children.size)
        val requestBodySpan = fixture.sentryTracer.children.firstOrNull { it.description == "requestBody" }
        assertNotNull(requestBodySpan)
        assertEquals(requestBody.toByteArray().size.toLong(), requestBodySpan.data["request_body_size"])
        assertEquals(requestBody.toByteArray().size.toLong(), callSpan?.getData("request_body_size"))
    }

    @Test
    fun `has response_body_size data if body is consumed`() {
        val sut = fixture.getSut()
        val requestBody = "request body sent in the request"
        val request = postRequest(body = requestBody)
        val call = sut.newCall(request)
        val response = call.execute()
        val okHttpEvent = SentryOkHttpEventListener.eventMap[call]
        val callSpan = okHttpEvent?.callRootSpan

        // Consume the response
        val responseBytes = response.body?.byteStream()?.readBytes()
        assertNotNull(responseBytes)

        response.close()
        val requestBodySpan = fixture.sentryTracer.children.firstOrNull { it.description == "responseBody" }
        assertNotNull(requestBodySpan)
        assertEquals(responseBytes.size.toLong(), requestBodySpan.data["response_body_size"])
        assertEquals(responseBytes.size.toLong(), callSpan?.getData("response_body_size"))
    }

    @Test
    fun `root call span status depends on http status code`() {
        val sut = fixture.getSut(httpStatusCode = 404)
        val request = getRequest()
        val call = sut.newCall(request)
        val response = call.execute()
        val okHttpEvent = SentryOkHttpEventListener.eventMap[call]
        val callSpan = okHttpEvent?.callRootSpan
        response.close()
        assertNotNull(callSpan)
        assertEquals(SpanStatus.fromHttpStatusCode(404), callSpan.status)
    }
}
