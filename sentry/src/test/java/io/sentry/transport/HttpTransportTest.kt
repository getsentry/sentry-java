package io.sentry.transport

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.ISerializer
import io.sentry.SentryEnvelope
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.Session
import io.sentry.protocol.User
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URI
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpTransportTest {

    private class Fixture {
        val dsn: URL = URI.create("http://key@localhost/proj").toURL()
        val serializer = mock<ISerializer>()
        var proxy: Proxy? = null
        var requestUpdater = IConnectionConfigurator {}
        var connectionTimeout = 1000
        var readTimeout = 500
        var bypassSecurity = false
        val connection = mock<HttpURLConnection>()
        val currentDateProvider = mock<ICurrentDateProvider>()

        init {
            whenever(connection.outputStream).thenReturn(mock())
            whenever(connection.inputStream).thenReturn(mock())
        }

        fun getSUT(): HttpTransport {
            val options = SentryOptions()
            options.setSerializer(serializer)
            options.proxy = proxy

            return object : HttpTransport(options, requestUpdater, connectionTimeout, readTimeout, bypassSecurity, dsn, currentDateProvider) {
                override fun open(): HttpURLConnection {
                    return connection
                }
            }
        }
    }

    private val fixture = Fixture()

    @Test
    fun `test serializes envelope`() {
        val transport = fixture.getSUT()
        whenever(fixture.connection.responseCode).thenReturn(200)

        val envelope = SentryEnvelope.fromSession(fixture.serializer, createSession(), null)

        val result = transport.send(envelope)

        verify(fixture.serializer).serialize(eq(envelope), any())
        assertTrue(result.isSuccess)
    }

    @Test
    fun `uses Retry-After header if X-Sentry-Rate-Limit is not set when sending an envelope`() {
        val transport = fixture.getSUT()

        throwOnEnvelopeSerialize()
        whenever(fixture.connection.getHeaderField(eq("Retry-After"))).thenReturn("30")
        whenever(fixture.connection.responseCode).thenReturn(429)
        whenever(fixture.currentDateProvider.currentTimeMillis).thenReturn(0)

        val envelope = SentryEnvelope.fromSession(fixture.serializer, createSession(), null)

        val result = transport.send(envelope)

        verify(fixture.serializer).serialize(eq(envelope), any())
        assertFalse(result.isSuccess)
        assertTrue(transport.isRetryAfter("session"))
    }

    @Test
    fun `passes on the response code on error when sending an envelope`() {
        val transport = fixture.getSUT()

        throwOnEnvelopeSerialize()
        whenever(fixture.connection.responseCode).thenReturn(1234)

        val envelope = SentryEnvelope.fromSession(fixture.serializer, createSession(), null)

        val result = transport.send(envelope)

        verify(fixture.serializer).serialize(eq(envelope), any())
        assertFalse(result.isSuccess)
        assertEquals(1234, result.responseCode)
    }

    @Test
    fun `uses the default retry interval if there is no Retry-After header when sending an envelope`() {
        val transport = fixture.getSUT()

        throwOnEnvelopeSerialize()
        whenever(fixture.connection.responseCode).thenReturn(429)
        whenever(fixture.currentDateProvider.currentTimeMillis).thenReturn(0)

        val envelope = SentryEnvelope.fromSession(fixture.serializer, createSession(), null)

        val result = transport.send(envelope)

        verify(fixture.serializer).serialize(eq(envelope), any())
        assertFalse(result.isSuccess)
        assertTrue(transport.isRetryAfter("session"))
    }

    @Test
    fun `failure to get response code doesn't break sending an envelope`() {
        val transport = fixture.getSUT()

        throwOnEnvelopeSerialize()
        whenever(fixture.connection.responseCode).thenThrow(IOException())

        val session = Session("123", User(), "env", "release")
        val envelope = SentryEnvelope.fromSession(fixture.serializer, session, null)

        val result = transport.send(envelope)

        verify(fixture.serializer).serialize(eq(envelope), any())
        assertFalse(result.isSuccess)
        assertEquals(-1, result.responseCode)
    }

    @Test
    fun `uses X-Sentry-Rate-Limit and returns accordingly`() {
        val transport = fixture.getSUT()

        throwOnEventSerialize()
        whenever(fixture.connection.getHeaderField(eq("X-Sentry-Rate-Limits")))
            .thenReturn("50:transaction:key, 2700:default;error;security:organization")
        whenever(fixture.currentDateProvider.currentTimeMillis).thenReturn(0)

        val envelope = createEnvelope()
        val result = transport.send(envelope)

        verify(fixture.serializer).serialize(eq(envelope), any())
        assertFalse(result.isSuccess)
        assertTrue(transport.isRetryAfter("event"))
    }

    @Test
    fun `uses X-Sentry-Rate-Limit and allows sending if time has passed`() {
        val transport = fixture.getSUT()

        throwOnEventSerialize()
        whenever(fixture.connection.getHeaderField(eq("X-Sentry-Rate-Limits")))
            .thenReturn("50:transaction:key, 1:default;error;security:organization")
        whenever(fixture.currentDateProvider.currentTimeMillis).thenReturn(0, 0, 1001)

        val envelope = createEnvelope()
        val result = transport.send(envelope)

        verify(fixture.serializer).serialize(eq(envelope), any())
        assertFalse(result.isSuccess)
        assertFalse(transport.isRetryAfter("event"))
    }

    @Test
    fun `parse X-Sentry-Rate-Limit and set its values and retry after should be true`() {
        val transport = fixture.getSUT()

        whenever(fixture.connection.inputStream).thenThrow(IOException())
        whenever(fixture.connection.getHeaderField(eq("X-Sentry-Rate-Limits")))
            .thenReturn("50:transaction:key, 2700:default;error;security:organization")
        whenever(fixture.currentDateProvider.currentTimeMillis).thenReturn(0)

        transport.send(createEnvelope())

        assertTrue(transport.isRetryAfter("transaction"))
        assertTrue(transport.isRetryAfter("event"))
    }

    @Test
    fun `parse X-Sentry-Rate-Limit and set its values and retry after should be false`() {
        val transport = fixture.getSUT()

        whenever(fixture.connection.inputStream).thenThrow(IOException())
        whenever(fixture.connection.getHeaderField(eq("X-Sentry-Rate-Limits")))
            .thenReturn("1:transaction:key, 1:default;error;security:organization")
        whenever(fixture.currentDateProvider.currentTimeMillis).thenReturn(0, 0, 1001)

        transport.send(createEnvelope())

        assertFalse(transport.isRetryAfter("transaction"))
        assertFalse(transport.isRetryAfter("event"))
    }

    @Test
    fun `When X-Sentry-Rate-Limit categories are empty, applies to all the categories`() {
        val transport = fixture.getSUT()

        whenever(fixture.connection.inputStream).thenThrow(IOException())
        whenever(fixture.connection.getHeaderField(eq("X-Sentry-Rate-Limits")))
            .thenReturn("50::key")
        whenever(fixture.currentDateProvider.currentTimeMillis).thenReturn(0)

        transport.send(createEnvelope())

        assertTrue(transport.isRetryAfter("event"))
    }

    @Test
    fun `parse X-Sentry-Rate-Limit and ignore unknown categories`() {
        val transport = fixture.getSUT()

        whenever(fixture.connection.inputStream).thenThrow(IOException())
        whenever(fixture.connection.getHeaderField(eq("X-Sentry-Rate-Limits")))
            .thenReturn("60:default;foobar;error;security:organization")
        whenever(fixture.currentDateProvider.currentTimeMillis).thenReturn(0)

        transport.send(createEnvelope())
        assertFalse(transport.isRetryAfter("foobar"))
    }

    @Test
    fun `When all categories is set but expired, applies only for specific category`() {
        val transport = fixture.getSUT()

        whenever(fixture.connection.inputStream).thenThrow(IOException())
        whenever(fixture.connection.getHeaderField(eq("X-Sentry-Rate-Limits")))
            .thenReturn("1::key, 60:default;error;security:organization")
        whenever(fixture.currentDateProvider.currentTimeMillis).thenReturn(0, 0, 1001)

        transport.send(createEnvelope())
        assertTrue(transport.isRetryAfter("event"))
    }

    @Test
    fun `When category has shorter rate limiting, do not apply new timestamp`() {
        val transport = fixture.getSUT()

        whenever(fixture.connection.inputStream).thenThrow(IOException())
        whenever(fixture.connection.getHeaderField(eq("X-Sentry-Rate-Limits")))
            .thenReturn("60:error:key, 1:error:organization")
        whenever(fixture.currentDateProvider.currentTimeMillis).thenReturn(0, 0, 1001)

        transport.send(createEnvelope())
        assertTrue(transport.isRetryAfter("event"))
    }

    @Test
    fun `When category has longer rate limiting, apply new timestamp`() {
        val transport = fixture.getSUT()

        whenever(fixture.connection.inputStream).thenThrow(IOException())
        whenever(fixture.connection.getHeaderField(eq("X-Sentry-Rate-Limits")))
            .thenReturn("1:error:key, 5:error:organization")
        whenever(fixture.currentDateProvider.currentTimeMillis).thenReturn(0, 0, 1001)

        transport.send(createEnvelope())
        assertTrue(transport.isRetryAfter("event"))
    }

    private fun createSession(): Session {
        return Session("123", User(), "env", "release")
    }

    // TODO: make inline fun <reified T : Any>, so we can throwOnSerialize<SentryEvent>()
    private fun throwOnEventSerialize() {
        whenever(fixture.serializer.serialize(any<SentryEvent>(), any())).thenThrow(IOException())
    }

    private fun throwOnEnvelopeSerialize() {
        whenever(fixture.serializer.serialize(any<SentryEnvelope>(), any())).thenThrow(IOException())
    }

    private fun createEnvelope(event: SentryEvent = SentryEvent()): SentryEnvelope {
        return SentryEnvelope.fromEvent(fixture.serializer, event, null)
    }
}
