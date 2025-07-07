package io.sentry.protocol

import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull

class UserTest {
  @Test
  fun `copying user wont have the same references`() {
    val user = createUser()
    val clone = User(user)

    assertNotNull(clone)
    assertNotSame(user, clone)

    assertNotSame(user.data, clone.data)

    assertNotSame(user.unknown, clone.unknown)
  }

  @Test
  fun `copying user will have the same values`() {
    val user = createUser()
    val clone = User(user)

    assertEquals("a@a.com", clone.email)
    assertEquals("123", clone.id)
    assertEquals("123.x", clone.ipAddress)
    assertEquals("userName", clone.username)
    assertEquals("data", clone.data!!["data"])
    assertEquals("unknown", clone.unknown!!["unknown"])
  }

  @Test
  fun `copying user and changing the original values wont change the clone values`() {
    val user = createUser()
    val clone = User(user)

    user.email = "b@b.com"
    user.id = "456"
    user.ipAddress = "456.x"
    user.username = "newUserName"
    user.data!!["data"] = "newOthers"
    user.data!!["anotherOne"] = "anotherOne"
    val newUnknown = mapOf(Pair("unknown", "newUnknown"), Pair("otherUnknown", "otherUnknown"))
    user.setUnknown(newUnknown)

    assertEquals("a@a.com", clone.email)
    assertEquals("123", clone.id)
    assertEquals("123.x", clone.ipAddress)
    assertEquals("userName", clone.username)
    assertEquals("data", clone.data!!["data"])
    assertEquals(1, clone.data!!.size)
    assertEquals("unknown", clone.unknown!!["unknown"])
    assertEquals(1, clone.unknown!!.size)
  }

  @Test
  fun `setting null data do not crash`() {
    val user = createUser()
    user.data = null

    assertNull(user.data)
  }

  @Test
  fun `when setOther receives immutable map as an argument, its still possible to add more data to the user`() {
    val user =
      User().apply {
        data = Collections.unmodifiableMap(mapOf("key1" to "value1"))
        data!!["key2"] = "value2"
      }
    assertNotNull(user.data) { assertEquals(mapOf("key1" to "value1", "key2" to "value2"), it) }
  }

  private fun createUser(): User =
    User().apply {
      email = "a@a.com"
      id = "123"
      ipAddress = "123.x"
      username = "userName"
      val data = mutableMapOf(Pair("data", "data"))
      setData(data)
      val unknown = mapOf(Pair("unknown", "unknown"))
      setUnknown(unknown)
    }
}
