package io.sentry.protocol

import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MechanismTest {
  @Test
  fun `when setMeta receives immutable map as an argument, its still possible to add more meta`() {
    val mechanism =
      Mechanism().apply {
        meta = Collections.unmodifiableMap(mapOf<String, Any>("key1" to "value1"))
        meta!!["key2"] = "value2"
      }
    assertNotNull(mechanism.meta) {
      assertEquals(mapOf("key1" to "value1", "key2" to "value2"), it)
    }
  }

  @Test
  fun `when setData receives immutable map as an argument, its still possible to add more data`() {
    val mechanism =
      Mechanism().apply {
        data = Collections.unmodifiableMap(mapOf<String, Any>("key1" to "value1"))
        data!!["key2"] = "value2"
      }
    assertNotNull(mechanism.data) {
      assertEquals(mapOf("key1" to "value1", "key2" to "value2"), it)
    }
  }
}
