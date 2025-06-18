package io.sentry.okhttp

import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.TransactionContext
import io.sentry.TypeCheckHint
import io.sentry.exception.SentryHttpClientException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentryOkHttpUtilsTest {
    class Fixture {
        val scopes = mock<IScopes>()
        val server = MockWebServer()

        fun getSut(
            httpStatusCode: Int = 500,
            responseBody: String = "success",
            socketPolicy: SocketPolicy = SocketPolicy.KEEP_OPEN,
            sendDefaultPii: Boolean = false,
        ): OkHttpClient {
            val options =
                SentryOptions().apply {
                    dsn = "https://key@sentry.io/proj"
                    setTracePropagationTargets(listOf(server.hostName))
                    isSendDefaultPii = sendDefaultPii
                }
            whenever(scopes.options).thenReturn(options)

            val sentryTracer = SentryTracer(TransactionContext("name", "op"), scopes)

            whenever(scopes.span).thenReturn(sentryTracer)

            server.enqueue(
                MockResponse()
                    .setBody(responseBody)
                    .addHeader("myResponseHeader", "myValue")
                    .addHeader("Set-Cookie", "setCookie")
                    .setSocketPolicy(socketPolicy)
                    .setResponseCode(httpStatusCode),
            )
            return OkHttpClient.Builder().build()
        }
    }

    private val fixture = Fixture()

    private fun getRequest(url: String = "/hello"): Request =
        Request
            .Builder()
            .addHeader("myHeader", "myValue")
            .addHeader("Cookie", "cookie")
            .get()
            .url(fixture.server.url(url))
            .build()

    @Test
    fun `captureClientError captures a client error`() {
        val sut = fixture.getSut()
        val request = getRequest()
        val response = sut.newCall(request).execute()

        SentryOkHttpUtils.captureClientError(fixture.scopes, request, response)
        verify(fixture.scopes).captureEvent(
            check {
                val req = it.request
                val resp = it.contexts.response
                assertIs<SentryHttpClientException>(it.throwable)
                assertTrue(it.throwable!!.message!!.startsWith("HTTP Client Error with status code: "))

                assertEquals(req!!.method, request.method)

                assertEquals(resp!!.statusCode, response.code)
            },
            argThat<Hint> {
                get(TypeCheckHint.OKHTTP_REQUEST) != null &&
                    get(TypeCheckHint.OKHTTP_RESPONSE) != null
            },
        )
    }

    @Test
    fun `captureClientError with sendDefaultPii sends headers`() {
        val sut = fixture.getSut(sendDefaultPii = true)
        val request = getRequest()
        val response = sut.newCall(request).execute()

        SentryOkHttpUtils.captureClientError(fixture.scopes, request, response)
        verify(fixture.scopes).captureEvent(
            check {
                val req = it.request
                val resp = it.contexts.response

                assertIs<io.sentry.protocol.Request>(req)
                assertTrue(req.headers!!.isNotEmpty())
                assertNotNull(req.cookies)

                assertIs<io.sentry.protocol.Response>(resp)
                assertTrue(resp.headers!!.isNotEmpty())
                assertNotNull(resp.cookies)
            },
            any<Hint>(),
        )
    }

    @Test
    fun `captureClientError without sendDefaultPii does not send headers`() {
        val sut = fixture.getSut(sendDefaultPii = false)
        val request = getRequest()
        val response = sut.newCall(request).execute()

        SentryOkHttpUtils.captureClientError(fixture.scopes, request, response)
        verify(fixture.scopes).captureEvent(
            check {
                val req = it.request
                val resp = it.contexts.response

                assertIs<io.sentry.protocol.Request>(req)
                assertNull(req.headers)
                assertNull(req.cookies)

                assertIs<io.sentry.protocol.Response>(resp)
                assertNull(resp.headers)
                assertNull(resp.cookies)
            },
            any<Hint>(),
        )
    }
}
