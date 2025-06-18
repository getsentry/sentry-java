package io.sentry.protocol

import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull

class RequestTest {
    @Test
    fun `copying request wont have the same references`() {
        val request = createRequest()
        val clone = Request(request)

        assertNotNull(clone)
        assertNotSame(request, clone)

        assertNotSame(request.others, clone.others)

        assertNotSame(request.unknown, clone.unknown)
    }

    @Test
    fun `copying request will have the same values`() {
        val request = createRequest()
        val clone = Request(request)

        assertEquals("get", clone.method)
        assertEquals("http://localhost:8080", clone.url)
        assertEquals("?foo=bar", clone.queryString)
        assertEquals("envs", clone.envs!!["envs"])
        assertEquals("others", clone.others!!["others"])
        assertEquals("unknown", clone.unknown!!["unknown"])
        assertEquals(1000, clone.bodySize)
        assertEquals("fragment", clone.fragment)
        assertEquals("graphql", clone.apiTarget)
    }

    @Test
    fun `copying request and changing the original values wont change the clone values`() {
        val request = createRequest()
        val clone = Request(request)

        request.method = "post"
        request.url = "http://another-host:8081/"
        request.queryString = "?xxx=yyy"
        request.envs!!["envs"] = "newEnvs"
        request.others!!["others"] = "newOthers"
        request.others!!["anotherOne"] = "anotherOne"
        val newUnknown = mapOf(Pair("unknown", "newUnknown"), Pair("otherUnknown", "otherUnknown"))
        request.unknown = newUnknown
        request.bodySize = 1001
        request.fragment = "fragment2"
        request.apiTarget = "graphql"

        assertEquals("get", clone.method)
        assertEquals("http://localhost:8080", clone.url)
        assertEquals("?foo=bar", clone.queryString)
        assertEquals("envs", clone.envs!!["envs"])
        assertEquals(1, clone.envs!!.size)
        assertEquals("others", clone.others!!["others"])
        assertEquals(1, clone.others!!.size)
        assertEquals("unknown", clone.unknown!!["unknown"])
        assertEquals(1, clone.unknown!!.size)
        assertEquals(1000, clone.bodySize)
        assertEquals("fragment", clone.fragment)
        assertEquals("graphql", clone.apiTarget)
    }

    @Test
    fun `setting null others do not crash`() {
        val request = createRequest()
        request.others = null

        assertNull(request.others)
    }

    @Test
    fun `when setEnvs receives immutable map as an argument, its still possible to add more env to the request`() {
        val request =
            Request().apply {
                envs = Collections.unmodifiableMap(mapOf("env1" to "value1"))
                envs!!["env2"] = "value2"
            }
        assertNotNull(request.envs) {
            assertEquals(mapOf("env1" to "value1", "env2" to "value2"), it)
        }
    }

    @Test
    fun `when setOther receives immutable map as an argument, its still possible to add more others to the request`() {
        val request =
            Request().apply {
                others = Collections.unmodifiableMap(mapOf("key1" to "value1"))
                others!!["key2"] = "value2"
            }
        assertNotNull(request.others) {
            assertEquals(mapOf("key1" to "value1", "key2" to "value2"), it)
        }
    }

    @Test
    fun `when setHeaders receives immutable map as an argument, its still possible to add more headers to the request`() {
        val request =
            Request().apply {
                headers = Collections.unmodifiableMap(mapOf("key1" to "value1"))
                headers!!["key2"] = "value2"
            }
        assertNotNull(request.headers) {
            assertEquals(mapOf("key1" to "value1", "key2" to "value2"), it)
        }
    }

    private fun createRequest(): Request =
        Request().apply {
            method = "get"
            url = "http://localhost:8080"
            queryString = "?foo=bar"
            envs = mutableMapOf(Pair("envs", "envs"))
            val others = mutableMapOf(Pair("others", "others"))
            setOthers(others)
            val unknown = mapOf(Pair("unknown", "unknown"))
            setUnknown(unknown)
            bodySize = 1000
            fragment = "fragment"
            apiTarget = "graphql"
        }
}
