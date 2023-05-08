package io.sentry.android.okhttp

import io.sentry.BaggageHeader
import io.sentry.IHub
import io.sentry.SentryOptions
import io.sentry.SentryTraceHeader
import io.sentry.SentryTracer
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
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
        val mockEventListener = mock<EventListener>()
        val mockEventListenerFactory = mock<EventListener.Factory>()
        lateinit var sentryTracer: SentryTracer
        lateinit var options: SentryOptions
        lateinit var sentryOkHttpEventListener: SentryOkHttpEventListener

        init {
            whenever(mockEventListenerFactory.create(any())).thenReturn(mockEventListener)
        }

        @SuppressWarnings("LongParameterList")
        fun getSut(
            isSpanActive: Boolean = true,
            useInterceptor: Boolean = false,
            httpStatusCode: Int = 201,
            sendDefaultPii: Boolean = false,
            eventListener: EventListener? = null,
            eventListenerFactory: EventListener.Factory? = null
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
                    .setBody("responseBody")
                    .addHeader("myResponseHeader", "myValue")
                    .setSocketPolicy(SocketPolicy.KEEP_OPEN)
                    .setResponseCode(httpStatusCode)
            )

            val builder = OkHttpClient.Builder()
            if (useInterceptor) {
                builder.addInterceptor(SentryOkHttpInterceptor(hub))
            }
            sentryOkHttpEventListener = when {
                eventListenerFactory != null -> SentryOkHttpEventListener(hub, eventListenerFactory)
                eventListener != null -> SentryOkHttpEventListener(hub, eventListener)
                else -> SentryOkHttpEventListener(hub)
            }
            return builder.eventListener(sentryOkHttpEventListener).build()
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
        assertEquals(requestBody.toByteArray().size.toLong(), requestBodySpan.data["http.request_content_length"])
        assertEquals(requestBody.toByteArray().size.toLong(), callSpan?.getData("http.request_content_length"))
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
        assertEquals(responseBytes.size.toLong(), requestBodySpan.data["http.response_content_length"])
        assertEquals(responseBytes.size.toLong(), callSpan?.getData("http.response_content_length"))
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

    @Test
    fun `propagate all calls to the event listener passed in the ctor`() {
        val sut = fixture.getSut(eventListener = fixture.mockEventListener, httpStatusCode = 500)
        val listener = fixture.sentryOkHttpEventListener
        val request = postRequest(body = "requestBody")
        val call = sut.newCall(request)
        val response = mock<Response>()
        whenever(response.protocol).thenReturn(Protocol.HTTP_1_1)

        listener.callStart(call)
        verify(fixture.mockEventListener).callStart(eq(call))
        listener.proxySelectStart(call, mock())
        verify(fixture.mockEventListener).proxySelectStart(eq(call), any())
        listener.proxySelectEnd(call, mock(), listOf(mock()))
        verify(fixture.mockEventListener).proxySelectEnd(eq(call), any(), any())
        listener.dnsStart(call, "domainName")
        verify(fixture.mockEventListener).dnsStart(eq(call), eq("domainName"))
        listener.dnsEnd(call, "domainName", listOf(mock()))
        verify(fixture.mockEventListener).dnsEnd(eq(call), eq("domainName"), any())
        listener.connectStart(call, mock(), mock())
        verify(fixture.mockEventListener).connectStart(eq(call), any(), any())
        listener.secureConnectStart(call)
        verify(fixture.mockEventListener).secureConnectStart(eq(call))
        listener.secureConnectEnd(call, mock())
        verify(fixture.mockEventListener).secureConnectEnd(eq(call), any())
        listener.connectEnd(call, mock(), mock(), mock())
        verify(fixture.mockEventListener).connectEnd(eq(call), any(), any(), any())
        listener.connectFailed(call, mock(), mock(), mock(), mock())
        verify(fixture.mockEventListener).connectFailed(eq(call), any(), any(), any(), any())
        listener.connectionAcquired(call, mock())
        verify(fixture.mockEventListener).connectionAcquired(eq(call), any())
        listener.connectionReleased(call, mock())
        verify(fixture.mockEventListener).connectionReleased(eq(call), any())
        listener.requestHeadersStart(call)
        verify(fixture.mockEventListener).requestHeadersStart(eq(call))
        listener.requestHeadersEnd(call, mock())
        verify(fixture.mockEventListener).requestHeadersEnd(eq(call), any())
        listener.requestBodyStart(call)
        verify(fixture.mockEventListener).requestBodyStart(eq(call))
        listener.requestBodyEnd(call, 10)
        verify(fixture.mockEventListener).requestBodyEnd(eq(call), eq(10))
        listener.requestFailed(call, mock())
        verify(fixture.mockEventListener).requestFailed(eq(call), any())
        listener.responseHeadersStart(call)
        verify(fixture.mockEventListener).responseHeadersStart(eq(call))
        listener.responseHeadersEnd(call, response)
        verify(fixture.mockEventListener).responseHeadersEnd(eq(call), any())
        listener.responseBodyStart(call)
        verify(fixture.mockEventListener).responseBodyStart(eq(call))
        listener.responseBodyEnd(call, 10)
        verify(fixture.mockEventListener).responseBodyEnd(eq(call), eq(10))
        listener.responseFailed(call, mock())
        verify(fixture.mockEventListener).responseFailed(eq(call), any())
        listener.callEnd(call)
        verify(fixture.mockEventListener).callEnd(eq(call))
        listener.callFailed(call, mock())
        verify(fixture.mockEventListener).callFailed(eq(call), any())
    }

    @Test
    fun `propagate all calls to the event listener factory passed in the ctor`() {
        val sut = fixture.getSut(eventListenerFactory = fixture.mockEventListenerFactory, httpStatusCode = 500)
        val listener = fixture.sentryOkHttpEventListener
        val request = postRequest(body = "requestBody")
        val call = sut.newCall(request)
        val response = mock<Response>()
        whenever(response.protocol).thenReturn(Protocol.HTTP_1_1)

        listener.callStart(call)
        verify(fixture.mockEventListener).callStart(eq(call))
        listener.proxySelectStart(call, mock())
        verify(fixture.mockEventListener).proxySelectStart(eq(call), any())
        listener.proxySelectEnd(call, mock(), listOf(mock()))
        verify(fixture.mockEventListener).proxySelectEnd(eq(call), any(), any())
        listener.dnsStart(call, "domainName")
        verify(fixture.mockEventListener).dnsStart(eq(call), eq("domainName"))
        listener.dnsEnd(call, "domainName", listOf(mock()))
        verify(fixture.mockEventListener).dnsEnd(eq(call), eq("domainName"), any())
        listener.connectStart(call, mock(), mock())
        verify(fixture.mockEventListener).connectStart(eq(call), any(), any())
        listener.secureConnectStart(call)
        verify(fixture.mockEventListener).secureConnectStart(eq(call))
        listener.secureConnectEnd(call, mock())
        verify(fixture.mockEventListener).secureConnectEnd(eq(call), any())
        listener.connectEnd(call, mock(), mock(), mock())
        verify(fixture.mockEventListener).connectEnd(eq(call), any(), any(), any())
        listener.connectFailed(call, mock(), mock(), mock(), mock())
        verify(fixture.mockEventListener).connectFailed(eq(call), any(), any(), any(), any())
        listener.connectionAcquired(call, mock())
        verify(fixture.mockEventListener).connectionAcquired(eq(call), any())
        listener.connectionReleased(call, mock())
        verify(fixture.mockEventListener).connectionReleased(eq(call), any())
        listener.requestHeadersStart(call)
        verify(fixture.mockEventListener).requestHeadersStart(eq(call))
        listener.requestHeadersEnd(call, mock())
        verify(fixture.mockEventListener).requestHeadersEnd(eq(call), any())
        listener.requestBodyStart(call)
        verify(fixture.mockEventListener).requestBodyStart(eq(call))
        listener.requestBodyEnd(call, 10)
        verify(fixture.mockEventListener).requestBodyEnd(eq(call), eq(10))
        listener.requestFailed(call, mock())
        verify(fixture.mockEventListener).requestFailed(eq(call), any())
        listener.responseHeadersStart(call)
        verify(fixture.mockEventListener).responseHeadersStart(eq(call))
        listener.responseHeadersEnd(call, response)
        verify(fixture.mockEventListener).responseHeadersEnd(eq(call), any())
        listener.responseBodyStart(call)
        verify(fixture.mockEventListener).responseBodyStart(eq(call))
        listener.responseBodyEnd(call, 10)
        verify(fixture.mockEventListener).responseBodyEnd(eq(call), eq(10))
        listener.responseFailed(call, mock())
        verify(fixture.mockEventListener).responseFailed(eq(call), any())
        listener.callEnd(call)
        verify(fixture.mockEventListener).callEnd(eq(call))
        listener.callFailed(call, mock())
        verify(fixture.mockEventListener).callFailed(eq(call), any())
    }
}
