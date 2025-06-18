package io.sentry

import io.sentry.util.PropagationTargetsUtils
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TracePropagationTargetsTest {
    @Test
    fun `origins contain the url when it contains one of the defined origins`() {
        val origins = listOf("localhost", "^(http|https)://api\\..*$")
        assertTrue(PropagationTargetsUtils.contain(origins, "http://localhost:8080/foo"))
        assertTrue(PropagationTargetsUtils.contain(origins, "http://xxx.localhost:8080/foo"))
    }

    @Test
    fun `origins contain the url when it matches regex`() {
        val origins = listOf("localhost", "^(http|https)://api\\..*$")
        assertTrue(PropagationTargetsUtils.contain(origins, "http://api.foo.bar:8080/foo"))
        assertTrue(PropagationTargetsUtils.contain(origins, "https://api.foo.bar:8080/foo"))
        assertFalse(PropagationTargetsUtils.contain(origins, "ftp://api.foo.bar:8080/foo"))
        assertTrue(PropagationTargetsUtils.contain(origins, "http://api.localhost:8080/foo"))
    }

    @Test
    fun `when no origins are defined, returns false for every url`() {
        assertFalse(PropagationTargetsUtils.contain(emptyList(), "http://some.api.com/"))
    }

    @Test
    fun `ignores broken regex`() {
        assertFalse(PropagationTargetsUtils.contain(listOf("AABB???"), "http://some.api.com/"))
    }
}
