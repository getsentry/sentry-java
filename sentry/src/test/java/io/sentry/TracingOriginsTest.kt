package io.sentry

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TracingOriginsTest {

    @Test
    fun `origins contain the url when it contains one of the defined origins`() {
        val origins = listOf("localhost", "^(http|https)://api\\..*$")
        assertTrue(TracingOrigins.contain(origins, "http://localhost:8080/foo"))
        assertTrue(TracingOrigins.contain(origins, "http://xxx.localhost:8080/foo"))
    }

    @Test
    fun `origins contain the url when it matches regex`() {
        val origins = listOf("localhost", "^(http|https)://api\\..*$")
        assertTrue(TracingOrigins.contain(origins, "http://api.foo.bar:8080/foo"))
        assertTrue(TracingOrigins.contain(origins, "https://api.foo.bar:8080/foo"))
        assertFalse(TracingOrigins.contain(origins, "ftp://api.foo.bar:8080/foo"))
        assertTrue(TracingOrigins.contain(origins, "http://api.localhost:8080/foo"))
    }

    @Test
    fun `when no origins are defined, returns false for every url`() {
        assertFalse(TracingOrigins.contain(emptyList(), "http://some.api.com/"))
    }
}
