package io.sentry

import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class RequestDetailsResolverTest {
    @Test
    fun `When options doesn't have a valid DSN, it throws InvalidDsnException`() {
        assertFailsWith<IllegalArgumentException> { RequestDetailsResolver(SentryOptions()).resolve() }
    }

    @Test
    fun `When options doesn't have a well formed DSN-URL, it throws IllegalArgumentException`() {
        val options =
            SentryOptions().apply {
                dsn = "ttps://key@sentry.io/proj"
            }
        assertFailsWith<IllegalArgumentException> { RequestDetailsResolver(options).resolve() }
    }

    @Test
    fun `When options is set correctly, RequestDetails is created`() {
        val options =
            SentryOptions().apply {
                dsn = "https://key@sentry.io/proj"
            }
        val requestDetails = RequestDetailsResolver(options).resolve()
        assertNotNull(requestDetails)
    }

    @Test
    fun `resolves envelope url`() {
        val options =
            SentryOptions().apply {
                dsn = "https://key@sentry.io/proj"
            }
        val requestDetails = RequestDetailsResolver(options).resolve()
        assertEquals(URL("https://sentry.io/api/proj/envelope/"), requestDetails.url)
    }

    @Test
    fun `resolves common headers`() {
        val options =
            SentryOptions().apply {
                dsn = "https://key@sentry.io/proj"
                sentryClientName = "custom-client"
            }
        val requestDetails = RequestDetailsResolver(options).resolve()
        assertEquals("custom-client", requestDetails.headers["User-Agent"])
        assertEquals("Sentry sentry_version=7,sentry_client=custom-client,sentry_key=key", requestDetails.headers["X-Sentry-Auth"])
    }
}
