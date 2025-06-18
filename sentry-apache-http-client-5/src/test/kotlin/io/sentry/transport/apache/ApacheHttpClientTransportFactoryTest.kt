package io.sentry.transport.apache

import io.sentry.SentryOptions
import io.sentry.test.getProperty
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.async.InternalHttpAsyncClient
import org.mockito.kotlin.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ApacheHttpClientTransportFactoryTest {
    class Fixture {
        fun getSut(options: SentryOptions = SentryOptions()) =
            ApacheHttpClientTransportFactory().create(options, mock()) as ApacheHttpClientTransport
    }

    private val fixture = Fixture()

    @Test
    fun `creates ApacheHttpClientTransport`() {
        assertNotNull(fixture.getSut())
    }

    @Test
    fun `options timeouts are applied to http client`() {
        val transport =
            fixture.getSut(
                SentryOptions().apply {
                    this.connectionTimeoutMillis = 1500
                    this.readTimeoutMillis = 2500
                },
            )
        val requestConfig = transport.getClient().getRequestConfig()
        assertEquals(1500, requestConfig.connectTimeout.toMilliseconds())
        assertEquals(1500, requestConfig.connectionRequestTimeout.toMilliseconds())
        assertEquals(2500, requestConfig.responseTimeout.toMilliseconds())
    }

    private fun ApacheHttpClientTransport.getClient(): InternalHttpAsyncClient = this.getProperty("httpclient")

    private fun InternalHttpAsyncClient.getRequestConfig(): RequestConfig = this.getProperty("defaultConfig")
}
