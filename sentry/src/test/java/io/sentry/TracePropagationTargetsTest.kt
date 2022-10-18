package io.sentry

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TracePropagationTargetsTest {

    @Test
    fun `origins contain the url when it contains one of the defined origins`() {
        val origins = listOf("localhost", "^(http|https)://api\\..*$")
        assertTrue(TracePropagationTargets.contain(origins, "http://localhost:8080/foo"))
        assertTrue(TracePropagationTargets.contain(origins, "http://xxx.localhost:8080/foo"))
    }

    @Test
    fun `origins contain the url when it matches regex`() {
        val origins = listOf("localhost", "^(http|https)://api\\..*$")
        assertTrue(TracePropagationTargets.contain(origins, "http://api.foo.bar:8080/foo"))
        assertTrue(TracePropagationTargets.contain(origins, "https://api.foo.bar:8080/foo"))
        assertFalse(TracePropagationTargets.contain(origins, "ftp://api.foo.bar:8080/foo"))
        assertTrue(TracePropagationTargets.contain(origins, "http://api.localhost:8080/foo"))
    }

    @Test
    fun `when no origins are defined, returns false for every url`() {
        assertFalse(TracePropagationTargets.contain(emptyList(), "http://some.api.com/"))
    }

    @Test
    fun `ignores broken regex`() {
        assertFalse(TracePropagationTargets.contain(listOf("AABB???"), "http://some.api.com/"))
    }
}
