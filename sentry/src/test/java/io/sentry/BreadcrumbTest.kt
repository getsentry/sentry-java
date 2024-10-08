package io.sentry

import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull

class BreadcrumbTest {

    @Test
    fun `copying breadcrumb wont have the same references`() {
        val breadcrumb = Breadcrumb()
        breadcrumb.message = "message"
        breadcrumb.setData("data", "data")
        val unknown = mapOf(Pair("unknown", "unknown"))
        breadcrumb.setUnknown(unknown)

        breadcrumb.type = "type"
        val level = SentryLevel.DEBUG
        breadcrumb.level = level
        breadcrumb.category = "category"
        breadcrumb.origin = "origin"

        val clone = Breadcrumb(breadcrumb)

        assertNotNull(clone)
        assertNotSame(breadcrumb, clone)

        assertNotSame(breadcrumb.data, clone.data)

        assertNotSame(breadcrumb.unknown, clone.unknown)
    }

    @Test
    fun `copying breadcrumb will have the same values`() {
        val breadcrumb = Breadcrumb()
        breadcrumb.message = "message"
        breadcrumb.setData("data", "data")
        val unknown = mapOf(Pair("unknown", "unknown"))
        breadcrumb.setUnknown(unknown)

        breadcrumb.type = "type"
        val level = SentryLevel.DEBUG
        breadcrumb.level = level
        breadcrumb.category = "category"
        breadcrumb.origin = "origin"

        val clone = Breadcrumb(breadcrumb)

        assertEquals("message", clone.message)
        assertEquals("data", clone.data["data"])
        assertEquals("unknown", clone.unknown!!["unknown"])
        assertEquals("type", clone.type)
        assertEquals(SentryLevel.DEBUG, clone.level)
        assertEquals("category", clone.category)
        assertEquals("origin", clone.origin)
    }

    @Test
    fun `copying breadcrumb and changing the original values wont change the clone values`() {
        val breadcrumb = Breadcrumb()
        breadcrumb.message = "message"
        breadcrumb.setData("data", "data")
        val unknown = mapOf(Pair("unknown", "unknown"))
        breadcrumb.setUnknown(unknown)

        breadcrumb.type = "type"
        val level = SentryLevel.DEBUG
        breadcrumb.level = level
        breadcrumb.category = "category"
        breadcrumb.origin = "origin"

        val clone = Breadcrumb(breadcrumb)

        breadcrumb.message = "newMessage"
        breadcrumb.data["data"] = "newData"
        breadcrumb.data["otherData"] = "otherData"

        breadcrumb.type = "newType"
        breadcrumb.level = SentryLevel.FATAL
        breadcrumb.category = "newCategory"
        breadcrumb.origin = "newOrigin"

        assertEquals("message", clone.message)
        assertEquals("data", clone.data["data"])
        assertEquals(1, clone.data.size)
        assertEquals("unknown", clone.unknown!!["unknown"])
        assertEquals(1, clone.unknown!!.size)
        assertEquals("type", clone.type)
        assertEquals(SentryLevel.DEBUG, clone.level)
        assertEquals("category", clone.category)
        assertEquals("origin", clone.origin)
    }

    @Test
    fun `breadcrumb has timestamp when created`() {
        val breadcrumb = Breadcrumb()
        assertNotNull(breadcrumb.timestamp)
    }

    @Test
    fun `breadcrumb can be created with Date timestamp`() {
        val breadcrumb = Breadcrumb(Date(123L))
        assertEquals(123L, breadcrumb.timestamp.time)
    }

    @Test
    fun `breadcrumb takes message on ctor`() {
        val breadcrumb = Breadcrumb("this is a test")
        assertEquals("this is a test", breadcrumb.message)
    }

    @Test
    fun `creates HTTP breadcrumb`() {
        val breadcrumb = Breadcrumb.http("http://example.com/api?q=1#top", "POST")
        assertEquals("http://example.com/api", breadcrumb.data["url"])
        assertEquals("q=1", breadcrumb.data["http.query"])
        assertEquals("top", breadcrumb.data["http.fragment"])
        assertEquals("POST", breadcrumb.data["method"])
        assertEquals("http", breadcrumb.type)
        assertEquals("http", breadcrumb.category)
    }

    @Test
    fun `creates HTTP breadcrumb with http status when status code is provided`() {
        val breadcrumb = Breadcrumb.http("http://example.com", "POST", 400)
        assertEquals("http://example.com", breadcrumb.data["url"])
        assertEquals("POST", breadcrumb.data["method"])
        assertEquals("http", breadcrumb.type)
        assertEquals("http", breadcrumb.category)
        assertEquals(400, breadcrumb.data["status_code"])
    }

    @Test
    fun `creates HTTP breadcrumb without http status when code is null`() {
        val breadcrumb = Breadcrumb.http("http://example.com", "POST", null)
        assertEquals("http://example.com", breadcrumb.data["url"])
        assertEquals("POST", breadcrumb.data["method"])
        assertEquals("http", breadcrumb.type)
        assertEquals("http", breadcrumb.category)
        assertFalse(breadcrumb.data.containsKey("status_code"))
    }

    @Test
    fun `creates navigation breadcrumb`() {
        val breadcrumb = Breadcrumb.navigation("from", "to")
        assertEquals("from", breadcrumb.data["from"])
        assertEquals("to", breadcrumb.data["to"])
        assertEquals("navigation", breadcrumb.type)
        assertEquals("navigation", breadcrumb.category)
    }

    @Test
    fun `creates transaction breadcrumb`() {
        val breadcrumb = Breadcrumb.transaction("message")
        assertEquals("default", breadcrumb.type)
        assertEquals("sentry.transaction", breadcrumb.category)
        assertEquals("message", breadcrumb.message)
    }

    @Test
    fun `creates query breadcrumb`() {
        val breadcrumb = Breadcrumb.query("message")
        assertEquals("query", breadcrumb.type)
        assertEquals("message", breadcrumb.message)
    }

    @Test
    fun `creates ui breadcrumb`() {
        val breadcrumb = Breadcrumb.ui("click", "message")
        assertEquals("default", breadcrumb.type)
        assertEquals("ui.click", breadcrumb.category)
        assertEquals("message", breadcrumb.message)
    }

    @Test
    fun `creates user breadcrumb`() {
        val breadcrumb = Breadcrumb.user("click", "message")
        assertEquals("user", breadcrumb.type)
        assertEquals("message", breadcrumb.message)
        assertEquals("click", breadcrumb.category)
    }

    @Test
    fun `creates debug breadcrumb`() {
        val breadcrumb = Breadcrumb.debug("message")
        assertEquals("debug", breadcrumb.type)
        assertEquals("message", breadcrumb.message)
        assertEquals(SentryLevel.DEBUG, breadcrumb.level)
    }

    @Test
    fun `creates info breadcrumb`() {
        val breadcrumb = Breadcrumb.info("message")
        assertEquals("info", breadcrumb.type)
        assertEquals("message", breadcrumb.message)
        assertEquals(SentryLevel.INFO, breadcrumb.level)
    }

    @Test
    fun `creates error breadcrumb`() {
        val breadcrumb = Breadcrumb.error("message")
        assertEquals("error", breadcrumb.type)
        assertEquals("message", breadcrumb.message)
        assertEquals(SentryLevel.ERROR, breadcrumb.level)
    }

    @Test
    fun `serializes String keys for graphql data loader breadcrumb`() {
        val breadcrumb = Breadcrumb.graphqlDataLoader(listOf("key1", "key2"), String::class.java, Throwable::class.java, null)
        assertEquals("graphql", breadcrumb.type)
        assertEquals("graphql.data_loader", breadcrumb.category)
        assertEquals(listOf("key1", "key2"), breadcrumb.data["keys"] as? Iterable<String>)
        assertEquals("java.lang.String", breadcrumb.data["key_type"])
        assertEquals("java.lang.Throwable", breadcrumb.data["value_type"])
        assertNull(breadcrumb.data["name"])
    }

    @Test
    fun `serializes Long keys for graphql data loader breadcrumb`() {
        val breadcrumb = Breadcrumb.graphqlDataLoader(listOf(java.lang.Long.valueOf(1), java.lang.Long.valueOf(2)), java.lang.Long::class.java, Throwable::class.java, null)
        assertEquals("graphql", breadcrumb.type)
        assertEquals("graphql.data_loader", breadcrumb.category)
        assertEquals(listOf("1", "2"), breadcrumb.data["keys"] as? Iterable<String>)
        assertEquals("java.lang.Long", breadcrumb.data["key_type"])
        assertEquals("java.lang.Throwable", breadcrumb.data["value_type"])
        assertNull(breadcrumb.data["name"])
    }

    @Test
    fun `serializes object keys using toString for graphql data loader breadcrumb`() {
        val breadcrumb = Breadcrumb.graphqlDataLoader(listOf(TestKey(1L), TestKey(2L)), TestKey::class.java, Throwable::class.java, null)
        assertEquals("graphql", breadcrumb.type)
        assertEquals("graphql.data_loader", breadcrumb.category)
        assertEquals(listOf("1", "2"), breadcrumb.data["keys"] as? Iterable<String>)
        assertEquals("io.sentry.BreadcrumbTest\$TestKey", breadcrumb.data["key_type"])
        assertEquals("java.lang.Throwable", breadcrumb.data["value_type"])
        assertNull(breadcrumb.data["name"])
    }

    class TestKey(val id: Long) {
        override fun toString(): String {
            return id.toString()
        }
    }
}
