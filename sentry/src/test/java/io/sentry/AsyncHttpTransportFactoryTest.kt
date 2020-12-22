package io.sentry

import io.sentry.exception.InvalidDsnException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class AsyncHttpTransportFactoryTest {

    @Test
    fun `When HttpTransportFactory doesn't have a valid DSN, it throws InvalidDsnException`() {
        assertFailsWith<InvalidDsnException> { AsyncHttpTransportFactory().create(SentryOptions()) }
    }

    @Test
    fun `When HttpTransportFactory doesn't have a well formed DSN-URL, it throws IllegalArgumentException`() {
        val options = SentryOptions().apply {
            dsn = "ttps://key@sentry.io/proj"
        }
        assertFailsWith<IllegalArgumentException> { AsyncHttpTransportFactory().create(options) }
    }

    @Test
    fun `When options is set correctly, HttpTransport is created`() {
        val options = SentryOptions().apply {
            dsn = "https://key@sentry.io/proj"
        }
        val transport = AsyncHttpTransportFactory().create(options)
        assertNotNull(transport)
    }
}
