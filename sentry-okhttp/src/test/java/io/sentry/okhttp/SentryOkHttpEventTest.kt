package io.sentry.okhttp

import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.ISentryExecutorService
import io.sentry.ISpan
import io.sentry.SentryDate
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.Span
import io.sentry.SpanDataConvention
import io.sentry.SpanOptions
import io.sentry.SpanStatus
import io.sentry.TracesSamplingDecision
import io.sentry.TransactionContext
import io.sentry.TypeCheckHint
import io.sentry.exception.SentryHttpClientException
import io.sentry.okhttp.SentryOkHttpEventListener.Companion.CONNECTION_EVENT
import io.sentry.okhttp.SentryOkHttpEventListener.Companion.CONNECT_EVENT
import io.sentry.okhttp.SentryOkHttpEventListener.Companion.REQUEST_BODY_EVENT
import io.sentry.okhttp.SentryOkHttpEventListener.Companion.REQUEST_HEADERS_EVENT
import io.sentry.okhttp.SentryOkHttpEventListener.Companion.RESPONSE_BODY_EVENT
import io.sentry.okhttp.SentryOkHttpEventListener.Companion.RESPONSE_HEADERS_EVENT
import io.sentry.okhttp.SentryOkHttpEventListener.Companion.SECURE_CONNECT_EVENT
import io.sentry.test.ImmediateExecutorService
import io.sentry.test.getProperty
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockWebServer
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.RejectedExecutionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentryOkHttpEventTest {
    private class Fixture {
        val scopes = mock<IScopes>()
        val server = MockWebServer()
        val span: ISpan
        val mockRequest: Request
        val response: Response

        init {
            whenever(scopes.options).thenReturn(
                SentryOptions().apply {
                    dsn = "https://key@sentry.io/proj"
                }
            )

            span = Span(
                TransactionContext("name", "op", TracesSamplingDecision(true)),
                SentryTracer(TransactionContext("name", "op", TracesSamplingDecision(true)), scopes),
                scopes,
                SpanOptions()
            )

            mockRequest = Request.Builder()
                .addHeader("myHeader", "myValue")
                .get()
                .url(server.url("/hello"))
                .build()

            response = Response.Builder()
                .code(200)
                .message("message")
                .request(mockRequest)
                .protocol(Protocol.HTTP_1_1)
                .build()
        }

        fun getSut(currentSpan: ISpan? = span, requestUrl: String ? = null): SentryOkHttpEvent {
            whenever(scopes.span).thenReturn(currentSpan)
            val request = if (requestUrl == null) {
                mockRequest
            } else {
                Request.Builder()
                    .addHeader("myHeader", "myValue")
                    .get()
                    .url(server.url(requestUrl))
                    .build()
            }
            return SentryOkHttpEvent(scopes, request)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when there is no active span, root span is null`() {
        val sut = fixture.getSut(currentSpan = null)
        assertNull(sut.callRootSpan)
    }

    @Test
    fun `when there is an active span, a root span is created`() {
        val sut = fixture.getSut()
        val callSpan = sut.callRootSpan
        assertNotNull(callSpan)
        assertEquals("http.client", callSpan.operation)
        assertEquals("${fixture.mockRequest.method} ${fixture.mockRequest.url}", callSpan.description)
        assertEquals(fixture.mockRequest.url.toString(), callSpan.getData("url"))
        assertEquals(fixture.mockRequest.url.host, callSpan.getData("host"))
        assertEquals(fixture.mockRequest.url.encodedPath, callSpan.getData("path"))
        assertEquals(fixture.mockRequest.method, callSpan.getData(SpanDataConvention.HTTP_METHOD_KEY))
    }

    @Test
    fun `when root span is null, breadcrumb is created anyway`() {
        val sut = fixture.getSut(currentSpan = null)
        assertNull(sut.callRootSpan)
        sut.finishEvent()
        verify(fixture.scopes).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
    }

    @Test
    fun `when root span is null, no span is created`() {
        val sut = fixture.getSut(currentSpan = null)
        assertNull(sut.callRootSpan)
        sut.startSpan("span")
        assertTrue(sut.getEventSpans().isEmpty())
    }

    @Test
    fun `when event is finished, root span is finished`() {
        val sut = fixture.getSut()
        val rootSpan = sut.callRootSpan
        assertNotNull(rootSpan)
        assertFalse(rootSpan.isFinished)
        sut.finishEvent()
        assertTrue(rootSpan.isFinished)
    }

    @Test
    fun `when startSpan, a new span is started`() {
        val sut = fixture.getSut()
        assertTrue(sut.getEventSpans().isEmpty())
        sut.startSpan("span")
        val spans = sut.getEventSpans()
        assertEquals(1, spans.size)
        val span = spans["span"]
        assertNotNull(span)
        assertTrue(spans.containsKey("span"))
        assertEquals("http.client.span", span.operation)
        assertFalse(span.isFinished)
    }

    @Test
    fun `when finishSpan, a span is finished if previously started`() {
        val sut = fixture.getSut()
        assertTrue(sut.getEventSpans().isEmpty())
        sut.startSpan("span")
        val spans = sut.getEventSpans()
        assertFalse(spans["span"]!!.isFinished)
        sut.finishSpan("span")
        assertTrue(spans["span"]!!.isFinished)
    }

    @Test
    fun `when finishSpan, a callback is called before the span is finished`() {
        val sut = fixture.getSut()
        var called = false
        assertTrue(sut.getEventSpans().isEmpty())
        sut.startSpan("span")
        val spans = sut.getEventSpans()
        assertFalse(spans["span"]!!.isFinished)
        sut.finishSpan("span") {
            called = true
            assertFalse(it.isFinished)
        }
        assertTrue(spans["span"]!!.isFinished)
        assertTrue(called)
    }

    @Test
    fun `when finishSpan, a callback is called with the current span and the root call span is finished`() {
        val sut = fixture.getSut()
        var called = 0
        sut.startSpan("span")
        sut.finishSpan("span") {
            if (called == 0) {
                assertEquals("http.client.span", it.operation)
            } else {
                assertEquals(sut.callRootSpan, it)
            }
            called++
            assertFalse(it.isFinished)
        }
        assertEquals(2, called)
    }

    @Test
    fun `finishSpan is ignored if the span was not previously started`() {
        val sut = fixture.getSut()
        var called = false
        assertTrue(sut.getEventSpans().isEmpty())
        sut.finishSpan("span") { called = true }
        assertTrue(sut.getEventSpans().isEmpty())
        assertFalse(called)
    }

    @Test
    fun `when finishEvent, a callback is called with the call root span before it is finished`() {
        val sut = fixture.getSut()
        var called = false
        sut.finishEvent {
            called = true
            assertEquals(sut.callRootSpan, it)
        }
        assertTrue(called)
    }

    @Test
    fun `when finishEvent, all running spans are finished`() {
        val sut = fixture.getSut()
        sut.startSpan("span")
        val spans = sut.getEventSpans()
        assertFalse(spans["span"]!!.isFinished)
        sut.finishEvent()
        assertTrue(spans["span"]!!.isFinished)
    }

    @Test
    fun `when finishEvent, a breadcrumb is captured with request in the hint`() {
        val sut = fixture.getSut()
        sut.finishEvent()
        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals(fixture.mockRequest.url.toString(), it.data["url"])
                assertEquals(fixture.mockRequest.url.host, it.data["host"])
                assertEquals(fixture.mockRequest.url.encodedPath, it.data["path"])
                assertEquals(fixture.mockRequest.method, it.data["method"])
            },
            check {
                assertEquals(fixture.mockRequest, it[TypeCheckHint.OKHTTP_REQUEST])
            }
        )
    }

    @Test
    fun `when finishEvent multiple times, only one breadcrumb is captured`() {
        val sut = fixture.getSut()
        sut.finishEvent()
        sut.finishEvent()
        verify(fixture.scopes, times(1)).addBreadcrumb(any<Breadcrumb>(), any())
    }

    @Test
    fun `when finishEvent, does not override running spans status if set`() {
        val sut = fixture.getSut()
        sut.startSpan("span")
        val spans = sut.getEventSpans()
        assertNull(spans["span"]!!.status)
        spans["span"]!!.status = SpanStatus.OK
        assertEquals(SpanStatus.OK, spans["span"]!!.status)
        sut.finishEvent()
        assertTrue(spans["span"]!!.isFinished)
        assertEquals(SpanStatus.OK, spans["span"]!!.status)
    }

    @Test
    fun `setResponse set protocol and code in the breadcrumb and root span, and response in the hint`() {
        val sut = fixture.getSut()
        sut.setResponse(fixture.response)

        assertEquals(fixture.response.protocol.name, sut.callRootSpan?.getData("protocol"))
        assertEquals(fixture.response.code, sut.callRootSpan?.getData(SpanDataConvention.HTTP_STATUS_CODE_KEY))
        sut.finishEvent()

        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals(fixture.response.protocol.name, it.data["protocol"])
                assertEquals(fixture.response.code, it.data["status_code"])
            },
            check {
                assertEquals(fixture.response, it[TypeCheckHint.OKHTTP_RESPONSE])
            }
        )
    }

    @Test
    fun `setProtocol set protocol in the breadcrumb and in the root span`() {
        val sut = fixture.getSut()
        sut.setProtocol("protocol")
        assertEquals("protocol", sut.callRootSpan?.getData("protocol"))
        sut.finishEvent()
        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("protocol", it.data["protocol"])
            },
            any()
        )
    }

    @Test
    fun `setProtocol is ignored if protocol is null`() {
        val sut = fixture.getSut()
        sut.setProtocol(null)
        assertNull(sut.callRootSpan?.getData("protocol"))
        sut.finishEvent()
        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertNull(it.data["protocol"])
            },
            any()
        )
    }

    @Test
    fun `setRequestBodySize set RequestBodySize in the breadcrumb and in the root span`() {
        val sut = fixture.getSut()
        sut.setRequestBodySize(10)
        assertEquals(10L, sut.callRootSpan?.getData("http.request_content_length"))
        sut.finishEvent()
        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals(10L, it.data["request_content_length"])
            },
            any()
        )
    }

    @Test
    fun `setRequestBodySize is ignored if RequestBodySize is negative`() {
        val sut = fixture.getSut()
        sut.setRequestBodySize(-1)
        assertNull(sut.callRootSpan?.getData("http.request_content_length"))
        sut.finishEvent()
        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertNull(it.data["request_content_length"])
            },
            any()
        )
    }

    @Test
    fun `setResponseBodySize set ResponseBodySize in the breadcrumb and in the root span`() {
        val sut = fixture.getSut()
        sut.setResponseBodySize(10)
        assertEquals(10L, sut.callRootSpan?.getData(SpanDataConvention.HTTP_RESPONSE_CONTENT_LENGTH_KEY))
        sut.finishEvent()
        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals(10L, it.data["response_content_length"])
            },
            any()
        )
    }

    @Test
    fun `setResponseBodySize is ignored if ResponseBodySize is negative`() {
        val sut = fixture.getSut()
        sut.setResponseBodySize(-1)
        assertNull(sut.callRootSpan?.getData(SpanDataConvention.HTTP_RESPONSE_CONTENT_LENGTH_KEY))
        sut.finishEvent()
        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertNull(it.data["response_content_length"])
            },
            any()
        )
    }

    @Test
    fun `setError set success to false and errorMessage in the breadcrumb and in the root span`() {
        val sut = fixture.getSut()
        sut.setError("errorMessage")
        assertEquals("errorMessage", sut.callRootSpan?.getData("error_message"))
        sut.finishEvent()
        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("errorMessage", it.data["error_message"])
            },
            any()
        )
    }

    @Test
    fun `setError sets success to false in the breadcrumb and in the root span even if errorMessage is null`() {
        val sut = fixture.getSut()
        sut.setError(null)
        assertNotNull(sut.callRootSpan)
        assertNull(sut.callRootSpan.getData("error_message"))
        sut.finishEvent()
        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertNull(it.data["error_message"])
            },
            any()
        )
    }

    @Test
    fun `secureConnect span is child of connect span`() {
        val sut = fixture.getSut()
        sut.startSpan(CONNECT_EVENT)
        sut.startSpan(SECURE_CONNECT_EVENT)
        val spans = sut.getEventSpans()
        val secureConnectSpan = spans[SECURE_CONNECT_EVENT] as Span?
        val connectSpan = spans[CONNECT_EVENT] as Span?
        assertNotNull(secureConnectSpan)
        assertNotNull(connectSpan)
        assertEquals(connectSpan.spanId, secureConnectSpan.parentSpanId)
    }

    @Test
    fun `secureConnect span is child of root span if connect span is not available`() {
        val sut = fixture.getSut()
        sut.startSpan(SECURE_CONNECT_EVENT)
        val spans = sut.getEventSpans()
        val rootSpan = sut.callRootSpan as Span?
        val secureConnectSpan = spans[SECURE_CONNECT_EVENT] as Span?
        assertNotNull(secureConnectSpan)
        assertNotNull(rootSpan)
        assertEquals(rootSpan.spanId, secureConnectSpan.parentSpanId)
    }

    @Test
    fun `request and response spans are children of connection span`() {
        val sut = fixture.getSut()
        sut.startSpan(CONNECTION_EVENT)
        sut.startSpan(REQUEST_HEADERS_EVENT)
        sut.startSpan(REQUEST_BODY_EVENT)
        sut.startSpan(RESPONSE_HEADERS_EVENT)
        sut.startSpan(RESPONSE_BODY_EVENT)
        val spans = sut.getEventSpans()
        val connectionSpan = spans[CONNECTION_EVENT] as Span?
        val requestHeadersSpan = spans[REQUEST_HEADERS_EVENT] as Span?
        val requestBodySpan = spans[REQUEST_BODY_EVENT] as Span?
        val responseHeadersSpan = spans[RESPONSE_HEADERS_EVENT] as Span?
        val responseBodySpan = spans[RESPONSE_BODY_EVENT] as Span?
        assertNotNull(connectionSpan)
        assertEquals(connectionSpan.spanId, requestHeadersSpan?.parentSpanId)
        assertEquals(connectionSpan.spanId, requestBodySpan?.parentSpanId)
        assertEquals(connectionSpan.spanId, responseHeadersSpan?.parentSpanId)
        assertEquals(connectionSpan.spanId, responseBodySpan?.parentSpanId)
    }

    @Test
    fun `request and response spans are children of root span if connection span is not available`() {
        val sut = fixture.getSut()
        sut.startSpan(REQUEST_HEADERS_EVENT)
        sut.startSpan(REQUEST_BODY_EVENT)
        sut.startSpan(RESPONSE_HEADERS_EVENT)
        sut.startSpan(RESPONSE_BODY_EVENT)
        val spans = sut.getEventSpans()
        val connectionSpan = spans[CONNECTION_EVENT] as Span?
        val requestHeadersSpan = spans[REQUEST_HEADERS_EVENT] as Span?
        val requestBodySpan = spans[REQUEST_BODY_EVENT] as Span?
        val responseHeadersSpan = spans[RESPONSE_HEADERS_EVENT] as Span?
        val responseBodySpan = spans[RESPONSE_BODY_EVENT] as Span?
        val rootSpan = sut.callRootSpan as Span?
        assertNotNull(rootSpan)
        assertNull(connectionSpan)
        assertEquals(rootSpan.spanId, requestHeadersSpan?.parentSpanId)
        assertEquals(rootSpan.spanId, requestBodySpan?.parentSpanId)
        assertEquals(rootSpan.spanId, responseHeadersSpan?.parentSpanId)
        assertEquals(rootSpan.spanId, responseBodySpan?.parentSpanId)
    }

    @Test
    fun `finishSpan beforeFinish is called on span, parent and call root span`() {
        val sut = fixture.getSut()
        sut.startSpan(CONNECTION_EVENT)
        sut.startSpan(REQUEST_HEADERS_EVENT)
        sut.startSpan("random event")
        sut.finishSpan(REQUEST_HEADERS_EVENT) { it.status = SpanStatus.INTERNAL_ERROR }
        sut.finishSpan("random event") { it.status = SpanStatus.DEADLINE_EXCEEDED }
        sut.finishSpan(CONNECTION_EVENT)
        sut.finishEvent()
        val spans = sut.getEventSpans()
        val connectionSpan = spans[CONNECTION_EVENT] as Span?
        val requestHeadersSpan = spans[REQUEST_HEADERS_EVENT] as Span?
        val randomEventSpan = spans["random event"] as Span?
        assertNotNull(connectionSpan)
        assertNotNull(requestHeadersSpan)
        assertNotNull(randomEventSpan)
        // requestHeadersSpan was finished with INTERNAL_ERROR
        assertEquals(SpanStatus.INTERNAL_ERROR, requestHeadersSpan.status)
        // randomEventSpan was finished with DEADLINE_EXCEEDED
        assertEquals(SpanStatus.DEADLINE_EXCEEDED, randomEventSpan.status)
        // requestHeadersSpan was finished with INTERNAL_ERROR, and it propagates to its parent
        assertEquals(SpanStatus.INTERNAL_ERROR, connectionSpan.status)
        // random event was finished last with DEADLINE_EXCEEDED, and it propagates to root call
        assertEquals(SpanStatus.DEADLINE_EXCEEDED, sut.callRootSpan!!.status)
    }

    @Test
    fun `finishEvent moves throwables from inner span to call root span`() {
        val sut = fixture.getSut()
        val throwable = RuntimeException()
        sut.startSpan(CONNECTION_EVENT)
        sut.startSpan("random event")
        sut.finishSpan("random event") { it.status = SpanStatus.DEADLINE_EXCEEDED }
        sut.finishSpan(CONNECTION_EVENT) {
            it.status = SpanStatus.INTERNAL_ERROR
            it.throwable = throwable
        }
        sut.finishEvent()
        val spans = sut.getEventSpans()
        val connectionSpan = spans[CONNECTION_EVENT] as Span?
        val randomEventSpan = spans["random event"] as Span?
        assertNotNull(connectionSpan)
        assertNotNull(randomEventSpan)
        // randomEventSpan was finished with DEADLINE_EXCEEDED
        assertEquals(SpanStatus.DEADLINE_EXCEEDED, randomEventSpan.status)
        // connectionSpan was finished with INTERNAL_ERROR
        assertEquals(SpanStatus.INTERNAL_ERROR, connectionSpan.status)

        // connectionSpan was finished last with INTERNAL_ERROR and a throwable, and it's moved to the root call
        assertEquals(SpanStatus.INTERNAL_ERROR, sut.callRootSpan!!.status)
        assertEquals(throwable, sut.callRootSpan.throwable)
        assertNull(connectionSpan.throwable)
    }

    @Test
    fun `scheduleFinish schedules finishEvent and finish running spans to specific timestamp`() {
        fixture.scopes.options.executorService = ImmediateExecutorService()
        val sut = spy(fixture.getSut())
        val timestamp = mock<SentryDate>()
        sut.startSpan(CONNECTION_EVENT)
        sut.scheduleFinish(timestamp)
        verify(sut).finishEvent(eq(timestamp), anyOrNull())
        val spans = sut.getEventSpans()
        assertEquals(timestamp, spans[CONNECTION_EVENT]?.finishDate)
    }

    @Test
    fun `finishEvent with timestamp trims call root span`() {
        val sut = fixture.getSut()
        val timestamp = mock<SentryDate>()
        sut.finishEvent(finishDate = timestamp)
        assertEquals(timestamp, sut.callRootSpan!!.finishDate)
    }

    @Test
    fun `scheduleFinish does not throw if executor is shut down`() {
        val executorService = mock<ISentryExecutorService>()
        whenever(executorService.schedule(any(), any())).thenThrow(RejectedExecutionException())
        whenever(fixture.scopes.options).thenReturn(SentryOptions().apply { this.executorService = executorService })
        val sut = fixture.getSut()
        sut.scheduleFinish(mock())
    }

    @Test
    fun `setClientErrorResponse will capture the client error on finishEvent`() {
        val sut = fixture.getSut()
        val clientErrorResponse = mock<Response>()
        whenever(clientErrorResponse.request).thenReturn(fixture.mockRequest)
        sut.setClientErrorResponse(clientErrorResponse)
        verify(fixture.scopes, never()).captureEvent(any(), any<Hint>())
        sut.finishEvent()
        assertNotNull(sut.callRootSpan)
        verify(fixture.scopes).captureEvent(
            argThat {
                throwable is SentryHttpClientException &&
                    throwable!!.message!!.startsWith("HTTP Client Error with status code: ")
            },
            argThat<Hint> {
                get(TypeCheckHint.OKHTTP_REQUEST) != null &&
                    get(TypeCheckHint.OKHTTP_RESPONSE) != null
            }
        )
    }

    @Test
    fun `setClientErrorResponse will capture the client error on finishEvent even when no span is running`() {
        val sut = fixture.getSut(currentSpan = null)
        val clientErrorResponse = mock<Response>()
        whenever(clientErrorResponse.request).thenReturn(fixture.mockRequest)
        sut.setClientErrorResponse(clientErrorResponse)
        verify(fixture.scopes, never()).captureEvent(any(), any<Hint>())
        sut.finishEvent()
        assertNull(sut.callRootSpan)
        verify(fixture.scopes).captureEvent(
            argThat {
                throwable is SentryHttpClientException &&
                    throwable!!.message!!.startsWith("HTTP Client Error with status code: ")
            },
            argThat<Hint> {
                get(TypeCheckHint.OKHTTP_REQUEST) != null &&
                    get(TypeCheckHint.OKHTTP_RESPONSE) != null
            }
        )
    }

    @Test
    fun `when setClientErrorResponse is not called, no client error is captured`() {
        val sut = fixture.getSut()
        sut.finishEvent()
        verify(fixture.scopes, never()).captureEvent(any(), any<Hint>())
    }

    /** Retrieve all the spans started in the event using reflection. */
    private fun SentryOkHttpEvent.getEventSpans() = getProperty<MutableMap<String, ISpan>>("eventSpans")
}
