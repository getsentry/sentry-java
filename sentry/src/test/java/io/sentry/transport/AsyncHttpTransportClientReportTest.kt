package io.sentry.transport

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.same
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.SentryEnvelope
import io.sentry.SentryOptions
import io.sentry.SentryOptionsManipulator
import io.sentry.Session
import io.sentry.clientreport.ClientReportTestHelper.Companion.retryableHint
import io.sentry.clientreport.DiscardReason
import io.sentry.clientreport.IClientReportRecorder
import io.sentry.dsnString
import io.sentry.protocol.User
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class AsyncHttpTransportClientReportTest {

    private class Fixture {
        var connection = mock<HttpConnection>()
        var transportGate = mock<ITransportGate>()
        var executor = mock<QueuedThreadPoolExecutor>()
        var rateLimiter = mock<RateLimiter>()
        var sentryOptions: SentryOptions = SentryOptions().apply {
            dsn = dsnString
            setSerializer(mock())
            setEnvelopeDiskCache(mock())
        }
        var clientReportRecorder = mock<IClientReportRecorder>()
        val envelopeBeforeAttachingClientReport = SentryEnvelope.from(sentryOptions.serializer, createSession(), null)
        val envelopeAfterAttachingClientReport = SentryEnvelope.from(sentryOptions.serializer, createSession(), null)

        fun getSUT(): AsyncHttpTransport {
            SentryOptionsManipulator.setClientReportRecorder(sentryOptions, clientReportRecorder)
            return AsyncHttpTransport(executor, sentryOptions, rateLimiter, transportGate, connection)
        }

        private fun createSession(): Session {
            return Session("123", User(), "env", "release")
        }
    }

    private val fixture = Fixture()

    @Test
    fun `attaches client report to envelope`() {
        // given
        givenSetup(TransportResult.success())

        // when
        fixture.getSUT().send(fixture.envelopeBeforeAttachingClientReport)

        // then
        verify(fixture.clientReportRecorder, times(1)).attachReportToEnvelope(same(fixture.envelopeBeforeAttachingClientReport), any())
        verify(fixture.clientReportRecorder, never()).recordLostEnvelope(any(), any(), any())
        verifyNoMoreInteractions(fixture.clientReportRecorder)
    }

    @Test
    fun `does not record lost envelope on 500 error for retryable`() {
        // given
        givenSetup(TransportResult.error(500))

        // when
        assertFailsWith(java.lang.IllegalStateException::class) {
            fixture.getSUT().send(fixture.envelopeBeforeAttachingClientReport, retryableHint())
        }

        // then
        verify(fixture.clientReportRecorder, times(1)).attachReportToEnvelope(same(fixture.envelopeBeforeAttachingClientReport), any())
        verify(fixture.clientReportRecorder, never()).recordLostEnvelope(any(), any(), any())
        verifyNoMoreInteractions(fixture.clientReportRecorder)
    }

    @Test
    fun `records lost envelope on 500 error for non retryable`() {
        // given
        givenSetup(TransportResult.error(500))

        // when
        assertFailsWith(java.lang.IllegalStateException::class) {
            fixture.getSUT().send(fixture.envelopeBeforeAttachingClientReport)
        }

        // then
        verify(fixture.clientReportRecorder, times(1)).attachReportToEnvelope(same(fixture.envelopeBeforeAttachingClientReport), any())
        verify(fixture.clientReportRecorder, times(1)).recordLostEnvelope(eq(DiscardReason.NETWORK_ERROR), same(fixture.envelopeAfterAttachingClientReport), any())
        verifyNoMoreInteractions(fixture.clientReportRecorder)
    }

    @Test
    fun `records lost envelope on full queue for non retryable`() {
        // given
        givenSetup(cancel = true)

        // when
        fixture.getSUT().send(fixture.envelopeBeforeAttachingClientReport)

        // then
        verify(fixture.clientReportRecorder, never()).attachReportToEnvelope(any(), any())
        verify(fixture.clientReportRecorder, times(1)).recordLostEnvelope(eq(DiscardReason.QUEUE_OVERFLOW), same(fixture.envelopeBeforeAttachingClientReport), any())
        verifyNoMoreInteractions(fixture.clientReportRecorder)
    }

    @Test
    fun `records lost envelope on full queue for retryable`() {
        // given
        givenSetup(cancel = true)

        // when
        fixture.getSUT().send(fixture.envelopeBeforeAttachingClientReport, retryableHint())

        // then
        verify(fixture.clientReportRecorder, never()).attachReportToEnvelope(any(), any())
        verify(fixture.clientReportRecorder, times(1)).recordLostEnvelope(eq(DiscardReason.QUEUE_OVERFLOW), same(fixture.envelopeBeforeAttachingClientReport), any())
        verifyNoMoreInteractions(fixture.clientReportRecorder)
    }

    @Test
    fun `records lost envelope on io exception for non retryable`() {
        // given
        givenSetup()
        whenever(fixture.connection.send(any())).thenThrow(IOException("thrown on purpose"))

        // when
        assertFailsWith(java.lang.IllegalStateException::class) {
            fixture.getSUT().send(fixture.envelopeBeforeAttachingClientReport)
        }

        // then
        verify(fixture.clientReportRecorder, times(1)).attachReportToEnvelope(same(fixture.envelopeBeforeAttachingClientReport), any())
        verify(fixture.clientReportRecorder, times(1)).recordLostEnvelope(eq(DiscardReason.NETWORK_ERROR), same(fixture.envelopeAfterAttachingClientReport), any())
        verifyNoMoreInteractions(fixture.clientReportRecorder)
    }

    @Test
    fun `does not record lost envelope on io exception for retryable`() {
        // given
        givenSetup()
        whenever(fixture.connection.send(any())).thenThrow(IOException("thrown on purpose"))

        // when
        assertFailsWith(java.lang.IllegalStateException::class) {
            fixture.getSUT().send(fixture.envelopeBeforeAttachingClientReport, retryableHint())
        }

        // then
        verify(fixture.clientReportRecorder, times(1)).attachReportToEnvelope(same(fixture.envelopeBeforeAttachingClientReport), any())
        verify(fixture.clientReportRecorder, never()).recordLostEnvelope(any(), any(), any())
        verifyNoMoreInteractions(fixture.clientReportRecorder)
    }

    @Test
    fun `does not record lost envelope on 429 error for retryable`() {
        // given
        givenSetup(TransportResult.error(429))

        // when
        assertFailsWith(java.lang.IllegalStateException::class) {
            fixture.getSUT().send(fixture.envelopeBeforeAttachingClientReport, retryableHint())
        }

        // then
        verify(fixture.clientReportRecorder, times(1)).attachReportToEnvelope(same(fixture.envelopeBeforeAttachingClientReport), any())
        verify(fixture.clientReportRecorder, never()).recordLostEnvelope(any(), any(), any())
        verifyNoMoreInteractions(fixture.clientReportRecorder)
    }

    @Test
    fun `does not record lost envelope on 429 error for non retryable`() {
        // given
        givenSetup(TransportResult.error(429))

        // when
        assertFailsWith(java.lang.IllegalStateException::class) {
            fixture.getSUT().send(fixture.envelopeBeforeAttachingClientReport)
        }

        // then
        verify(fixture.clientReportRecorder, times(1)).attachReportToEnvelope(same(fixture.envelopeBeforeAttachingClientReport), any())
        verify(fixture.clientReportRecorder, never()).recordLostEnvelope(any(), any(), any())
        verifyNoMoreInteractions(fixture.clientReportRecorder)
    }

    @Test
    fun `records lost envelope if transport not connected for non retryable`() {
        // given
        givenSetup(transportConnected = false)

        // when
        fixture.getSUT().send(fixture.envelopeBeforeAttachingClientReport)

        // then
        verify(fixture.clientReportRecorder, never()).attachReportToEnvelope(any(), any())
        verify(fixture.clientReportRecorder, times(1)).recordLostEnvelope(eq(DiscardReason.NETWORK_ERROR), same(fixture.envelopeBeforeAttachingClientReport), any())
        verifyNoMoreInteractions(fixture.clientReportRecorder)
    }

    @Test
    fun `does not record lost envelope if transport not connected for retryable`() {
        // given
        givenSetup(transportConnected = false)

        // when
        fixture.getSUT().send(fixture.envelopeBeforeAttachingClientReport, retryableHint())

        // then
        verify(fixture.clientReportRecorder, never()).attachReportToEnvelope(any(), any())
        verify(fixture.clientReportRecorder, never()).recordLostEnvelope(any(), any(), any())
        verifyNoMoreInteractions(fixture.clientReportRecorder)
    }

    private fun givenSetup(result: TransportResult? = null, cancel: Boolean? = null, transportConnected: Boolean? = null) {
        if (cancel == true) {
            whenever(fixture.executor.submit(any())).thenAnswer { QueuedThreadPoolExecutor.CancelledFuture<Any>() }
        } else {
            whenever(fixture.executor.submit(any())).thenAnswer { (it.arguments[0] as Runnable).run(); null }
        }

        whenever(fixture.transportGate.isConnected).thenReturn(transportConnected ?: true)
        whenever(fixture.rateLimiter.filter(any(), anyOrNull())).thenAnswer { it.arguments[0] }

        result?.let {
            whenever(fixture.connection.send(any())).thenReturn(result)
        }

        whenever(
            fixture.clientReportRecorder.attachReportToEnvelope(
                eq(fixture.envelopeBeforeAttachingClientReport),
                any()
            )
        ).thenReturn(fixture.envelopeAfterAttachingClientReport)
    }
}
