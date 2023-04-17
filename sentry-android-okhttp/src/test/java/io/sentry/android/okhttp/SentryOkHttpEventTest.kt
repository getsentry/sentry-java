package io.sentry.android.okhttp

import io.sentry.Breadcrumb
import io.sentry.IHub
import io.sentry.ISpan
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.Span
import io.sentry.SpanOptions
import io.sentry.TracesSamplingDecision
import io.sentry.TransactionContext
import io.sentry.TypeCheckHint
import io.sentry.test.getProperty
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockWebServer
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentryOkHttpEventTest {
    private class Fixture {
        val hub = mock<IHub>()
        val server = MockWebServer()
        val span: ISpan
        val request: Request
        val response: Response

        init {
            whenever(hub.options).thenReturn(
                SentryOptions().apply {
                    dsn = "https://key@sentry.io/proj"
                }
            )

            span = Span(
                TransactionContext("name", "op", TracesSamplingDecision(true)),
                SentryTracer(TransactionContext("name", "op", TracesSamplingDecision(true)), hub),
                hub,
                null,
                SpanOptions()
            )

            request = Request.Builder()
                .addHeader("myHeader", "myValue")
                .get()
                .url(server.url("/hello"))
                .build()

            response = Response.Builder()
                .code(200)
                .message("message")
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .build()
        }

        fun getSut(currentSpan: ISpan? = span, requestUrl: String ? = null): SentryOkHttpEvent {
            whenever(hub.span).thenReturn(currentSpan)
            val okhttpClient = OkHttpClient.Builder().build()
            val call = if (requestUrl == null) {
                okhttpClient.newCall(request)
            } else {
                okhttpClient.newCall(
                    Request.Builder()
                        .addHeader("myHeader", "myValue")
                        .get()
                        .url(server.url(requestUrl))
                        .build()
                )
            }
            return SentryOkHttpEvent(hub, call)
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
        assertEquals("${fixture.request.method} ${fixture.request.url}", callSpan.description)
        assertEquals(fixture.request.url.toString(), callSpan.getData("url"))
        assertEquals(fixture.request.url.toString(), callSpan.getData("filtered_url"))
        assertEquals(fixture.request.url.host, callSpan.getData("host"))
        assertEquals(fixture.request.url.encodedPath, callSpan.getData("path"))
        assertEquals(fixture.request.method, callSpan.getData("method"))
        assertTrue(callSpan.getData("success") as Boolean)
    }

    @Test
    fun `filtered_url data in root span does not contain uids or parameters`() {
        val sut = fixture.getSut(requestUrl = "/hello/${UUID.randomUUID()}/?param1=1&param2=2")
        val callSpan = sut.callRootSpan
        val filteredUrl = callSpan?.getData("filtered_url")
        assertNotNull(filteredUrl)
        assertEquals(fixture.server.url("/hello/*/").toString(), filteredUrl)
    }

    @Test
    fun `when root span is null, no breadcrumb is created`() {
        val sut = fixture.getSut(currentSpan = null)
        assertNull(sut.callRootSpan)
        sut.finishEvent()
        verify(fixture.hub, never()).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
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
        assertTrue(spans.containsKey("span"))
        assertFalse(spans["span"]!!.isFinished)
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
                assertEquals("span", it.description)
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
        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals(fixture.request.url.toString(), it.data["url"])
                assertEquals(fixture.request.url.toString(), it.data["filtered_url"])
                assertEquals(fixture.request.url.host, it.data["host"])
                assertEquals(fixture.request.url.encodedPath, it.data["path"])
                assertEquals(fixture.request.method, it.data["method"])
                assertTrue(it.data["success"] as Boolean)
            },
            check {
                assertEquals(fixture.request, it[TypeCheckHint.OKHTTP_REQUEST])
            }
        )
    }

    @Test
    fun `setResponse set protocol and code in the breadcrumb and root span, and response in the hint`() {
        val sut = fixture.getSut()
        sut.setResponse(fixture.response)

        assertEquals(fixture.response.protocol.name, sut.callRootSpan?.getData("protocol"))
        assertEquals(fixture.response.code, sut.callRootSpan?.getData("status_code"))
        sut.finishEvent()

        verify(fixture.hub).addBreadcrumb(
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
        verify(fixture.hub).addBreadcrumb(
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
        verify(fixture.hub).addBreadcrumb(
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
        assertEquals(10L, sut.callRootSpan?.getData("request_body_size"))
        sut.finishEvent()
        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals(10L, it.data["request_body_size"])
            },
            any()
        )
    }

    @Test
    fun `setRequestBodySize is ignored if RequestBodySize is negative`() {
        val sut = fixture.getSut()
        sut.setRequestBodySize(-1)
        assertNull(sut.callRootSpan?.getData("request_body_size"))
        sut.finishEvent()
        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertNull(it.data["request_body_size"])
            },
            any()
        )
    }

    @Test
    fun `setResponseBodySize set ResponseBodySize in the breadcrumb and in the root span`() {
        val sut = fixture.getSut()
        sut.setResponseBodySize(10)
        assertEquals(10L, sut.callRootSpan?.getData("response_body_size"))
        sut.finishEvent()
        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals(10L, it.data["response_body_size"])
            },
            any()
        )
    }

    @Test
    fun `setResponseBodySize is ignored if ResponseBodySize is negative`() {
        val sut = fixture.getSut()
        sut.setResponseBodySize(-1)
        assertNull(sut.callRootSpan?.getData("response_body_size"))
        sut.finishEvent()
        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertNull(it.data["response_body_size"])
            },
            any()
        )
    }

    @Test
    fun `setError set success to false and errorMessage in the breadcrumb and in the root span`() {
        val sut = fixture.getSut()
        sut.setError("errorMessage")
        assertFalse(sut.callRootSpan?.getData("success") as Boolean)
        assertEquals("errorMessage", sut.callRootSpan.getData("error_message"))
        sut.finishEvent()
        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertFalse(it.data["success"] as Boolean)
                assertEquals("errorMessage", it.data["error_message"])
            },
            any()
        )
    }

    @Test
    fun `setError sets success to false in the breadcrumb and in the root span even if errorMessage is null`() {
        val sut = fixture.getSut()
        sut.setError(null)
        assertFalse(sut.callRootSpan?.getData("success") as Boolean)
        assertNull(sut.callRootSpan.getData("error_message"))
        sut.finishEvent()
        verify(fixture.hub).addBreadcrumb(
            check<Breadcrumb> {
                assertFalse(it.data["success"] as Boolean)
                assertNull(it.data["error_message"])
            },
            any()
        )
    }

    /** Retrieve all the spans started in the event using reflection. */
    private fun SentryOkHttpEvent.getEventSpans() = getProperty<MutableMap<String, ISpan>>("eventSpans")
}
