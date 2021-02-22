package io.sentry.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull

class RequestTest {
    @Test
    fun `cloning request wont have the same references`() {
        val request = createRequest()
        val clone = request.clone()

        assertNotNull(clone)
        assertNotSame(request, clone)

        assertNotSame(request.others, clone.others)

        assertNotSame(request.unknown, clone.unknown)
    }

    @Test
    fun `cloning request will have the same values`() {
        val request = createRequest()
        val clone = request.clone()

        assertEquals("get", clone.method)
        assertEquals("http://localhost:8080", clone.url)
        assertEquals("?foo=bar", clone.queryString)
        assertEquals("envs", clone.envs!!["envs"])
        assertEquals("others", clone.others!!["others"])
        assertEquals("unknown", clone.unknown!!["unknown"])
    }

    @Test
    fun `cloning request and changing the original values wont change the clone values`() {
        val request = createRequest()
        val clone = request.clone()

        request.method = "post"
        request.url = "http://another-host:8081/"
        request.queryString = "?xxx=yyy"
        request.envs!!["envs"] = "newEnvs"
        request.others!!["others"] = "newOthers"
        request.others!!["anotherOne"] = "anotherOne"
        val newUnknown = mapOf(Pair("unknown", "newUnknown"), Pair("otherUnknown", "otherUnknown"))
        request.acceptUnknownProperties(newUnknown)

        assertEquals("get", clone.method)
        assertEquals("http://localhost:8080", clone.url)
        assertEquals("?foo=bar", clone.queryString)
        assertEquals("envs", clone.envs!!["envs"])
        assertEquals(1, clone.envs!!.size)
        assertEquals("others", clone.others!!["others"])
        assertEquals(1, clone.others!!.size)
        assertEquals("unknown", clone.unknown!!["unknown"])
        assertEquals(1, clone.unknown!!.size)
    }

    @Test
    fun `setting null others do not crash`() {
        val request = createRequest()
        request.others = null

        assertNull(request.others)
    }

    private fun createRequest(): Request {
        return Request().apply {
            method = "get"
            url = "http://localhost:8080"
            queryString = "?foo=bar"
            envs = mutableMapOf(Pair("envs", "envs"))
            val others = mutableMapOf(Pair("others", "others"))
            setOthers(others)
            val unknown = mapOf(Pair("unknown", "unknown"))
            acceptUnknownProperties(unknown)
        }
    }
}
