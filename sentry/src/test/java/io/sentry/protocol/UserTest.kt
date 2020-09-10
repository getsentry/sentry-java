package io.sentry.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

class UserTest {
    @Test
    fun `cloning user wont have the same references`() {
        val user = User()
        user.email = "a@a.com"
        user.id = "123"
        user.ipAddress = "123.x"
        user.username = "userName"
        val others = mutableMapOf(Pair("others", "others"))
        user.others = others
        val unknown = mapOf(Pair("unknown", "unknown"))
        user.acceptUnknownProperties(unknown)

        val clone = user.clone()

        assertNotNull(clone)
        assertNotSame(user, clone)

        assertNotSame(user.others, clone.others)

        assertNotSame(user.unknown, clone.unknown)
    }

    @Test
    fun `cloning user will have the same values`() {
        val user = User()
        user.email = "a@a.com"
        user.id = "123"
        user.ipAddress = "123.x"
        user.username = "userName"
        val others = mutableMapOf(Pair("others", "others"))
        user.others = others
        val unknown = mapOf(Pair("unknown", "unknown"))
        user.acceptUnknownProperties(unknown)

        val clone = user.clone()

        assertEquals("a@a.com", clone.email)
        assertEquals("123", clone.id)
        assertEquals("123.x", clone.ipAddress)
        assertEquals("userName", clone.username)
        assertEquals("others", clone.others!!["others"])
        assertEquals("unknown", clone.unknown["unknown"])
    }

    @Test
    fun `cloning user and changing the original values wont change the clone values`() {
        val user = User()
        user.email = "a@a.com"
        user.id = "123"
        user.ipAddress = "123.x"
        user.username = "userName"
        val others = mutableMapOf(Pair("others", "others"))
        user.others = others
        val unknown = mapOf(Pair("unknown", "unknown"))
        user.acceptUnknownProperties(unknown)

        val clone = user.clone()

        user.email = "b@b.com"
        user.id = "456"
        user.ipAddress = "456.x"
        user.username = "newUserName"
        user.others!!["others"] = "newOthers"
        user.others!!["anotherOne"] = "anotherOne"
        val newUnknown = mapOf(Pair("unknown", "newUnknown"), Pair("otherUnknown", "otherUnknown"))
        user.acceptUnknownProperties(newUnknown)

        assertEquals("a@a.com", clone.email)
        assertEquals("123", clone.id)
        assertEquals("123.x", clone.ipAddress)
        assertEquals("userName", clone.username)
        assertEquals("others", clone.others!!["others"])
        assertEquals(1, clone.others!!.size)
        assertEquals("unknown", clone.unknown["unknown"])
        assertEquals(1, clone.unknown.size)
    }
}
