package io.sentry.transport.apache

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import io.sentry.SentryOptions
import kotlin.test.Test
import kotlin.test.assertNotNull

class ApacheHttpClientTransportFactoryTest {

    @Test
    fun `creates ApacheHttpClientTransport`() {
        val factory = ApacheHttpClientTransportFactory()
        val transport = factory.create(SentryOptions(), mock())
        assertNotNull(transport)
    }

    @Test
    fun `options timeouts are used when creating ApacheHttpClientTransport`() {
        val factory = ApacheHttpClientTransportFactory()
        val options = spy(SentryOptions())
        factory.create(options, mock())
        verify(options, times(2)).connectionTimeoutMillis
        verify(options).readTimeoutMillis
    }
}
