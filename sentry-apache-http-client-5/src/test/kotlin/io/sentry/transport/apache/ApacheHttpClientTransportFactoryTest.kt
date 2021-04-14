package io.sentry.transport.apache

import com.nhaarman.mockitokotlin2.mock
import io.sentry.SentryOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.async.InternalHttpAsyncClient

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
        val options = SentryOptions().apply {
            this.connectionTimeoutMillis = 1500
            this.readTimeoutMillis = 2500
        }
        val transport = factory.create(options, mock()) as ApacheHttpClientTransport
        val requestConfig = transport.getClient().getRequestConfig()
        assertEquals(1500, requestConfig.connectTimeout.toMilliseconds())
        assertEquals(1500, requestConfig.connectionRequestTimeout.toMilliseconds())
        assertEquals(2500, requestConfig.responseTimeout.toMilliseconds())
    }

    private fun ApacheHttpClientTransport.getClient(): InternalHttpAsyncClient = this.getProperty("httpclient")
    private fun InternalHttpAsyncClient.getRequestConfig(): RequestConfig = this.getProperty("defaultConfig")

    private inline fun <reified T> Any.getProperty(name: String): T =
        try {
            this::class.java.getDeclaredField(name)
        } catch (_: NoSuchFieldException) {
            this::class.java.superclass.getDeclaredField(name)
        }.apply {
            this.isAccessible = true
        }.get(this) as T
}
