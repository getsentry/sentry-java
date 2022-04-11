package io.sentry.transport

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.CachedEvent
import io.sentry.SentryEnvelope
import io.sentry.SentryEnvelopeHeader
import io.sentry.SentryEnvelopeItem
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.SentryOptionsManipulator
import io.sentry.Session
import io.sentry.TypeCheckHint.SENTRY_TYPE_CHECK_HINT
import io.sentry.clientreport.NoOpClientReportRecorder
import io.sentry.dsnString
import io.sentry.protocol.User
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class AsyncHttpTransportTest {

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
        var clientReportRecorder = NoOpClientReportRecorder()

        init {
            // this is an executor service running immediately in the current thread. Of course this defeats the
            // purpose of the AsyncConnection but enables us to easily test the behavior of the send jobs that
            // AsyncConnection creates and submits to the executor.
            whenever(executor.submit(any())).thenAnswer { (it.arguments[0] as Runnable).run(); null }
        }

        fun getSUT(): AsyncHttpTransport {
            SentryOptionsManipulator.setClientReportRecorder(sentryOptions, clientReportRecorder)
            return AsyncHttpTransport(executor, sentryOptions, rateLimiter, transportGate, connection)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `successful send discards the envelope from cache`() {
        // given
        val envelope = SentryEnvelope.from(fixture.sentryOptions.serializer, createSession(), null)
        whenever(fixture.transportGate.isConnected).thenReturn(true)
        whenever(fixture.rateLimiter.filter(any(), anyOrNull())).thenAnswer { it.arguments[0] }
        whenever(fixture.connection.send(any())).thenReturn(TransportResult.success())

        // when
        fixture.getSUT().send(envelope)

        // then
        val order = inOrder(fixture.connection, fixture.sentryOptions.envelopeDiskCache)

        // because storeBeforeSend is enabled by default
        order.verify(fixture.sentryOptions.envelopeDiskCache).store(eq(envelope), anyOrNull())

        order.verify(fixture.connection).send(eq(envelope))
        order.verify(fixture.sentryOptions.envelopeDiskCache).discard(eq(envelope))
    }

    @Test
    fun `stores envelope in cache if sending is not allowed`() {
        // given
        val envelope = SentryEnvelope.from(fixture.sentryOptions.serializer, createSession(), null)
        whenever(fixture.transportGate.isConnected).thenReturn(false)
        whenever(fixture.rateLimiter.filter(eq(envelope), anyOrNull())).thenReturn(envelope)

        // when
        fixture.getSUT().send(envelope)

        // then
        verify(fixture.sentryOptions.envelopeDiskCache).store(eq(envelope), anyOrNull())
        verify(fixture.rateLimiter).filter(eq(envelope), anyOrNull())
    }

    @Test
    fun `stores envelope after unsuccessful send`() {
        // given
        val envelope = SentryEnvelope.from(fixture.sentryOptions.serializer, createSession(), null)
        whenever(fixture.transportGate.isConnected).thenReturn(true)
        whenever(fixture.rateLimiter.filter(eq(envelope), anyOrNull())).thenReturn(envelope)
        whenever(fixture.connection.send(any())).thenReturn(TransportResult.error(500))

        // when
        try {
            fixture.getSUT().send(envelope)
        } catch (e: IllegalStateException) {
            // expected - this is how the AsyncConnection signals failure to the executor for it to retry
        }

        // then
        val order = inOrder(fixture.connection, fixture.sentryOptions.envelopeDiskCache)

        // because storeBeforeSend is enabled by default
        order.verify(fixture.sentryOptions.envelopeDiskCache).store(eq(envelope), anyOrNull())

        order.verify(fixture.connection).send(eq(envelope))
        verify(fixture.sentryOptions.envelopeDiskCache, never()).discard(any())
    }

    @Test
    fun `stores envelope after send failure`() {
        // given
        val envelope = SentryEnvelope.from(fixture.sentryOptions.serializer, createSession(), null)
        whenever(fixture.transportGate.isConnected).thenReturn(true)
        whenever(fixture.rateLimiter.filter(eq(envelope), anyOrNull())).thenReturn(envelope)
        whenever(fixture.connection.send(any())).thenThrow(IOException())

        // when
        try {
            fixture.getSUT().send(envelope)
        } catch (e: IllegalStateException) {
            // expected - this is how the AsyncConnection signals failure to the executor for it to retry
        }

        // then
        val order = inOrder(fixture.connection, fixture.sentryOptions.envelopeDiskCache)
        order.verify(fixture.connection).send(eq(envelope))
        verify(fixture.sentryOptions.envelopeDiskCache, never()).discard(any())
    }

    @Test
    fun `when event is filtered out, do not submit runnable`() {
        // given
        val ev = SentryEvent()
        whenever(fixture.rateLimiter.filter(any(), anyOrNull())).thenReturn(null)
        val envelope = SentryEnvelope.from(fixture.sentryOptions.serializer, ev, null)

        // when
        fixture.getSUT().send(envelope)

        // then
        verify(fixture.executor, never()).submit(any())
    }

    @Test
    fun `when event is not filtered out, submit runnable`() {
        // given
        val ev = SentryEvent()
        whenever(fixture.rateLimiter.filter(any(), anyOrNull())).thenAnswer { it.arguments[0] }
        val envelope = SentryEnvelope.from(fixture.sentryOptions.serializer, ev, null)

        // when
        fixture.getSUT().send(envelope)

        // then
        verify(fixture.executor).submit(any())
    }

    @Test
    fun `when session is filtered out, do not submit runnable`() {
        // given
        val envelope = SentryEnvelope.from(fixture.sentryOptions.serializer, createSession(), null)
        whenever(fixture.rateLimiter.filter(any(), anyOrNull())).thenReturn(null)

        // when
        fixture.getSUT().send(envelope)

        // then
        verify(fixture.executor, never()).submit(any())
    }

    @Test
    fun `when session is filtered out, discard session`() {
        // given
        val envelope = SentryEnvelope.from(fixture.sentryOptions.serializer, createSession(), null)
        whenever(fixture.rateLimiter.filter(any(), anyOrNull())).thenReturn(null)

        // when
        val hintsMap = mutableMapOf<String, Any>(SENTRY_TYPE_CHECK_HINT to CachedEvent())
        fixture.getSUT().send(envelope, hintsMap)

        // then
        verify(fixture.sentryOptions.envelopeDiskCache).discard(any())
    }

    @Test
    fun `when session is filtered out, do nothing`() {
        // given
        val envelope = SentryEnvelope.from(fixture.sentryOptions.serializer, createSession(), null)
        whenever(fixture.rateLimiter.filter(any(), anyOrNull())).thenReturn(null)

        // when
        fixture.getSUT().send(envelope)

        // then
        verify(fixture.sentryOptions.envelopeDiskCache, never()).discard(any())
    }

    @Test
    fun `when session is not filtered out, submit runnable`() {
        // given
        val envelope = SentryEnvelope.from(fixture.sentryOptions.serializer, createSession(), null)
        whenever(fixture.rateLimiter.filter(any(), anyOrNull())).thenAnswer { it.arguments[0] }

        // when
        fixture.getSUT().send(envelope)

        // then
        verify(fixture.executor).submit(any())
    }

    @Test
    fun `When envelopes have retry after items, ignore them and send others`() {
        val sessionItem = SentryEnvelopeItem.fromSession(fixture.sentryOptions.serializer, createSession())
        val eventItem = SentryEnvelopeItem.fromEvent(fixture.sentryOptions.serializer, SentryEvent())
        val envelope = SentryEnvelope(SentryEnvelopeHeader(), arrayListOf(sessionItem, eventItem))

        whenever(fixture.rateLimiter.filter(any(), anyOrNull())).thenAnswer { SentryEnvelope(SentryEnvelopeHeader(), arrayListOf(eventItem)) }
        whenever(fixture.transportGate.isConnected).thenReturn(true)
        whenever(fixture.connection.send(any<SentryEnvelope>())).thenReturn(TransportResult.success())
        fixture.getSUT().send(envelope)
        verify(fixture.connection).send(
            check<SentryEnvelope> {
                assertEquals(1, it.items.count())
            }
        )
    }

    @Test
    fun `when event is filtered out and cached, discard session`() {
        // given
        val ev = SentryEvent()
        whenever(fixture.rateLimiter.filter(any(), anyOrNull())).thenReturn(null)
        val envelope = SentryEnvelope.from(fixture.sentryOptions.serializer, ev, null)

        // when
        val hintsMap = mutableMapOf<String, Any>(SENTRY_TYPE_CHECK_HINT to CachedEvent())
        fixture.getSUT().send(envelope, hintsMap)

        // then
        verify(fixture.sentryOptions.envelopeDiskCache).discard(any())
    }

    @Test
    fun `when event is filtered out but not cached, do nothing`() {
        // given
        val ev = SentryEvent()
        whenever(fixture.rateLimiter.filter(any(), anyOrNull())).thenReturn(null)
        val envelope = SentryEnvelope.from(fixture.sentryOptions.serializer, ev, null)

        // when
        fixture.getSUT().send(envelope)

        // then
        verify(fixture.sentryOptions.envelopeDiskCache, never()).discard(any())
    }

    @Test
    fun `flush waits for executor to finish tasks`() {
        val sut = fixture.getSUT()
        sut.flush(500)
        verify(fixture.executor).waitTillIdle(500)
    }

    private fun createSession(): Session {
        return Session("123", User(), "env", "release")
    }
}
