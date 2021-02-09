package io.sentry.transport.apache

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.RequestDetails
import io.sentry.SentryEnvelope
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.transport.RateLimiter
import io.sentry.transport.ReusableCountLatch
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient
import org.apache.hc.core5.concurrent.FutureCallback
import org.apache.hc.core5.io.CloseMode

class ApacheHttpClientTransportTest {

    class Fixture {
        val options = SentryOptions().apply {
            setSerializer(mock())
            setLogger(mock())
        }
        val rateLimiter = mock<RateLimiter>()
        val requestDetails = RequestDetails("http://key@localhost/proj", mapOf("header-name" to "header-value"))
        val client = mock<CloseableHttpAsyncClient>()
        val currentlyRunning = spy<ReusableCountLatch>()

        init {
            whenever(rateLimiter.filter(any(), anyOrNull())).thenAnswer { it.arguments[0] }
        }

        fun getSut(response: SimpleHttpResponse? = null, queueFull: Boolean = false): ApacheHttpClientTransport {
            val transport = ApacheHttpClientTransport(options, requestDetails, client, rateLimiter, currentlyRunning)

            if (response != null) {
                whenever(client.execute(any(), any())).thenAnswer {
                    (it.arguments[1] as FutureCallback<SimpleHttpResponse>).completed(response)
                    CompletableFuture.completedFuture(response)
                }
            }

            if (queueFull) {
                whenever(currentlyRunning.count).thenReturn(options.maxQueueSize)
            }
            return transport
        }
    }

    private val fixture = Fixture()

    @Test
    fun `updates retry on rate limiter`() {
        val response = SimpleHttpResponse(200)
        response.addHeader("Retry-After", "10")
        response.addHeader("X-Sentry-Rate-Limits", "1000")

        val sut = fixture.getSut(response = response)
        sut.send(SentryEnvelope.from(fixture.options.serializer, SentryEvent(), null))

        verify(fixture.rateLimiter).updateRetryAfterLimits("1000", "10", 200)
    }

    @Test
    fun `uses common headers and url`() {
        val sut = fixture.getSut(response = SimpleHttpResponse(200))

        sut.send(SentryEnvelope.from(fixture.options.serializer, SentryEvent(), null))

        verify(fixture.client).execute(check {
            assertEquals("http://localhost/proj", it.uri.toString())
            assertEquals("header-value", it.getFirstHeader("header-name").value)
        }, any())
    }

    @Test
    fun `does not submit when queue is full`() {
        val sut = fixture.getSut(queueFull = true)
        sut.send(SentryEnvelope.from(fixture.options.serializer, SentryEvent(), null))
        verify(fixture.client, never()).execute(any(), any())
    }

    @Test
    fun `close waits for shutdown`() {
        val sut = fixture.getSut()
        sut.close()
        verify(fixture.client).awaitShutdown(any())
    }

    @Test
    fun `close shuts down gracefully`() {
        val sut = fixture.getSut()
        sut.close()
        verify(fixture.client).close(CloseMode.GRACEFUL)
    }

    @Test
    fun `flush waits till all requests are finished`() {
        val sut = fixture.getSut()
        whenever(fixture.client.execute(any(), any())).then {
            CompletableFuture.runAsync {
                Thread.sleep(100)
                (it.arguments[1] as FutureCallback<SimpleHttpResponse>).completed(SimpleHttpResponse(200))
            }
        }
        sut.send(SentryEnvelope.from(fixture.options.serializer, SentryEvent(), null))
        sut.send(SentryEnvelope.from(fixture.options.serializer, SentryEvent(), null))
        sut.send(SentryEnvelope.from(fixture.options.serializer, SentryEvent(), null))

        sut.flush(500)

        verify(fixture.currentlyRunning, times(3)).decrement()
    }
}
