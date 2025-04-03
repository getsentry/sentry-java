package io.sentry.transport

import io.sentry.Hint
import io.sentry.SentryEnvelope
import io.sentry.SentryOptions
import io.sentry.Session
import io.sentry.dsnString
import io.sentry.protocol.User
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultiplexedTransportTest {
    private class Fixture {
        var transport1 = mock<ITransport>()
        var transport2 = mock<ITransport>()
        var rateLimiter1 = mock<RateLimiter>()
        var rateLimiter2 = mock<RateLimiter>()
        var sentryOptions: SentryOptions = SentryOptions().apply {
            dsn = dsnString
            setSerializer(mock())
            setEnvelopeDiskCache(mock())
        }

        init {
            whenever(transport1.rateLimiter).thenReturn(rateLimiter1)
            whenever(transport2.rateLimiter).thenReturn(rateLimiter2)
        }

        fun getSUT(): MultiplexedTransport {
            return MultiplexedTransport(listOf(transport1, transport2))
        }
    }
    private val fixture = Fixture()

    private fun createSession(): Session {
        return Session("123", User(), "env", "release")
    }

    @Test
    fun `send sends to all transports`() {
        val envelope = SentryEnvelope
            .from(fixture.sentryOptions.serializer, createSession(), null)
        val hint = Hint()

        fixture.getSUT().send(envelope, hint)

        verify(fixture.transport1).send(envelope, hint)
        verify(fixture.transport2).send(envelope, hint)
    }

    @Test
    fun `healthy if all transports are healthy`() {
        whenever(fixture.transport1.isHealthy).thenReturn(true)
        whenever(fixture.transport2.isHealthy).thenReturn(true)

        assertTrue(fixture.getSUT().isHealthy)
    }

    @Test
    fun `not healthy if one transport is unhealthy`() {
        whenever(fixture.transport1.isHealthy).thenReturn(true)
        whenever(fixture.transport2.isHealthy).thenReturn(false)

        assertFalse(fixture.getSUT().isHealthy)
    }

    @Test
    fun `close closes all transports`() {
        fixture.getSUT().close()

        verify(fixture.transport1).close()
        verify(fixture.transport2).close()
    }

    @Test
    fun `close with isRestarting closes all transports with isRestarting`() {
        fixture.getSUT().close(true)

        verify(fixture.transport1).close(true)
        verify(fixture.transport2).close(true)
    }

    @Test
    fun `getRateLimiter returns active rate limiter if exists`() {
        whenever(fixture.rateLimiter1.isAnyRateLimitActive).thenReturn(false)
        whenever(fixture.rateLimiter2.isAnyRateLimitActive).thenReturn(true)

        assertEquals(fixture.rateLimiter2, fixture.getSUT().rateLimiter)
    }
}
