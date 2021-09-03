package io.sentry

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TracingOriginsTest {

    @Test
    fun `origins contain the url when it contains one of the defined origins`() {
        val tracingOrigins = TracingOrigins("localhost", "^(http|https)://api\\..*$")
        assertTrue(tracingOrigins.contain("http://localhost:8080/foo"))
        assertTrue(tracingOrigins.contain("http://xxx.localhost:8080/foo"))
    }

    @Test
    fun `origins contain the url when it matches regex`() {
        val tracingOrigins = TracingOrigins("localhost", "^(http|https)://api\\..*$")
        assertTrue(tracingOrigins.contain("http://api.foo.bar:8080/foo"))
        assertTrue(tracingOrigins.contain("https://api.foo.bar:8080/foo"))
        assertFalse(tracingOrigins.contain("ftp://api.foo.bar:8080/foo"))
        assertTrue(tracingOrigins.contain("http://api.localhost:8080/foo"))
    }

    @Test
    fun `when no origins are defined, returns true for every url`() {
        val tracingOrigins = TracingOrigins()
        assertTrue(tracingOrigins.contain("http://some.api.com/"))
    }
}
