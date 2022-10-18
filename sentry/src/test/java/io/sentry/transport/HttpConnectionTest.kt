package io.sentry.transport

import io.sentry.ISerializer
import io.sentry.RequestDetails
import io.sentry.SentryEnvelope
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.SentryOptions.Proxy
import io.sentry.Session
import io.sentry.protocol.User
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy.Type
import java.net.URL
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpConnectionTest {

    private class Fixture {
        val serializer = mock<ISerializer>()
        var proxy: Proxy? = null
        val connection = mock<HttpsURLConnection>()
        val currentDateProvider = mock<ICurrentDateProvider>()
        val authenticatorWrapper = mock<AuthenticatorWrapper>()
        val rateLimiter = mock<RateLimiter>()
        var sslSocketFactory: SSLSocketFactory? = null
        var hostnameVerifier: HostnameVerifier? = null
        val requestDetails = mock<RequestDetails>()

        init {
            whenever(connection.outputStream).thenReturn(mock())
            whenever(connection.inputStream).thenReturn(mock())
            whenever(connection.setHostnameVerifier(any())).thenCallRealMethod()
            whenever(connection.setSSLSocketFactory(any())).thenCallRealMethod()
            whenever(requestDetails.headers).thenReturn(mapOf("header-name" to "header-value"))
            val url = mock<URL>()
            whenever(url.openConnection()).thenReturn(connection)
            whenever(url.openConnection(any())).thenReturn(connection)
            whenever(requestDetails.url).thenReturn(url)
        }

        fun getSUT(): HttpConnection {
            val options = SentryOptions()
            options.setSerializer(serializer)
            options.proxy = proxy
            options.sslSocketFactory = sslSocketFactory
            options.hostnameVerifier = hostnameVerifier

            return HttpConnection(options, requestDetails, authenticatorWrapper, rateLimiter)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `test serializes envelope`() {
        val transport = fixture.getSUT()
        whenever(fixture.connection.responseCode).thenReturn(200)

        val envelope = SentryEnvelope.from(fixture.serializer, createSession(), null)

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

        val envelope = SentryEnvelope.from(fixture.serializer, createSession(), null)

        val result = transport.send(envelope)

        verify(fixture.rateLimiter).updateRetryAfterLimits(null, "30", 429)
        verify(fixture.serializer).serialize(eq(envelope), any())
        assertFalse(result.isSuccess)
    }

    @Test
    fun `passes on the response code on error when sending an envelope`() {
        val transport = fixture.getSUT()

        throwOnEnvelopeSerialize()
        whenever(fixture.connection.responseCode).thenReturn(1234)

        val envelope = SentryEnvelope.from(fixture.serializer, createSession(), null)

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

        val envelope = SentryEnvelope.from(fixture.serializer, createSession(), null)

        val result = transport.send(envelope)

        verify(fixture.serializer).serialize(eq(envelope), any())
        verify(fixture.rateLimiter).updateRetryAfterLimits(null, null, 429)
        assertFalse(result.isSuccess)
    }

    @Test
    fun `failure to get response code doesn't break sending an envelope`() {
        val transport = fixture.getSUT()

        throwOnEnvelopeSerialize()
        whenever(fixture.connection.responseCode).thenThrow(IOException())

        val session = Session("123", User(), "env", "release")
        val envelope = SentryEnvelope.from(fixture.serializer, session, null)

        val result = transport.send(envelope)

        verify(fixture.serializer).serialize(eq(envelope), any())
        assertFalse(result.isSuccess)
        assertEquals(-1, result.responseCode)
    }

    @Test
    fun `When SSLSocketFactory is given, set to connection`() {
        val factory = mock<SSLSocketFactory>()
        fixture.sslSocketFactory = factory
        val transport = fixture.getSUT()

        transport.send(createEnvelope())

        verify(fixture.connection).sslSocketFactory = eq(factory)
    }

    @Test
    fun `When SSLSocketFactory is not given, do not set to connection`() {
        val transport = fixture.getSUT()

        transport.send(createEnvelope())

        verify(fixture.connection, never()).sslSocketFactory = any()
    }

    @Test
    fun `When HostnameVerifier is given, set to connection`() {
        val hostname = mock<HostnameVerifier>()
        fixture.hostnameVerifier = hostname
        val transport = fixture.getSUT()

        transport.send(createEnvelope())

        verify(fixture.connection).hostnameVerifier = eq(hostname)
    }

    @Test
    fun `When HostnameVerifier is not given, do not set to connection`() {
        val transport = fixture.getSUT()

        transport.send(createEnvelope())

        verify(fixture.connection, never()).hostnameVerifier = any()
    }

    @Test
    fun `When Proxy host and port are given, set to connection`() {
        fixture.proxy = Proxy("proxy.example.com", "8090")
        val transport = fixture.getSUT()

        transport.send(createEnvelope())

        assertEquals(java.net.Proxy(Type.HTTP, InetSocketAddress("proxy.example.com", 8090)), transport.proxy)
    }

    @Test
    fun `When Proxy username and password are given, set to connection`() {
        fixture.proxy = Proxy("proxy.example.com", "8090", "some-user", "some-password")
        val transport = fixture.getSUT()

        transport.send(createEnvelope())

        verify(fixture.authenticatorWrapper).setDefault(
            check<ProxyAuthenticator> {
                assertEquals("some-user", it.user)
                assertEquals("some-password", it.password)
            }
        )
    }

    @Test
    fun `When Proxy port has invalid format, proxy is not set to connection`() {
        fixture.proxy = Proxy("proxy.example.com", "xxx")
        val transport = fixture.getSUT()

        transport.send(createEnvelope())

        assertNull(transport.proxy)
        verify(fixture.authenticatorWrapper, never()).setDefault(any())
    }

    @Test
    fun `sets common headers and on http connection`() {
        val transport = fixture.getSUT()

        transport.send(createEnvelope())

        verify(fixture.connection).setRequestProperty("header-name", "header-value")
        verify(fixture.requestDetails.url).openConnection()
    }

    private fun createSession(): Session {
        return Session("123", User(), "env", "release")
    }

    private fun throwOnEnvelopeSerialize() {
        whenever(fixture.serializer.serialize(any(), any())).thenThrow(IOException())
    }

    private fun createEnvelope(event: SentryEvent = SentryEvent()): SentryEnvelope {
        return SentryEnvelope.from(fixture.serializer, event, null)
    }
}
