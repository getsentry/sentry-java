package io.sentry.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

class BreadcrumbTest {
    @Test
    fun `cloning breadcrumb wont have the same references`() {
        val breadcrumb = Breadcrumb()
        breadcrumb.message = "message"
        breadcrumb.setData("data", "data")
        val unknown = mapOf(Pair("unknown", "unknown"))
        breadcrumb.acceptUnknownProperties(unknown)

        breadcrumb.type = "type"
        val level = SentryLevel.DEBUG
        breadcrumb.level = level
        breadcrumb.category = "category"

        val clone = breadcrumb.clone()

        assertNotNull(clone)
        assertNotSame(breadcrumb, clone)

        assertNotSame(breadcrumb.data, clone.data)

        assertNotSame(breadcrumb.unknown, clone.unknown)
    }

    @Test
    fun `cloning breadcrumb will have the same values`() {
        val breadcrumb = Breadcrumb()
        breadcrumb.message = "message"
        breadcrumb.setData("data", "data")
        val unknown = mapOf(Pair("unknown", "unknown"))
        breadcrumb.acceptUnknownProperties(unknown)

        breadcrumb.type = "type"
        val level = SentryLevel.DEBUG
        breadcrumb.level = level
        breadcrumb.category = "category"

        val clone = breadcrumb.clone()

        assertEquals("message", clone.message)
        assertEquals("data", clone.data["data"])
        assertEquals("unknown", clone.unknown["unknown"])
        assertEquals("type", clone.type)
        assertEquals(SentryLevel.DEBUG, clone.level)
        assertEquals("category", clone.category)
    }

    @Test
    fun `cloning breadcrumb and changing the original values wont change the clone values`() {
        val breadcrumb = Breadcrumb()
        breadcrumb.message = "message"
        breadcrumb.setData("data", "data")
        val unknown = mapOf(Pair("unknown", "unknown"))
        breadcrumb.acceptUnknownProperties(unknown)

        breadcrumb.type = "type"
        val level = SentryLevel.DEBUG
        breadcrumb.level = level
        breadcrumb.category = "category"

        val clone = breadcrumb.clone()

        breadcrumb.message = "newMessage"
        breadcrumb.data["data"] = "newData"
        breadcrumb.data["otherData"] = "otherData"
        val newUnknown = mapOf(Pair("unknown", "newUnknown"), Pair("otherUnknown", "otherUnknown"))
        breadcrumb.acceptUnknownProperties(newUnknown)

        breadcrumb.type = "newType"
        breadcrumb.level = SentryLevel.FATAL
        breadcrumb.category = "newCategory"

        assertEquals("message", clone.message)
        assertEquals("data", clone.data["data"])
        assertEquals(1, clone.data.size)
        assertEquals("unknown", clone.unknown["unknown"])
        assertEquals(1, clone.unknown.size)
        assertEquals("type", clone.type)
        assertEquals(SentryLevel.DEBUG, clone.level)
        assertEquals("category", clone.category)
    }

    @Test
    fun `breadcrumb has timestamp when created`() {
        val breadcrumb = Breadcrumb()
        assertNotNull(breadcrumb.timestamp)
    }

    @Test
    fun `breadcrumb takes message on ctor`() {
        val breadcrumb = Breadcrumb("this is a test")
        assertEquals("this is a test", breadcrumb.message)
    }
}
