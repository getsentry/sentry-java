package io.sentry.okhttp

import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.SentryDate
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.Span
import io.sentry.SpanDataConvention
import io.sentry.SpanOptions
import io.sentry.TracesSamplingDecision
import io.sentry.TransactionContext
import io.sentry.TypeCheckHint
import io.sentry.exception.SentryHttpClientException
import io.sentry.test.getProperty
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockWebServer
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
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
    fun `when there is no active span, call span is null`() {
        val sut = fixture.getSut(currentSpan = null)
        assertNull(sut.callSpan)
    }

    @Test
    fun `when there is an active span, a call span is created`() {
        val sut = fixture.getSut()
        val callSpan = sut.callSpan
        assertNotNull(callSpan)
        assertEquals("http.client", callSpan.operation)
        assertEquals("${fixture.mockRequest.method} ${fixture.mockRequest.url}", callSpan.description)
        assertEquals(fixture.mockRequest.url.toString(), callSpan.getData("url"))
        assertEquals(fixture.mockRequest.url.host, callSpan.getData("host"))
        assertEquals(fixture.mockRequest.url.encodedPath, callSpan.getData("path"))
        assertEquals(fixture.mockRequest.method, callSpan.getData(SpanDataConvention.HTTP_METHOD_KEY))
    }

    @Test
    fun `when call span is null, breadcrumb is created anyway`() {
        val sut = fixture.getSut(currentSpan = null)
        assertNull(sut.callSpan)
        sut.finish()
        verify(fixture.scopes).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
    }

    @Test
    fun `when call span is null, no event is recorded`() {
        val sut = fixture.getSut(currentSpan = null)
        assertNull(sut.callSpan)
        sut.onEventStart("span")
        assertTrue(sut.getEventDates().isEmpty())
    }

    @Test
    fun `when event is finished, call span is finished`() {
        val sut = fixture.getSut()
        val rootSpan = sut.callSpan
        assertNotNull(rootSpan)
        assertFalse(rootSpan.isFinished)
        sut.finish()
        assertTrue(rootSpan.isFinished)
    }

    @Test
    fun `when onEventStart, a new event is recorded`() {
        val sut = fixture.getSut()
        val callSpan = sut.callSpan
        assertTrue(sut.getEventDates().isEmpty())
        sut.onEventStart("span")
        val dates = sut.getEventDates()
        assertEquals(1, dates.size)
        assertNull(callSpan!!.getData("span"))
    }

    @Test
    fun `when onEventFinish, an event is added to call span`() {
        val sut = fixture.getSut()
        val callSpan = sut.callSpan
        assertTrue(sut.getEventDates().isEmpty())
        sut.onEventStart("span")
        val dates = sut.getEventDates()
        assertEquals(1, dates.size)
        assertNull(callSpan!!.getData("span"))
        sut.onEventFinish("span")
        assertEquals(0, dates.size)
        assertNotNull(callSpan.getData("span"))
    }

    @Test
    fun `when onEventFinish, a callback is called before the event is set`() {
        val sut = fixture.getSut()
        val callSpan = sut.callSpan
        var called = false
        assertTrue(sut.getEventDates().isEmpty())
        sut.onEventStart("span")
        assertNull(callSpan!!.getData("span"))
        sut.onEventFinish("span") {
            called = true
            assertNull(callSpan.getData("span"))
        }
        assertNotNull(callSpan.getData("span"))
        assertTrue(called)
    }

    @Test
    fun `when onEventFinish, a callback is called only once with the call span`() {
        val sut = fixture.getSut()
        var called = 0
        sut.onEventStart("span")
        sut.onEventFinish("span") {
            called++
        }
        assertEquals(1, called)
    }

    @Test
    fun `onEventFinish is ignored if the span was not previously started`() {
        val sut = fixture.getSut()
        var called = false
        assertTrue(sut.getEventDates().isEmpty())
        sut.onEventFinish("span") { called = true }
        assertTrue(sut.getEventDates().isEmpty())
        assertFalse(called)
    }

    @Test
    fun `when finish, a callback is called with the call span before it is finished`() {
        val sut = fixture.getSut()
        var called = false
        sut.finish {
            called = true
            assertEquals(sut.callSpan, it)
            assertFalse(it.isFinished)
        }
        assertTrue(called)
    }

    @Test
    fun `when finish, all event dates are cleared`() {
        val sut = fixture.getSut()
        sut.onEventStart("span")
        val dates = sut.getEventDates()
        assertFalse(dates.isEmpty())
        sut.finish()
        assertTrue(dates.isEmpty())
    }

    @Test
    fun `when finish, a breadcrumb is captured with request in the hint`() {
        val sut = fixture.getSut()
        sut.finish()
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
    fun `when finish multiple times, only one breadcrumb is captured`() {
        val sut = fixture.getSut()
        sut.finish()
        sut.finish()
        verify(fixture.scopes, times(1)).addBreadcrumb(any<Breadcrumb>(), any())
    }

    @Test
    fun `setResponse set protocol and code in the breadcrumb and call span, and response in the hint`() {
        val sut = fixture.getSut()
        sut.setResponse(fixture.response)

        assertEquals(fixture.response.protocol.name, sut.callSpan?.getData("protocol"))
        assertEquals(fixture.response.code, sut.callSpan?.getData(SpanDataConvention.HTTP_STATUS_CODE_KEY))
        sut.finish()

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
    fun `setProtocol set protocol in the breadcrumb and in the call span`() {
        val sut = fixture.getSut()
        sut.setProtocol("protocol")
        assertEquals("protocol", sut.callSpan?.getData("protocol"))
        sut.finish()
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
        assertNull(sut.callSpan?.getData("protocol"))
        sut.finish()
        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertNull(it.data["protocol"])
            },
            any()
        )
    }

    @Test
    fun `setRequestBodySize set RequestBodySize in the breadcrumb and in the call span`() {
        val sut = fixture.getSut()
        sut.setRequestBodySize(10)
        assertEquals(10L, sut.callSpan?.getData("http.request_content_length"))
        sut.finish()
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
        assertNull(sut.callSpan?.getData("http.request_content_length"))
        sut.finish()
        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertNull(it.data["request_content_length"])
            },
            any()
        )
    }

    @Test
    fun `setResponseBodySize set ResponseBodySize in the breadcrumb and in the call span`() {
        val sut = fixture.getSut()
        sut.setResponseBodySize(10)
        assertEquals(10L, sut.callSpan?.getData(SpanDataConvention.HTTP_RESPONSE_CONTENT_LENGTH_KEY))
        sut.finish()
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
        assertNull(sut.callSpan?.getData(SpanDataConvention.HTTP_RESPONSE_CONTENT_LENGTH_KEY))
        sut.finish()
        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertNull(it.data["response_content_length"])
            },
            any()
        )
    }

    @Test
    fun `setError set success to false and errorMessage in the breadcrumb and in the call span`() {
        val sut = fixture.getSut()
        sut.setError("errorMessage")
        assertEquals("errorMessage", sut.callSpan?.getData("error_message"))
        sut.finish()
        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("errorMessage", it.data["error_message"])
            },
            any()
        )
    }

    @Test
    fun `setError sets success to false in the breadcrumb and in the call span even if errorMessage is null`() {
        val sut = fixture.getSut()
        sut.setError(null)
        assertNotNull(sut.callSpan)
        assertNull(sut.callSpan.getData("error_message"))
        sut.finish()
        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertNull(it.data["error_message"])
            },
            any()
        )
    }

    @Test
    fun `setClientErrorResponse will capture the client error on finish`() {
        val sut = fixture.getSut()
        val clientErrorResponse = mock<Response>()
        whenever(clientErrorResponse.request).thenReturn(fixture.mockRequest)
        sut.setClientErrorResponse(clientErrorResponse)
        verify(fixture.scopes, never()).captureEvent(any(), any<Hint>())
        sut.finish()
        assertNotNull(sut.callSpan)
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
    fun `setClientErrorResponse will capture the client error on finish even when no span is running`() {
        val sut = fixture.getSut(currentSpan = null)
        val clientErrorResponse = mock<Response>()
        whenever(clientErrorResponse.request).thenReturn(fixture.mockRequest)
        sut.setClientErrorResponse(clientErrorResponse)
        verify(fixture.scopes, never()).captureEvent(any(), any<Hint>())
        sut.finish()
        assertNull(sut.callSpan)
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
        sut.finish()
        verify(fixture.scopes, never()).captureEvent(any(), any<Hint>())
    }

    /** Retrieve all the spans started in the event using reflection. */
    private fun SentryOkHttpEvent.getEventDates() = getProperty<MutableMap<String, SentryDate>>("eventDates")
}
