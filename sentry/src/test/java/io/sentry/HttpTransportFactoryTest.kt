package io.sentry

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class HttpTransportFactoryTest {

    @Test
    fun `When HttpTransportFactory doesn't have a valid DSN, it throws InvalidDsnException`() {
        assertFailsWith<InvalidDsnException> { HttpTransportFactory.create(SentryOptions()) }
    }

    @Test
    fun `When HttpTransportFactory doesn't have a well formed DSN-URL, it throws IllegalArgumentException`() {
        val options = SentryOptions().apply {
            dsn = "ttps://key@sentry.io/proj"
        }
        assertFailsWith<IllegalArgumentException> { HttpTransportFactory.create(options) }
    }

    @Test
    fun `When options is set correctly, HttpTransport is created`() {
        val options = SentryOptions().apply {
            dsn = "https://key@sentry.io/proj"
        }
        val transport = HttpTransportFactory.create(options)
        assertNotNull(transport)
    }
}
