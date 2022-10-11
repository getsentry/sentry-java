package io.sentry.transport.apache

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.same
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.ILogger
import io.sentry.RequestDetails
import io.sentry.SentryEnvelope
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.SentryOptionsManipulator
import io.sentry.clientreport.DiscardReason
import io.sentry.clientreport.IClientReportRecorder
import io.sentry.hints.Retryable
import io.sentry.transport.RateLimiter
import io.sentry.transport.ReusableCountLatch
import io.sentry.util.HintUtils
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient
import org.apache.hc.core5.concurrent.FutureCallback
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test

class ApacheHttpClientTransportClientReportTest {

    class Fixture {
        val options: SentryOptions
        val logger = mock<ILogger>()
        val rateLimiter = mock<RateLimiter>()
        val clientReportRecorder = mock<IClientReportRecorder>()
        val requestDetails = RequestDetails("http://key@localhost/proj", mapOf("header-name" to "header-value"))
        val client = mock<CloseableHttpAsyncClient>()
        val currentlyRunning = spy<ReusableCountLatch>()
        val executorService = Executors.newFixedThreadPool(2)
        val envelopeBeforeClientReportAttached: SentryEnvelope
        val envelopeAfterClientReportAttached: SentryEnvelope

        init {
            whenever(rateLimiter.filter(any(), anyOrNull())).thenAnswer { it.arguments[0] }
            options = SentryOptions()
            options.setSerializer(mock())
            options.setDiagnosticLevel(SentryLevel.WARNING)
            options.setDebug(true)
            options.setLogger(logger)
            SentryOptionsManipulator.setClientReportRecorder(options, clientReportRecorder)

            envelopeBeforeClientReportAttached = SentryEnvelope.from(options.serializer, SentryEvent(), null)
            envelopeAfterClientReportAttached = SentryEnvelope.from(options.serializer, SentryEvent(), null)
            whenever(clientReportRecorder.attachReportToEnvelope(same(envelopeBeforeClientReportAttached)))
                .thenReturn(envelopeAfterClientReportAttached)
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

    @AfterTest
    fun `shutdown executor`() {
        fixture.executorService.shutdownNow()
    }

    @Test
    fun `attaches client report to envelope`() {
        val sut = fixture.getSut(SimpleHttpResponse(200))

        sut.send(fixture.envelopeBeforeClientReportAttached)

        verify(fixture.clientReportRecorder, times(1)).attachReportToEnvelope(same(fixture.envelopeBeforeClientReportAttached))
        verify(fixture.clientReportRecorder, never()).recordLostEnvelope(any(), any())
        verifyNoMoreInteractions(fixture.clientReportRecorder)
    }

    @Test
    fun `records lost envelope when queue is full for non retryable`() {
        val sut = fixture.getSut(queueFull = true)

        sut.send(fixture.envelopeBeforeClientReportAttached)

        verify(fixture.clientReportRecorder, never()).attachReportToEnvelope(any())
        verify(fixture.clientReportRecorder, times(1)).recordLostEnvelope(eq(DiscardReason.QUEUE_OVERFLOW), eq(fixture.envelopeBeforeClientReportAttached))
        verifyNoMoreInteractions(fixture.clientReportRecorder)
    }

    @Test
    fun `records lost envelope when queue is full for retryable`() {
        val sut = fixture.getSut(queueFull = true)

        sut.send(fixture.envelopeBeforeClientReportAttached, retryableHint())

        verify(fixture.clientReportRecorder, never()).attachReportToEnvelope(any())
        verify(fixture.clientReportRecorder, times(1)).recordLostEnvelope(eq(DiscardReason.QUEUE_OVERFLOW), same(fixture.envelopeBeforeClientReportAttached))
        verifyNoMoreInteractions(fixture.clientReportRecorder)
    }

    @Test
    fun `records lost envelope on 500 error for non retryable`() {
        val sut = fixture.getSut(SimpleHttpResponse(500))

        sut.send(fixture.envelopeBeforeClientReportAttached)

        verify(fixture.clientReportRecorder, times(1)).attachReportToEnvelope(same(fixture.envelopeBeforeClientReportAttached))
        verify(fixture.clientReportRecorder, times(1)).recordLostEnvelope(eq(DiscardReason.NETWORK_ERROR), same(fixture.envelopeAfterClientReportAttached))
        verifyNoMoreInteractions(fixture.clientReportRecorder)
    }

    @Test
    fun `does not record lost envelope on 500 error for retryable`() {
        val sut = fixture.getSut(SimpleHttpResponse(500))

        sut.send(fixture.envelopeBeforeClientReportAttached, retryableHint())

        verify(fixture.clientReportRecorder, times(1)).attachReportToEnvelope(same(fixture.envelopeBeforeClientReportAttached))
        verify(fixture.clientReportRecorder, never()).recordLostEnvelope(any(), any())
        verifyNoMoreInteractions(fixture.clientReportRecorder)
    }

    @Test
    fun `records lost envelope on 400 error for non retryable`() {
        val sut = fixture.getSut(SimpleHttpResponse(400))

        sut.send(fixture.envelopeBeforeClientReportAttached)

        verify(fixture.clientReportRecorder, times(1)).attachReportToEnvelope(same(fixture.envelopeBeforeClientReportAttached))
        verify(fixture.clientReportRecorder, times(1)).recordLostEnvelope(eq(DiscardReason.NETWORK_ERROR), same(fixture.envelopeAfterClientReportAttached))
        verifyNoMoreInteractions(fixture.clientReportRecorder)
    }

    @Test
    fun `does not record lost envelope on 400 error for retryable`() {
        val sut = fixture.getSut(SimpleHttpResponse(400))

        sut.send(fixture.envelopeBeforeClientReportAttached, retryableHint())

        verify(fixture.clientReportRecorder, times(1)).attachReportToEnvelope(same(fixture.envelopeBeforeClientReportAttached))
        verify(fixture.clientReportRecorder, never()).recordLostEnvelope(any(), any())
        verifyNoMoreInteractions(fixture.clientReportRecorder)
    }

    @Test
    fun `does not record lost envelope on 429 error for non retryable`() {
        val sut = fixture.getSut(SimpleHttpResponse(429))

        sut.send(fixture.envelopeBeforeClientReportAttached)

        verify(fixture.clientReportRecorder, times(1)).attachReportToEnvelope(same(fixture.envelopeBeforeClientReportAttached))
        verify(fixture.clientReportRecorder, never()).recordLostEnvelope(any(), any())
        verifyNoMoreInteractions(fixture.clientReportRecorder)
    }

    @Test
    fun `does not record lost envelope on 429 error for retryable`() {
        val sut = fixture.getSut(SimpleHttpResponse(429))

        sut.send(fixture.envelopeBeforeClientReportAttached, retryableHint())

        verify(fixture.clientReportRecorder, times(1)).attachReportToEnvelope(same(fixture.envelopeBeforeClientReportAttached))
        verify(fixture.clientReportRecorder, never()).recordLostEnvelope(any(), any())
        verifyNoMoreInteractions(fixture.clientReportRecorder)
    }

    @Test
    fun `does not record lost envelope on io exception for retryable`() {
        val sut = fixture.getSut()
        whenever(fixture.client.execute(any(), any())).thenThrow(RuntimeException("thrown on purpose"))

        sut.send(fixture.envelopeBeforeClientReportAttached, retryableHint())

        verify(fixture.clientReportRecorder, times(1)).attachReportToEnvelope(same(fixture.envelopeBeforeClientReportAttached))
        verify(fixture.clientReportRecorder, never()).recordLostEnvelope(any(), any())
        verifyNoMoreInteractions(fixture.clientReportRecorder)
    }

    @Test
    fun `records lost envelope on io exception for non retryable`() {
        val sut = fixture.getSut()
        whenever(fixture.client.execute(any(), any())).thenThrow(RuntimeException("thrown on purpose"))

        sut.send(fixture.envelopeBeforeClientReportAttached)

        verify(fixture.clientReportRecorder, times(1)).attachReportToEnvelope(same(fixture.envelopeBeforeClientReportAttached))
        verify(fixture.clientReportRecorder, times(1)).recordLostEnvelope(eq(DiscardReason.NETWORK_ERROR), same(fixture.envelopeAfterClientReportAttached))
        verifyNoMoreInteractions(fixture.clientReportRecorder)
    }

    private fun retryableHint() = HintUtils.createWithTypeCheckHint(TestRetryable())
}

class TestRetryable : Retryable {
    private var retry = false

    override fun setRetry(retry: Boolean) {
        this.retry = retry
    }

    override fun isRetry(): Boolean {
        return this.retry
    }
}
