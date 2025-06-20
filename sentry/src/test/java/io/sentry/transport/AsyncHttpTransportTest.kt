package io.sentry.transport

import io.sentry.CachedEvent
import io.sentry.Hint
import io.sentry.SentryEnvelope
import io.sentry.SentryEnvelopeHeader
import io.sentry.SentryEnvelopeItem
import io.sentry.SentryEvent
import io.sentry.SentryNanotimeDate
import io.sentry.SentryOptions
import io.sentry.SentryOptionsManipulator
import io.sentry.Session
import io.sentry.clientreport.NoOpClientReportRecorder
import io.sentry.dsnString
import io.sentry.hints.DiskFlushNotification
import io.sentry.hints.Enqueable
import io.sentry.protocol.SentryId
import io.sentry.protocol.User
import io.sentry.test.injectForField
import io.sentry.util.HintUtils
import java.io.IOException
import java.util.Date
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AsyncHttpTransportTest {
  private class Fixture {
    var connection = mock<HttpConnection>()
    var transportGate = mock<ITransportGate>()
    var executor = mock<QueuedThreadPoolExecutor>()
    var rateLimiter = mock<RateLimiter>()
    var sentryOptions: SentryOptions =
      SentryOptions().apply {
        dsn = dsnString
        setSerializer(mock())
        setEnvelopeDiskCache(mock())
      }
    var clientReportRecorder = NoOpClientReportRecorder()

    init {
      // this is an executor service running immediately in the current thread. Of course this
      // defeats the
      // purpose of the AsyncConnection but enables us to easily test the behavior of the send jobs
      // that
      // AsyncConnection creates and submits to the executor.
      whenever(executor.submit(any())).thenAnswer {
        (it.arguments[0] as Runnable).run()
        null
      }
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
    val hints = HintUtils.createWithTypeCheckHint(CachedEvent())
    fixture.getSUT().send(envelope, hints)

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
    val sessionItem =
      SentryEnvelopeItem.fromSession(fixture.sentryOptions.serializer, createSession())
    val eventItem = SentryEnvelopeItem.fromEvent(fixture.sentryOptions.serializer, SentryEvent())
    val envelope = SentryEnvelope(SentryEnvelopeHeader(), arrayListOf(sessionItem, eventItem))

    whenever(fixture.rateLimiter.filter(any(), anyOrNull())).thenAnswer {
      SentryEnvelope(SentryEnvelopeHeader(), arrayListOf(eventItem))
    }
    whenever(fixture.transportGate.isConnected).thenReturn(true)
    whenever(fixture.connection.send(any<SentryEnvelope>())).thenReturn(TransportResult.success())
    fixture.getSUT().send(envelope)
    verify(fixture.connection).send(check { assertEquals(1, it.items.count()) })
  }

  @Test
  fun `when event is filtered out and cached, discard session`() {
    // given
    val ev = SentryEvent()
    whenever(fixture.rateLimiter.filter(any(), anyOrNull())).thenReturn(null)
    val envelope = SentryEnvelope.from(fixture.sentryOptions.serializer, ev, null)

    // when
    val hints = HintUtils.createWithTypeCheckHint(CachedEvent())
    fixture.getSUT().send(envelope, hints)

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

  @Test
  fun `sets current date to sent_at in envelope header before send`() {
    // given
    val now = Date(9001)
    fixture.sentryOptions.dateProvider = mock()
    whenever(fixture.sentryOptions.dateProvider.now()).thenReturn(SentryNanotimeDate(now, 0))

    val envelope = SentryEnvelope.from(fixture.sentryOptions.serializer, createSession(), null)
    whenever(fixture.transportGate.isConnected).thenReturn(true)
    whenever(fixture.rateLimiter.filter(any(), anyOrNull())).thenAnswer { it.arguments[0] }
    whenever(fixture.connection.send(any())).thenReturn(TransportResult.success())

    // when
    val sut = fixture.getSUT()
    sut.send(envelope)

    // then
    verify(fixture.connection).send(check { assertEquals(it.header.sentAt, now) })
  }

  @Test
  fun `sets current date to sent_at in envelope header before send when sent with hint`() {
    // given
    val now = Date(9001)
    fixture.sentryOptions.dateProvider = mock()
    whenever(fixture.sentryOptions.dateProvider.now()).thenReturn(SentryNanotimeDate(now, 0))

    val envelope = SentryEnvelope.from(fixture.sentryOptions.serializer, createSession(), null)
    whenever(fixture.transportGate.isConnected).thenReturn(true)
    whenever(fixture.rateLimiter.filter(any(), anyOrNull())).thenAnswer { it.arguments[0] }
    whenever(fixture.connection.send(any())).thenReturn(TransportResult.success())

    // when
    val sut = fixture.getSUT()
    sut.send(envelope, Hint())

    // then
    verify(fixture.connection).send(check { assertEquals(it.header.sentAt, now) })
  }

  @Test
  fun `close closes the rate limiter`() {
    val sut = fixture.getSUT()
    sut.close()

    verify(fixture.rateLimiter).close()
  }

  @Test
  fun `close uses flushTimeoutMillis option to schedule termination`() {
    fixture.sentryOptions.flushTimeoutMillis = 123
    val sut = fixture.getSUT()
    sut.close()

    verify(fixture.executor).awaitTermination(eq(123), eq(TimeUnit.MILLISECONDS))
  }

  @Test
  fun `close with isRestarting false uses flushTimeoutMillis option to schedule termination`() {
    fixture.sentryOptions.flushTimeoutMillis = 123
    val sut = fixture.getSUT()
    sut.close(false)

    verify(fixture.executor).awaitTermination(eq(123), eq(TimeUnit.MILLISECONDS))
  }

  @Test
  fun `close with isRestarting true does not await termination`() {
    fixture.sentryOptions.flushTimeoutMillis = 123
    val sut = fixture.getSUT()
    sut.close(true)

    verify(fixture.executor).awaitTermination(eq(0), eq(TimeUnit.MILLISECONDS))
  }

  @Test
  fun `close shuts down the executor and runs executing runnable through rejectedExecutionHandler`() {
    val rejectedExecutionHandler = mock<RejectedExecutionHandler>()
    val sut = fixture.getSUT()
    val runnable = mock<Runnable>()

    // Emulate a runnable currently being executed
    sut.injectForField("currentRunnable", runnable)
    whenever(fixture.executor.rejectedExecutionHandler).thenReturn(rejectedExecutionHandler)
    sut.close(true)

    verify(fixture.executor).shutdownNow()
    verify(rejectedExecutionHandler).rejectedExecution(eq(runnable), eq(fixture.executor))
  }

  @Test
  fun `when DiskFlushNotification is not flushable, does not flush`() {
    // given
    val ev = SentryEvent()
    val envelope = SentryEnvelope.from(fixture.sentryOptions.serializer, ev, null)
    whenever(fixture.rateLimiter.filter(any(), anyOrNull())).thenAnswer { it.arguments[0] }

    var calledFlush = false
    val sentryHint =
      object : DiskFlushNotification {
        override fun markFlushed() {
          calledFlush = true
        }

        override fun isFlushable(eventId: SentryId?): Boolean = false

        override fun setFlushable(eventId: SentryId) = Unit
      }
    val hint = HintUtils.createWithTypeCheckHint(sentryHint)

    // when
    fixture.getSUT().send(envelope, hint)

    // then
    assertFalse(calledFlush)
  }

  @Test
  fun `when DiskFlushNotification is flushable, marks it as flushed`() {
    // given
    val ev = SentryEvent()
    val envelope = SentryEnvelope.from(fixture.sentryOptions.serializer, ev, null)
    whenever(fixture.rateLimiter.filter(any(), anyOrNull())).thenAnswer { it.arguments[0] }

    var calledFlush = false
    val sentryHint =
      object : DiskFlushNotification {
        override fun markFlushed() {
          calledFlush = true
        }

        override fun isFlushable(eventId: SentryId?): Boolean = envelope.header.eventId == eventId

        override fun setFlushable(eventId: SentryId) = Unit
      }
    val hint = HintUtils.createWithTypeCheckHint(sentryHint)

    // when
    fixture.getSUT().send(envelope, hint)

    // then
    assertTrue(calledFlush)
  }

  @Test
  fun `when event is Enqueable, marks it after sending to the queue`() {
    val envelope = SentryEnvelope.from(fixture.sentryOptions.serializer, createSession(), null)
    whenever(fixture.transportGate.isConnected).thenReturn(true)
    whenever(fixture.rateLimiter.filter(any(), anyOrNull())).thenAnswer { it.arguments[0] }
    whenever(fixture.connection.send(any())).thenReturn(TransportResult.success())

    var called = false
    val hint =
      HintUtils.createWithTypeCheckHint(
        object : Enqueable {
          override fun markEnqueued() {
            called = true
          }
        }
      )
    fixture.getSUT().send(envelope, hint)

    assertTrue(called)
  }

  @Test
  fun `is healthy if not rate limited and not rejected recently`() {
    whenever(fixture.rateLimiter.isAnyRateLimitActive()).thenReturn(false)
    whenever(fixture.executor.didRejectRecently()).thenReturn(false)

    assertTrue(fixture.getSUT().isHealthy)
  }

  @Test
  fun `is unhealthy if rate limited and not rejected recently`() {
    whenever(fixture.rateLimiter.isAnyRateLimitActive()).thenReturn(true)
    whenever(fixture.executor.didRejectRecently()).thenReturn(false)

    assertFalse(fixture.getSUT().isHealthy)
  }

  @Test
  fun `is unhealthy if not rate limited but rejected recently`() {
    whenever(fixture.rateLimiter.isAnyRateLimitActive()).thenReturn(false)
    whenever(fixture.executor.didRejectRecently()).thenReturn(true)

    assertFalse(fixture.getSUT().isHealthy)
  }

  @Test
  fun `is unhealthy if rate limited and rejected recently`() {
    whenever(fixture.rateLimiter.isAnyRateLimitActive()).thenReturn(true)
    whenever(fixture.executor.didRejectRecently()).thenReturn(true)

    assertFalse(fixture.getSUT().isHealthy)
  }

  private fun createSession(): Session = Session("123", User(), "env", "release")
}
