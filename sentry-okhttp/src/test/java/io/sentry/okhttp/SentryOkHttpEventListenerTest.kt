package io.sentry.okhttp

import io.sentry.BaggageHeader
import io.sentry.IHub
import io.sentry.SentryOptions
import io.sentry.SentryTraceHeader
import io.sentry.SentryTracer
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.test.ImmediateExecutorService
import okhttp3.Call
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
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
            configureOptions: (options: SentryOptions) -> Unit = {},
            eventListener: EventListener? = null,
            eventListenerFactory: EventListener.Factory? = null
        ): OkHttpClient {
            options = SentryOptions().apply {
                dsn = "https://key@sentry.io/proj"
                isSendDefaultPii = sendDefaultPii
                configureOptions(this)
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
                    assertEquals(201, span.data[SpanDataConvention.HTTP_STATUS_CODE_KEY])
                }
                1 -> {
                    assertEquals("http.client.proxy_select", span.operation)
                    assertEquals("http.client.proxy_select GET ${request.url}", span.description)
                    assertNotNull(span.data["proxies"])
                }
                2 -> {
                    assertEquals("http.client.dns", span.operation)
                    assertEquals("http.client.dns GET ${request.url}", span.description)
                    assertNotNull(span.data["domain_name"])
                    assertNotNull(span.data["dns_addresses"])
                }
                3 -> {
                    assertEquals("http.client.connect", span.operation)
                    assertEquals("http.client.connect GET ${request.url}", span.description)
                }
                4 -> {
                    assertEquals("http.client.connection", span.operation)
                    assertEquals("http.client.connection GET ${request.url}", span.description)
                }
                5 -> {
                    assertEquals("http.client.request_headers", span.operation)
                    assertEquals("http.client.request_headers GET ${request.url}", span.description)
                }
                6 -> {
                    assertEquals("http.client.response_headers", span.operation)
                    assertEquals("http.client.response_headers GET ${request.url}", span.description)
                    assertEquals(201, span.data[SpanDataConvention.HTTP_STATUS_CODE_KEY])
                }
                7 -> {
                    assertEquals("http.client.response_body", span.operation)
                    assertEquals("http.client.response_body GET ${request.url}", span.description)
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
        val requestBodySpan = fixture.sentryTracer.children.firstOrNull { it.operation == "http.client.request_body" }
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
        val requestBodySpan = fixture.sentryTracer.children.firstOrNull { it.operation == "http.client.response_body" }
        assertNotNull(requestBodySpan)
        assertEquals(
            responseBytes.size.toLong(),
            requestBodySpan.data[SpanDataConvention.HTTP_RESPONSE_CONTENT_LENGTH_KEY]
        )
        assertEquals(
            responseBytes.size.toLong(),
            callSpan?.getData(SpanDataConvention.HTTP_RESPONSE_CONTENT_LENGTH_KEY)
        )
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
        val sut = fixture.getSut(eventListener = fixture.mockEventListener)
        val listener = fixture.sentryOkHttpEventListener
        val request = postRequest(body = "requestBody")
        val call = sut.newCall(request)
        val response = mock<Response>()
        whenever(response.protocol).thenReturn(Protocol.HTTP_1_1)
        verifyDelegation(listener, fixture.mockEventListener, call, response)
    }

    @Test
    fun `propagate all calls to the event listener factory passed in the ctor`() {
        val sut = fixture.getSut(eventListenerFactory = fixture.mockEventListenerFactory)
        val listener = fixture.sentryOkHttpEventListener
        val request = postRequest(body = "requestBody")
        val call = sut.newCall(request)
        val response = mock<Response>()
        whenever(response.protocol).thenReturn(Protocol.HTTP_1_1)
        verifyDelegation(listener, fixture.mockEventListener, call, response)
    }

    @Test
    fun `propagate all calls to the SentryOkHttpEventListener passed in the ctor`() {
        val originalListener = spy(SentryOkHttpEventListener(fixture.hub, fixture.mockEventListener))
        val sut = fixture.getSut(eventListener = originalListener)
        val listener = fixture.sentryOkHttpEventListener
        val request = postRequest(body = "requestBody")
        val call = sut.newCall(request)
        val response = mock<Response>()
        whenever(response.protocol).thenReturn(Protocol.HTTP_1_1)
        verifyDelegation(listener, originalListener, call, response)
    }

    @Test
    fun `propagate all calls to the SentryOkHttpEventListener factory passed in the ctor`() {
        val originalListener = spy(SentryOkHttpEventListener(fixture.hub, fixture.mockEventListener))
        val sut = fixture.getSut(eventListenerFactory = { originalListener })
        val listener = fixture.sentryOkHttpEventListener
        val request = postRequest(body = "requestBody")
        val call = sut.newCall(request)
        val response = mock<Response>()
        whenever(response.protocol).thenReturn(Protocol.HTTP_1_1)
        verifyDelegation(listener, originalListener, call, response)
    }

    @Test
    fun `does not duplicated spans if an SentryOkHttpEventListener is passed in the ctor`() {
        val originalListener = spy(SentryOkHttpEventListener(fixture.hub, fixture.mockEventListener))
        val sut = fixture.getSut(eventListener = originalListener)
        val request = postRequest(body = "requestBody")
        val call = sut.newCall(request)
        val response = call.execute()
        response.close()
        // Spans are created by the originalListener, so the listener doesn't create duplicates
        assertEquals(9, fixture.sentryTracer.children.size)
    }

    @Test
    fun `status propagates to parent span and call root span`() {
        val sut = fixture.getSut(httpStatusCode = 500)
        val request = getRequest()
        val call = sut.newCall(request)
        val response = call.execute()
        val okHttpEvent = SentryOkHttpEventListener.eventMap[call]
        val callSpan = okHttpEvent?.callRootSpan
        val responseHeaderSpan =
            fixture.sentryTracer.children.firstOrNull { it.operation == "http.client.response_headers" }
        val connectionSpan = fixture.sentryTracer.children.firstOrNull { it.operation == "http.client.connection" }
        response.close()
        assertNotNull(callSpan)
        assertNotNull(responseHeaderSpan)
        assertNotNull(connectionSpan)
        assertEquals(SpanStatus.fromHttpStatusCode(500), callSpan.status)
        assertEquals(SpanStatus.fromHttpStatusCode(500), responseHeaderSpan.status)
        assertEquals(SpanStatus.fromHttpStatusCode(500), connectionSpan.status)
    }

    @Test
    fun `when response is not closed, root call is trimmed to responseHeadersEnd`() {
        val sut = fixture.getSut(
            httpStatusCode = 500,
            configureOptions = { it.executorService = ImmediateExecutorService() }
        )
        val request = getRequest()
        val call = sut.newCall(request)
        val response = spy(call.execute())
        val okHttpEvent = SentryOkHttpEventListener.eventMap[call]
        val callSpan = okHttpEvent?.callRootSpan
        val responseHeaderSpan =
            fixture.sentryTracer.children.firstOrNull { it.operation == "http.client.response_headers" }
        val responseBodySpan = fixture.sentryTracer.children.firstOrNull { it.operation == "http.client.response_body" }

        // Response is not finished
        verify(response, never()).close()

        // response body span is never started
        assertNull(responseBodySpan)

        assertNotNull(callSpan)
        assertNotNull(responseHeaderSpan)

        // Call span is trimmed to responseHeader finishTimestamp
        assertEquals(callSpan.finishDate?.nanoTimestamp(), responseHeaderSpan.finishDate?.nanoTimestamp())

        // All children spans of the root call are finished
        assertTrue(fixture.sentryTracer.children.all { it.isFinished })
    }

    @Test
    fun `responseHeadersEnd schedules event finish`() {
        val listener = SentryOkHttpEventListener(fixture.hub, fixture.mockEventListener)
        whenever(fixture.hub.options).thenReturn(SentryOptions())
        val call = mock<Call>()
        whenever(call.request()).thenReturn(getRequest())
        val okHttpEvent = mock<SentryOkHttpEvent>()
        SentryOkHttpEventListener.eventMap[call] = okHttpEvent
        listener.responseHeadersEnd(call, mock())
        verify(okHttpEvent).scheduleFinish(any())
    }

    @Test
    fun `call root span status is not overridden if not null`() {
        val mockListener = mock<EventListener>()
        lateinit var call: Call
        whenever(mockListener.connectStart(any(), anyOrNull(), anyOrNull())).then {
            val okHttpEvent = SentryOkHttpEventListener.eventMap[call]
            val callSpan = okHttpEvent?.callRootSpan
            assertNotNull(callSpan)
            assertNull(callSpan.status)
            callSpan.status = SpanStatus.UNKNOWN
            it
        }
        val sut = fixture.getSut(eventListener = mockListener)
        val request = getRequest()
        call = sut.newCall(request)
        val response = call.execute()
        val okHttpEvent = SentryOkHttpEventListener.eventMap[call]
        val callSpan = okHttpEvent?.callRootSpan
        assertNotNull(callSpan)
        response.close()
        assertEquals(SpanStatus.UNKNOWN, callSpan.status)
    }

    private fun verifyDelegation(
        listener: SentryOkHttpEventListener,
        originalListener: EventListener,
        call: Call,
        response: Response
    ) {
        listener.callStart(call)
        verify(originalListener).callStart(eq(call))
        listener.proxySelectStart(call, mock())
        verify(originalListener).proxySelectStart(eq(call), any())
        listener.proxySelectEnd(call, mock(), listOf(mock()))
        verify(originalListener).proxySelectEnd(eq(call), any(), any())
        listener.dnsStart(call, "domainName")
        verify(originalListener).dnsStart(eq(call), eq("domainName"))
        listener.dnsEnd(call, "domainName", listOf(mock()))
        verify(originalListener).dnsEnd(eq(call), eq("domainName"), any())
        listener.connectStart(call, mock(), mock())
        verify(originalListener).connectStart(eq(call), any(), any())
        listener.secureConnectStart(call)
        verify(originalListener).secureConnectStart(eq(call))
        listener.secureConnectEnd(call, mock())
        verify(originalListener).secureConnectEnd(eq(call), any())
        listener.connectEnd(call, mock(), mock(), mock())
        verify(originalListener).connectEnd(eq(call), any(), any(), any())
        listener.connectFailed(call, mock(), mock(), mock(), mock())
        verify(originalListener).connectFailed(eq(call), any(), any(), any(), any())
        listener.connectionAcquired(call, mock())
        verify(originalListener).connectionAcquired(eq(call), any())
        listener.connectionReleased(call, mock())
        verify(originalListener).connectionReleased(eq(call), any())
        listener.requestHeadersStart(call)
        verify(originalListener).requestHeadersStart(eq(call))
        listener.requestHeadersEnd(call, mock())
        verify(originalListener).requestHeadersEnd(eq(call), any())
        listener.requestBodyStart(call)
        verify(originalListener).requestBodyStart(eq(call))
        listener.requestBodyEnd(call, 10)
        verify(originalListener).requestBodyEnd(eq(call), eq(10))
        listener.requestFailed(call, mock())
        verify(originalListener).requestFailed(eq(call), any())
        listener.responseHeadersStart(call)
        verify(originalListener).responseHeadersStart(eq(call))
        listener.responseHeadersEnd(call, response)
        verify(originalListener).responseHeadersEnd(eq(call), any())
        listener.responseBodyStart(call)
        verify(originalListener).responseBodyStart(eq(call))
        listener.responseBodyEnd(call, 10)
        verify(originalListener).responseBodyEnd(eq(call), eq(10))
        listener.responseFailed(call, mock())
        verify(originalListener).responseFailed(eq(call), any())
        listener.callEnd(call)
        verify(originalListener).callEnd(eq(call))
        listener.callFailed(call, mock())
        verify(originalListener).callFailed(eq(call), any())
    }
}
