package io.sentry.core.transport

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.core.SentryEvent
import io.sentry.core.SentryOptions
import io.sentry.core.dsnString
import java.io.IOException
import java.util.concurrent.ExecutorService
import kotlin.test.Test

class AsyncConnectionTest {

    private class Fixture {
        var transport = mock<ITransport>()
        var transportGate = mock<ITransportGate>()
        var eventCache = mock<IEventCache>()
        var executor = mock<ExecutorService>()
        var sentryOptions: SentryOptions = SentryOptions().apply {
            dsn = dsnString
        }

        init {
            // this is an executor service running immediately in the current thread. Of course this defeats the
            // purpose of the AsyncConnection but enables us to easily test the behavior of the send jobs that
            // AsyncConnection creates and submits to the executor.
            whenever(executor.submit(any())).thenAnswer { (it.arguments[0] as Runnable).run(); null }
        }

        fun getSUT(): AsyncConnection {
            return AsyncConnection(transport, transportGate, eventCache, executor, sentryOptions)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `successful send discards the event from cache`() {
        // given
        val ev = mock<SentryEvent>()
        whenever(fixture.transportGate.isSendingAllowed).thenReturn(true)
        whenever(fixture.transport.send(any())).thenReturn(TransportResult.success())

        // when
        fixture.getSUT().send(ev)

        // then
        val order = inOrder(fixture.transport, fixture.eventCache)
        order.verify(fixture.transport).send(eq(ev))
        order.verify(fixture.eventCache).discard(eq(ev))
        verify(fixture.eventCache, never()).store(any())
    }

    @Test
    fun `stores event in cache if sending is not allowed`() {
        // given
        val ev = mock<SentryEvent>()
        whenever(fixture.transportGate.isSendingAllowed).thenReturn(false)

        // when
        fixture.getSUT().send(ev)

        // then
        verify(fixture.eventCache).store(eq(ev))
        verifyZeroInteractions(fixture.transport)
    }

    @Test
    fun `stores event after unsuccessful send`() {
        // given
        val ev = mock<SentryEvent>()
        whenever(fixture.transportGate.isSendingAllowed).thenReturn(true)
        whenever(fixture.transport.send(any())).thenReturn(TransportResult.error(4, 2))

        // when
        try {
            fixture.getSUT().send(ev)
        } catch (e: IllegalStateException) {
            // expected - this is how the AsyncConnection signals failure to the executor for it to retry
        }

        // then
        val order = inOrder(fixture.transport, fixture.eventCache)
        order.verify(fixture.transport).send(eq(ev))
        order.verify(fixture.eventCache).store(eq(ev))
        verify(fixture.eventCache, never()).discard(any())
    }

    @Test
    fun `stores event after send failure`() {
        // given
        val ev = mock<SentryEvent>()
        whenever(fixture.transportGate.isSendingAllowed).thenReturn(true)
        whenever(fixture.transport.send(any())).thenThrow(IOException())

        // when
        try {
            fixture.getSUT().send(ev)
        } catch (e: IllegalStateException) {
            // expected - this is how the AsyncConnection signals failure to the executor for it to retry
        }

        // then
        val order = inOrder(fixture.transport, fixture.eventCache)
        order.verify(fixture.transport).send(eq(ev))
        order.verify(fixture.eventCache).store(eq(ev))
        verify(fixture.eventCache, never()).discard(any())
    }
}
