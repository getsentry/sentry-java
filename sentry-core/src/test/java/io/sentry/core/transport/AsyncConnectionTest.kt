package io.sentry.core.transport

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.core.CachedEvent
import io.sentry.core.SentryEnvelope
import io.sentry.core.SentryEnvelopeHeader
import io.sentry.core.SentryEnvelopeItem
import io.sentry.core.SentryEvent
import io.sentry.core.SentryOptions
import io.sentry.core.Session
import io.sentry.core.cache.IEnvelopeCache
import io.sentry.core.cache.IEventCache
import io.sentry.core.dsnString
import io.sentry.core.protocol.User
import java.io.IOException
import java.util.concurrent.ExecutorService
import kotlin.test.Test
import kotlin.test.assertEquals

class AsyncConnectionTest {

    private class Fixture {
        var transport = mock<ITransport>()
        var transportGate = mock<ITransportGate>()
        var eventCache = mock<IEventCache>()
        var sessionCache = mock<IEnvelopeCache>()
        var executor = mock<ExecutorService>()
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
            return AsyncConnection(transport, transportGate, eventCache, sessionCache, executor, sentryOptions)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `successful send discards the event from cache`() {
        // given
        val ev = mock<SentryEvent>()
        whenever(fixture.transportGate.isConnected).thenReturn(true)
        whenever(fixture.transport.send(any<SentryEvent>())).thenReturn(TransportResult.success())

        // when
        fixture.getSUT().send(ev)

        // then
        val order = inOrder(fixture.transport, fixture.eventCache)

        // because storeBeforeSend is enabled by default
        order.verify(fixture.eventCache).store(eq(ev))

        order.verify(fixture.transport).send(eq(ev))
        order.verify(fixture.eventCache).discard(eq(ev))
    }

    @Test
    fun `successful send discards the session from cache`() {
        // given
        val envelope = SentryEnvelope.fromSession(fixture.sentryOptions.serializer, createSession())
        whenever(fixture.transportGate.isConnected).thenReturn(true)
        whenever(fixture.transport.send(any<SentryEnvelope>())).thenReturn(TransportResult.success())

        // when
        fixture.getSUT().send(envelope)

        // then
        val order = inOrder(fixture.transport, fixture.sessionCache)

        // because storeBeforeSend is enabled by default
        order.verify(fixture.sessionCache).store(eq(envelope), anyOrNull())

        order.verify(fixture.transport).send(eq(envelope))
        order.verify(fixture.sessionCache).discard(eq(envelope))
    }

    @Test
    fun `stores event in cache if sending is not allowed`() {
        // given
        val ev = mock<SentryEvent>()
        whenever(fixture.transportGate.isConnected).thenReturn(false)

        // when
        fixture.getSUT().send(ev)

        // then
        verify(fixture.eventCache).store(eq(ev))
        verify(fixture.transport).isRetryAfter(any())
    }

    @Test
    fun `stores session in cache if sending is not allowed`() {
        // given
        val envelope = SentryEnvelope.fromSession(fixture.sentryOptions.serializer, createSession())
        whenever(fixture.transportGate.isConnected).thenReturn(false)

        // when
        fixture.getSUT().send(envelope)

        // then
        verify(fixture.sessionCache).store(eq(envelope), anyOrNull())
        verify(fixture.transport).isRetryAfter(any())
    }

    @Test
    fun `stores event after unsuccessful send`() {
        // given
        val ev = mock<SentryEvent>()
        whenever(fixture.transportGate.isConnected).thenReturn(true)
        whenever(fixture.transport.send(any<SentryEvent>())).thenReturn(TransportResult.error(500))

        // when
        try {
            fixture.getSUT().send(ev)
        } catch (e: IllegalStateException) {
            // expected - this is how the AsyncConnection signals failure to the executor for it to retry
        }

        // then
        val order = inOrder(fixture.transport, fixture.eventCache)

        // because storeBeforeSend is enabled by default
        order.verify(fixture.eventCache).store(eq(ev))

        order.verify(fixture.transport).send(eq(ev))
        verify(fixture.eventCache, never()).discard(any())
    }

    @Test
    fun `stores session after unsuccessful send`() {
        // given
        val envelope = SentryEnvelope.fromSession(fixture.sentryOptions.serializer, createSession())
        whenever(fixture.transportGate.isConnected).thenReturn(true)
        whenever(fixture.transport.send(any<SentryEnvelope>())).thenReturn(TransportResult.error(500))

        // when
        try {
            fixture.getSUT().send(envelope)
        } catch (e: IllegalStateException) {
            // expected - this is how the AsyncConnection signals failure to the executor for it to retry
        }

        // then
        val order = inOrder(fixture.transport, fixture.sessionCache)

        // because storeBeforeSend is enabled by default
        order.verify(fixture.sessionCache).store(eq(envelope), anyOrNull())

        order.verify(fixture.transport).send(eq(envelope))
        verify(fixture.eventCache, never()).discard(any())
    }

    @Test
    fun `stores event after send failure`() {
        // given
        val ev = mock<SentryEvent>()
        whenever(fixture.transportGate.isConnected).thenReturn(true)
        whenever(fixture.transport.send(any<SentryEvent>())).thenThrow(IOException())

        // when
        try {
            fixture.getSUT().send(ev)
        } catch (e: IllegalStateException) {
            // expected - this is how the AsyncConnection signals failure to the executor for it to retry
        }

        // then
        val order = inOrder(fixture.transport, fixture.eventCache)
        order.verify(fixture.transport).send(eq(ev))
        verify(fixture.eventCache, never()).discard(any())
    }

    @Test
    fun `stores session after send failure`() {
        // given
        val envelope = SentryEnvelope.fromSession(fixture.sentryOptions.serializer, createSession())
        whenever(fixture.transportGate.isConnected).thenReturn(true)
        whenever(fixture.transport.send(any<SentryEnvelope>())).thenThrow(IOException())

        // when
        try {
            fixture.getSUT().send(envelope)
        } catch (e: IllegalStateException) {
            // expected - this is how the AsyncConnection signals failure to the executor for it to retry
        }

        // then
        val order = inOrder(fixture.transport, fixture.sessionCache)
        order.verify(fixture.transport).send(eq(envelope))
        verify(fixture.sessionCache, never()).discard(any())
    }

    @Test
    fun `when event is retry after, do not submit runnable`() {
        // given
        val ev = mock<SentryEvent>()
        whenever(fixture.transport.isRetryAfter(any())).thenReturn(true)

        // when
        fixture.getSUT().send(ev)

        // then
        verify(fixture.executor, never()).submit(any())
    }

    @Test
    fun `when event is not retry after, submit runnable`() {
        // given
        val ev = mock<SentryEvent>()
        whenever(fixture.transport.isRetryAfter(any())).thenReturn(false)

        // when
        fixture.getSUT().send(ev)

        // then
        verify(fixture.executor).submit(any())
    }

    @Test
    fun `when session is retry after, do not submit runnable`() {
        // given
        val envelope = SentryEnvelope.fromSession(fixture.sentryOptions.serializer, createSession())
        whenever(fixture.transport.isRetryAfter(any())).thenReturn(true)

        // when
        fixture.getSUT().send(envelope)

        // then
        verify(fixture.executor, never()).submit(any())
    }

    @Test
    fun `when session is retry after and cached, discard session`() {
        // given
        val envelope = SentryEnvelope.fromSession(fixture.sentryOptions.serializer, createSession())
        whenever(fixture.transport.isRetryAfter(any())).thenReturn(true)

        // when
        fixture.getSUT().send(envelope, CachedEvent())

        // then
        verify(fixture.sessionCache).discard(any())
    }

    @Test
    fun `when session is retry after but not cached, do nothing`() {
        // given
        val envelope = SentryEnvelope.fromSession(fixture.sentryOptions.serializer, createSession())
        whenever(fixture.transport.isRetryAfter(any())).thenReturn(true)

        // when
        fixture.getSUT().send(envelope)

        // then
        verify(fixture.sessionCache, never()).discard(any())
    }

    @Test
    fun `when session is not retry after, submit runnable`() {
        // given
        val envelope = SentryEnvelope.fromSession(fixture.sentryOptions.serializer, createSession())
        whenever(fixture.transport.isRetryAfter(any())).thenReturn(false)

        // when
        fixture.getSUT().send(envelope)

        // then
        verify(fixture.executor).submit(any())
    }

    @Test
    fun `When envelopes have retry after items, ignore them and send others`() {
        val sessionItem = SentryEnvelopeItem.fromSession(fixture.sentryOptions.serializer, mock())
        val eventItem = SentryEnvelopeItem.fromEvent(fixture.sentryOptions.serializer, mock())
        val envelope = SentryEnvelope(SentryEnvelopeHeader(), arrayListOf(sessionItem, eventItem))

        whenever(fixture.transport.isRetryAfter(eq("event"))).thenReturn(false)
        whenever(fixture.transport.isRetryAfter(eq("session"))).thenReturn(true)
        whenever(fixture.transportGate.isConnected).thenReturn(true)
        whenever(fixture.transport.send(any<SentryEnvelope>())).thenReturn(TransportResult.success())
        fixture.getSUT().send(envelope)
        verify(fixture.transport).send(check<SentryEnvelope> {
            assertEquals(1, it.items.count())
        })
    }

    @Test
    fun `when event is retry after and cached, discard session`() {
        // given
        val ev = mock<SentryEvent>()
        whenever(fixture.transport.isRetryAfter(any())).thenReturn(true)

        // when
        fixture.getSUT().send(ev, CachedEvent())

        // then
        verify(fixture.eventCache).discard(any())
    }

    @Test
    fun `when event is retry after but not cached, do nothing`() {
        // given
        val ev = mock<SentryEvent>()
        whenever(fixture.transport.isRetryAfter(any())).thenReturn(true)

        // when
        fixture.getSUT().send(ev)

        // then
        verify(fixture.eventCache, never()).discard(any())
    }

    private fun createSession(): Session {
        return Session("123", User(), "env", "release")
    }
}
