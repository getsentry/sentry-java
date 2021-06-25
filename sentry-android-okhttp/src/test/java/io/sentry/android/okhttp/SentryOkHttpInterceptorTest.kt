package io.sentry.android.okhttp

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.Breadcrumb
import io.sentry.IHub
import io.sentry.ISpan
import io.sentry.SentryOptions
import io.sentry.SentryTraceHeader
import io.sentry.SentryTracer
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy

class SentryOkHttpInterceptorTest {

    class Fixture {
        val hub = mock<IHub>()
        var interceptor = SentryOkHttpInterceptor(hub)
        val server = MockWebServer()
        val sentryTracer = SentryTracer(TransactionContext("name", "op"), hub)

        init {
            whenever(hub.options).thenReturn(SentryOptions())
        }

        fun getSut(
            isSpanActive: Boolean = true,
            httpStatusCode: Int = 201,
            responseBody: String = "success",
            socketPolicy: SocketPolicy = SocketPolicy.KEEP_OPEN,
            beforeSpan: SentryOkHttpInterceptor.BeforeSpanCallback? = null
        ): OkHttpClient {
            if (isSpanActive) {
                whenever(hub.span).thenReturn(sentryTracer)
            }
            server.enqueue(MockResponse()
                    .setBody(responseBody)
                    .setSocketPolicy(socketPolicy)
                    .setResponseCode(httpStatusCode))
            server.start()
            if (beforeSpan != null) {
                interceptor = SentryOkHttpInterceptor(hub, beforeSpan)
            }
            return OkHttpClient.Builder().addInterceptor(interceptor).build()
        }
    }

    private val fixture = Fixture()

    private val getRequest = { Request.Builder().get().url(fixture.server.url("/hello")).build() }
    private val postRequest = { Request.Builder().post("request-body"
            .toRequestBody("text/plain"
                    .toMediaType())).url(fixture.server.url("/hello")).build() }

    @Test
    fun `when there is an active span, adds sentry trace header to the request`() {
        val sut = fixture.getSut()
        sut.newCall(getRequest()).execute()
        val recorderRequest = fixture.server.takeRequest()
        assertNotNull(recorderRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
    }

    @Test
    fun `when there is no active span, does not add sentry trace header to the request`() {
        val sut = fixture.getSut(isSpanActive = false)
        sut.newCall(getRequest()).execute()
        val recorderRequest = fixture.server.takeRequest()
        assertNull(recorderRequest.headers[SentryTraceHeader.SENTRY_TRACE_HEADER])
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
        assertEquals(SpanStatus.OK, httpClientSpan.status)
        assertTrue(httpClientSpan.isFinished)
    }

    @Test
    fun `maps http status code to SpanStatus`() {
        val sut = fixture.getSut(httpStatusCode = 400)
        sut.newCall(getRequest()).execute()
        val httpClientSpan = fixture.sentryTracer.children.first()
        assertEquals(SpanStatus.INVALID_ARGUMENT, httpClientSpan.status)
    }

    @Test
    fun `does not map unmapped http status code to SpanStatus`() {
        val sut = fixture.getSut(httpStatusCode = 502)
        sut.newCall(getRequest()).execute()
        val httpClientSpan = fixture.sentryTracer.children.first()
        assertNull(httpClientSpan.status)
    }

    @Test
    fun `adds breadcrumb when http calls succeeds`() {
        val sut = fixture.getSut(responseBody = "response body")
        sut.newCall(postRequest()).execute()
        verify(fixture.hub).addBreadcrumb(check<Breadcrumb> {
            assertEquals("http", it.type)
            assertEquals(13L, it.data["response_body_size"])
            assertEquals(12L, it.data["request_body_size"])
        })
    }

    @SuppressWarnings("SwallowedException")
    @Test
    fun `adds breadcrumb when http calls results in exception`() {
        val chain = mock<Interceptor.Chain>()
        whenever(chain.proceed(any())).thenThrow(IOException())
        whenever(chain.request()).thenReturn(getRequest())

        try {
            fixture.interceptor.intercept(chain)
            fail()
        } catch (e: IOException) {
            // ignore me
        }
        verify(fixture.hub).addBreadcrumb(check<Breadcrumb> {
            assertEquals("http", it.type)
        })
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
        assertEquals(SpanStatus.INTERNAL_ERROR, httpClientSpan.status)
        assertTrue(httpClientSpan.throwable is IOException)
    }

    @Test
    fun `customizer modifies span`() {
        val sut = fixture.getSut(beforeSpan = object : SentryOkHttpInterceptor.BeforeSpanCallback {
            override fun execute(span: ISpan, request: Request, response: Response?): ISpan {
                span.description = "overwritten description"
                return span
            }
        })
        val request = getRequest()
        sut.newCall(request).execute()
        assertEquals(1, fixture.sentryTracer.children.size)
        val httpClientSpan = fixture.sentryTracer.children.first()
        assertEquals("overwritten description", httpClientSpan.description)
    }

    @Test
    fun `customizer receives request and response`() {
        var request: Request? = null
        val sut = fixture.getSut(beforeSpan = object : SentryOkHttpInterceptor.BeforeSpanCallback {
            override fun execute(span: ISpan, req: Request, res: Response?): ISpan {
            assertEquals(request!!.url, req.url)
            assertEquals(request!!.method, req.method)
            assertNotNull(res) {
                assertEquals(201, it.code)
            }
            return span
        } })
        request = getRequest()
        sut.newCall(request).execute()
    }

    @Test
    fun `customizer can drop the span`() {
        val sut = fixture.getSut(beforeSpan = object : SentryOkHttpInterceptor.BeforeSpanCallback {
            override fun execute(span: ISpan, request: Request, response: Response?): ISpan? {
            return null
        } })
        sut.newCall(getRequest()).execute()
        val httpClientSpan = fixture.sentryTracer.children.first()
        assertFalse(httpClientSpan.isFinished)
    }
}
