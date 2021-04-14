package io.sentry.android.okhttp

import com.nhaarman.mockitokotlin2.any
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
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class SentryOkHttpInterceptorTest {

    class Fixture {
        val hub = mock<IHub>()
        val interceptor = SentryOkHttpInterceptor(hub)
        val server = MockWebServer()
        val sentryTracer = SentryTracer(TransactionContext("name", "op"), hub)

        init {
            whenever(hub.options).thenReturn(SentryOptions())
        }

        fun getSut(isSpanActive: Boolean = true, httpStatusCode: Int = 201, responseBody: String = "success"): OkHttpClient {
            if (isSpanActive) {
                whenever(hub.span).thenReturn(sentryTracer)
            }
            server.enqueue(MockResponse().setBody(responseBody).setResponseCode(httpStatusCode))
            server.start()
            return OkHttpClient.Builder().addInterceptor(interceptor).build()
        }
    }

    val fixture = Fixture()

    @Test
    fun `when there is an active span, adds sentry trace header to the request`() {
        val sut = fixture.getSut()
        sut.newCall(Request.Builder().get().url(fixture.server.url("/hello")).build()).execute()
        val recorderRequest = fixture.server.takeRequest()
        assertNotNull(recorderRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
    }

    @Test
    fun `when there is no active span, does not add sentry trace header to the request`() {
        val sut = fixture.getSut(isSpanActive = false)
        sut.newCall(Request.Builder().get().url(fixture.server.url("/hello")).build()).execute()
        val recorderRequest = fixture.server.takeRequest()
        assertNull(recorderRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
    }

    @Test
    fun `does not overwrite response body`() {
        val sut = fixture.getSut()
        val response = sut.newCall(Request.Builder().get().url(fixture.server.url("/hello")).build()).execute()
        assertEquals("success", response.body?.string())
    }

    @Test
    fun `creates a span around the request`() {
        val sut = fixture.getSut()
        val url = fixture.server.url("/hello")
        sut.newCall(Request.Builder().get().url(url).build()).execute()
        assertEquals(1, fixture.sentryTracer.children.size)
        val httpClientSpan = fixture.sentryTracer.children.first()
        assertEquals("http.client", httpClientSpan.operation)
        assertEquals("GET $url", httpClientSpan.description)
        assertEquals(SpanStatus.OK, httpClientSpan.status)
    }

    @Test
    fun `maps http status code to SpanStatus`() {
        val sut = fixture.getSut(httpStatusCode = 400)
        val url = fixture.server.url("/hello")
        sut.newCall(Request.Builder().get().url(url).build()).execute()
        val httpClientSpan = fixture.sentryTracer.children.first()
        assertEquals(SpanStatus.INVALID_ARGUMENT, httpClientSpan.status)
    }

    @Test
    fun `adds breadcrumb when http calls succeeds`() {
        val sut = fixture.getSut(responseBody = "response body")
        sut.newCall(Request.Builder().post("request-body".toRequestBody("text/plain".toMediaType())).url(fixture.server.url("/hello")).build()).execute()
        verify(fixture.hub).addBreadcrumb(check<Breadcrumb> {
            assertEquals("http", it.type)
            assertEquals(13L, it.data["responseBodySize"])
            assertEquals(12L, it.data["requestBodySize"])
        })
    }

    @Test
    fun `adds breadcrumb when http calls results in exception`() {
        val chain = mock<Interceptor.Chain>()
        whenever(chain.proceed(any())).thenThrow(IOException())
        whenever(chain.request()).thenReturn(Request.Builder().get().url(fixture.server.url("/hello")).build())

        try {
            fixture.interceptor.intercept(chain)
            fail()
        } catch (e: IOException) {}

        verify(fixture.hub).addBreadcrumb(check<Breadcrumb> {
            assertEquals("http", it.type)
        })
    }
}
