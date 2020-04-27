package io.sentry.core.transport

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.core.ISerializer
import io.sentry.core.SentryEnvelope
import io.sentry.core.SentryEvent
import io.sentry.core.SentryOptions
import io.sentry.core.Session
import io.sentry.core.protocol.User
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

        init {
            whenever(connection.outputStream).thenReturn(mock())
            whenever(connection.inputStream).thenReturn(mock())
        }

        fun getSUT(): HttpTransport {
            val options = SentryOptions()
            options.setSerializer(serializer)
            options.proxy = proxy

            return object : HttpTransport(options, requestUpdater, connectionTimeout, readTimeout, bypassSecurity, dsn) {
                override fun open(proxy: Proxy?): HttpURLConnection {
                    return connection
                }
                override fun open(url: URL, proxy: Proxy?): HttpURLConnection {
                    return connection
                }
            }
        }
    }

    private val fixture = Fixture()

    @Test
    fun `test serializes event`() {
        val transport = fixture.getSUT()

        val event = SentryEvent()

        val result = transport.send(event)

        verify(fixture.serializer).serialize(eq(event), any())
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test serializes envelope`() {
        val transport = fixture.getSUT()

        val envelope = SentryEnvelope.fromSession(fixture.serializer, createSession())

        val result = transport.send(envelope)

        verify(fixture.serializer).serialize(eq(envelope), any())
        assertTrue(result.isSuccess)
    }

    @Test
    fun `uses Retry-After header if X-Sentry-Rate-Limit is not set when sending an event`() {
        val transport = fixture.getSUT()

        whenever(fixture.connection.inputStream).thenThrow(IOException())
        whenever(fixture.connection.getHeaderField(eq("Retry-After"))).thenReturn("30")
        whenever(fixture.connection.responseCode).thenReturn(429)

        val event = SentryEvent()

        val result = transport.send(event)

        verify(fixture.serializer).serialize(eq(event), any())
        assertFalse(result.isSuccess)
        assertTrue(transport.isRetryAfter("event"))
    }

    @Test
    fun `uses Retry-After header if X-Sentry-Rate-Limit is not set when sending an envelope`() {
        val transport = fixture.getSUT()

        whenever(fixture.connection.inputStream).thenThrow(IOException())
        whenever(fixture.connection.getHeaderField(eq("Retry-After"))).thenReturn("30")
        whenever(fixture.connection.responseCode).thenReturn(429)

        val envelope = SentryEnvelope.fromSession(fixture.serializer, createSession())

        val result = transport.send(envelope)

        verify(fixture.serializer).serialize(eq(envelope), any())
        assertFalse(result.isSuccess)
        assertTrue(transport.isRetryAfter("session"))
    }

    @Test
    fun `passes on the response code on error when sending an event`() {
        val transport = fixture.getSUT()

        whenever(fixture.connection.inputStream).thenThrow(IOException())
        whenever(fixture.connection.responseCode).thenReturn(1234)

        val event = SentryEvent()

        val result = transport.send(event)

        verify(fixture.serializer).serialize(eq(event), any())
        assertFalse(result.isSuccess)
        assertEquals(1234, result.responseCode)
    }

    @Test
    fun `passes on the response code on error when sending an envelope`() {
        val transport = fixture.getSUT()

        whenever(fixture.connection.inputStream).thenThrow(IOException())
        whenever(fixture.connection.responseCode).thenReturn(1234)

        val envelope = SentryEnvelope.fromSession(fixture.serializer, createSession())

        val result = transport.send(envelope)

        verify(fixture.serializer).serialize(eq(envelope), any())
        assertFalse(result.isSuccess)
        assertEquals(1234, result.responseCode)
    }

    @Test
    fun `uses the default retry interval if there is no Retry-After header when sending an event`() {
        val transport = fixture.getSUT()

        whenever(fixture.connection.inputStream).thenThrow(IOException())
        whenever(fixture.connection.responseCode).thenReturn(429)

        val event = SentryEvent()

        val result = transport.send(event)

        verify(fixture.serializer).serialize(eq(event), any())
        assertFalse(result.isSuccess)
        assertTrue(transport.isRetryAfter("event"))
    }

    @Test
    fun `uses the default retry interval if there is no Retry-After header when sending an envelope`() {
        val transport = fixture.getSUT()

        whenever(fixture.connection.inputStream).thenThrow(IOException())
        whenever(fixture.connection.responseCode).thenReturn(429)

        val envelope = SentryEnvelope.fromSession(fixture.serializer, createSession())

        val result = transport.send(envelope)

        verify(fixture.serializer).serialize(eq(envelope), any())
        assertFalse(result.isSuccess)
        assertTrue(transport.isRetryAfter("session"))
    }

    @Test
    fun `failure to get response code doesn't break sending an event`() {
        val transport = fixture.getSUT()

        whenever(fixture.connection.inputStream).thenThrow(IOException())
        whenever(fixture.connection.responseCode).thenThrow(IOException())

        val event = SentryEvent()

        val result = transport.send(event)

        verify(fixture.serializer).serialize(eq(event), any())
        assertFalse(result.isSuccess)
        assertEquals(-1, result.responseCode)
    }

    @Test
    fun `failure to get response code doesn't break sending an envelope`() {
        val transport = fixture.getSUT()

        whenever(fixture.connection.inputStream).thenThrow(IOException())
        whenever(fixture.connection.responseCode).thenThrow(IOException())

        val session = Session("123", User(), "env", "release")
        val envelope = SentryEnvelope.fromSession(fixture.serializer, session)

        val result = transport.send(envelope)

        verify(fixture.serializer).serialize(eq(envelope), any())
        assertFalse(result.isSuccess)
        assertEquals(-1, result.responseCode)
    }

    @Test
    fun `uses X-Sentry-Rate-Limit and returns accordingly`() {
        val transport = fixture.getSUT()

        whenever(fixture.connection.inputStream).thenThrow(IOException())
        whenever(fixture.connection.getHeaderField(eq("X-Sentry-Rate-Limits")))
            .thenReturn("50:transaction:key, 2700:default;event;security:organization")

        val event = SentryEvent()

        val result = transport.send(event)

        verify(fixture.serializer).serialize(eq(event), any())
        assertFalse(result.isSuccess)
        assertTrue(transport.isRetryAfter("event"))
    }

    @Test
    fun `uses X-Sentry-Rate-Limit and allows sending if time has passed`() {
        val transport = fixture.getSUT()

        whenever(fixture.connection.inputStream).thenThrow(IOException())
        whenever(fixture.connection.getHeaderField(eq("X-Sentry-Rate-Limits")))
            .thenReturn("50:transaction:key, 1:default;event;security:organization")

        val event = SentryEvent()

        val result = transport.send(event)

        verify(fixture.serializer).serialize(eq(event), any())
        assertFalse(result.isSuccess)
        Thread.sleep(2000)
        assertFalse(transport.isRetryAfter("event"))
    }

    @Test
    fun `parse X-Sentry-Rate-Limit and set its values and retry after should be true`() {
        val transport = fixture.getSUT()

        whenever(fixture.connection.inputStream).thenThrow(IOException())
        whenever(fixture.connection.getHeaderField(eq("X-Sentry-Rate-Limits")))
            .thenReturn("50:transaction:key, 2700:default;event;security:organization")

        val event = SentryEvent()

        transport.send(event)

        assertTrue(transport.isRetryAfter("transaction"))
        assertTrue(transport.isRetryAfter("default"))
        assertTrue(transport.isRetryAfter("event"))
        assertTrue(transport.isRetryAfter("security"))
    }

    @Test
    fun `parse X-Sentry-Rate-Limit and set its values and retry after should be false`() {
        val transport = fixture.getSUT()

        whenever(fixture.connection.inputStream).thenThrow(IOException())
        whenever(fixture.connection.getHeaderField(eq("X-Sentry-Rate-Limits")))
            .thenReturn("1:transaction:key, 1:default;event;security:organization")

        val event = SentryEvent()

        transport.send(event)
        Thread.sleep(2000)
        assertFalse(transport.isRetryAfter("transaction"))
        assertFalse(transport.isRetryAfter("default"))
        assertFalse(transport.isRetryAfter("event"))
        assertFalse(transport.isRetryAfter("security"))
    }

    @Test
    fun `When X-Sentry-Rate-Limit categories are empty, applies to all the categories`() {
        val transport = fixture.getSUT()

        whenever(fixture.connection.inputStream).thenThrow(IOException())
        whenever(fixture.connection.getHeaderField(eq("X-Sentry-Rate-Limits")))
            .thenReturn("50::key")

        val event = SentryEvent()

        transport.send(event)

        assertTrue(transport.isRetryAfter("event"))
    }

    private fun createSession(): Session {
        return Session("123", User(), "env", "release")
    }
}
