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
import io.sentry.Session
import io.sentry.cache.IEnvelopeCache
import io.sentry.dsnString
import io.sentry.protocol.User
import java.io.IOException
import java.util.concurrent.ExecutorService
import kotlin.test.Test
import kotlin.test.assertEquals

class AsyncConnectionTest {

    private class Fixture {
        var transport = mock<ITransport>()
        var transportGate = mock<ITransportGate>()
        var envelopeCache = mock<IEnvelopeCache>()
        var executor = mock<ExecutorService>()
        var rateLimiter = mock<RateLimiter>()
        var sentryOptions: SentryOptions = SentryOptions().apply {
            dsn = dsnString
            setSerializer(mock())
        }

        init {
            // this is an executor service running immediately in the current thread. Of course this defeats the
            // purpose of the AsyncConnection but enables us to easily test the behavior of the send jobs that
            // AsyncConnection creates and submits to the executor.
            whenever(executor.submit(any())).thenAnswer { (it.arguments[0] as Runnable).run(); null }
        }

        fun getSUT(): AsyncConnection {
            return AsyncConnection(transport, transportGate, envelopeCache, executor, sentryOptions, rateLimiter)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `successful send discards the envelope from cache`() {
        // given
        val envelope = SentryEnvelope.from(fixture.sentryOptions.serializer, createSession(), null)
        whenever(fixture.transportGate.isConnected).thenReturn(true)
        whenever(fixture.rateLimiter.filter(any(), anyOrNull())).thenAnswer { it.arguments[0] }
        whenever(fixture.transport.send(any())).thenReturn(TransportResult.success())

        // when
        fixture.getSUT().send(envelope)

        // then
        val order = inOrder(fixture.transport, fixture.envelopeCache)

        // because storeBeforeSend is enabled by default
        order.verify(fixture.envelopeCache).store(eq(envelope), anyOrNull())

        order.verify(fixture.transport).send(eq(envelope))
        order.verify(fixture.envelopeCache).discard(eq(envelope))
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
        verify(fixture.envelopeCache).store(eq(envelope), anyOrNull())
        verify(fixture.rateLimiter).filter(eq(envelope), anyOrNull())
    }

    @Test
    fun `stores envelope after unsuccessful send`() {
        // given
        val envelope = SentryEnvelope.from(fixture.sentryOptions.serializer, createSession(), null)
        whenever(fixture.transportGate.isConnected).thenReturn(true)
        whenever(fixture.rateLimiter.filter(eq(envelope), anyOrNull())).thenReturn(envelope)
        whenever(fixture.transport.send(any())).thenReturn(TransportResult.error(500))

        // when
        try {
            fixture.getSUT().send(envelope)
        } catch (e: IllegalStateException) {
            // expected - this is how the AsyncConnection signals failure to the executor for it to retry
        }

        // then
        val order = inOrder(fixture.transport, fixture.envelopeCache)

        // because storeBeforeSend is enabled by default
        order.verify(fixture.envelopeCache).store(eq(envelope), anyOrNull())

        order.verify(fixture.transport).send(eq(envelope))
        verify(fixture.envelopeCache, never()).discard(any())
    }

    @Test
    fun `stores envelope after send failure`() {
        // given
        val envelope = SentryEnvelope.from(fixture.sentryOptions.serializer, createSession(), null)
        whenever(fixture.transportGate.isConnected).thenReturn(true)
        whenever(fixture.rateLimiter.filter(eq(envelope), anyOrNull())).thenReturn(envelope)
        whenever(fixture.transport.send(any())).thenThrow(IOException())

        // when
        try {
            fixture.getSUT().send(envelope)
        } catch (e: IllegalStateException) {
            // expected - this is how the AsyncConnection signals failure to the executor for it to retry
        }

        // then
        val order = inOrder(fixture.transport, fixture.envelopeCache)
        order.verify(fixture.transport).send(eq(envelope))
        verify(fixture.envelopeCache, never()).discard(any())
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
        fixture.getSUT().send(envelope, CachedEvent())

        // then
        verify(fixture.envelopeCache).discard(any())
    }

    @Test
    fun `when session is filtered out, do nothing`() {
        // given
        val envelope = SentryEnvelope.from(fixture.sentryOptions.serializer, createSession(), null)
        whenever(fixture.rateLimiter.filter(any(), anyOrNull())).thenReturn(null)

        // when
        fixture.getSUT().send(envelope)

        // then
        verify(fixture.envelopeCache, never()).discard(any())
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
        whenever(fixture.transport.send(any<SentryEnvelope>())).thenReturn(TransportResult.success())
        fixture.getSUT().send(envelope)
        verify(fixture.transport).send(check<SentryEnvelope> {
            assertEquals(1, it.items.count())
        })
    }

    @Test
    fun `when event is filtered out and cached, discard session`() {
        // given
        val ev = SentryEvent()
        whenever(fixture.rateLimiter.filter(any(), anyOrNull())).thenReturn(null)
        val envelope = SentryEnvelope.from(fixture.sentryOptions.serializer, ev, null)

        // when
        fixture.getSUT().send(envelope, CachedEvent())

        // then
        verify(fixture.envelopeCache).discard(any())
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
        verify(fixture.envelopeCache, never()).discard(any())
    }

    private fun createSession(): Session {
        return Session("123", User(), "env", "release")
    }
}
