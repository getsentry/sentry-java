package io.sentry.android.okhttp

import io.sentry.IHub
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.TransactionContext
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals

@SuppressWarnings("Deprecated")
class SentryOkHttpEventListenerTest {

    class Fixture {
        val hub = mock<IHub>()
        val server = MockWebServer()
        lateinit var sentryTracer: SentryTracer

        @SuppressWarnings("LongParameterList")
        fun getSut(
            eventListener: EventListener? = null
        ): OkHttpClient {
            val options = SentryOptions().apply {
                dsn = "https://key@sentry.io/proj"
            }
            whenever(hub.options).thenReturn(options)

            sentryTracer = SentryTracer(TransactionContext("name", "op"), hub)
            whenever(hub.span).thenReturn(sentryTracer)
            server.enqueue(
                MockResponse()
                    .setBody("responseBody")
                    .setSocketPolicy(SocketPolicy.KEEP_OPEN)
                    .setResponseCode(200)
            )

            val builder = OkHttpClient.Builder().addInterceptor(SentryOkHttpInterceptor(hub))
            val sentryOkHttpEventListener = when {
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

    @Test
    fun `when there are multiple SentryOkHttpEventListeners, they don't duplicate spans`() {
        val sut = fixture.getSut(eventListener = SentryOkHttpEventListener(fixture.hub))
        val call = sut.newCall(getRequest())
        call.execute().close()
        assertEquals(8, fixture.sentryTracer.children.size)
    }
}
