package io.sentry.util

import io.sentry.JsonObjectReader
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals

class CollectionUtilsTest {

    @Test
    fun `filters out map not matching predicate`() {
        val map = mapOf("key1" to 1, "key2" to 2, "key3" to 3)
        val result = CollectionUtils.filterMapEntries(map) {
            it.value % 2 == 0
        }
        assertEquals(2, result["key2"])
        assertEquals(1, result.size)
    }

    @Test
    fun `filters out list not matching predicate`() {
        val list = listOf("key1", "key2", "key3")
        val result = CollectionUtils.filterListEntries(list) {
            it != "key2"
        }
        assertEquals("key1", result[0])
        assertEquals("key3", result[1])
        assertEquals(2, result.size)
    }

    @Test
    fun `concurrent hashmap creation with null returns null`() {
        val result: Map<String, String>? = CollectionUtils.newConcurrentHashMap(null)
        assertEquals(null, result)
    }

    @Test
    fun `concurrent hashmap creation ignores null keys`() {
        val map = mutableMapOf("key1" to "value1", null to "value2", "key3" to "value3")
        val result = CollectionUtils.newConcurrentHashMap(map)

        assertEquals(2, result?.size)
        assertEquals("value1", result?.get("key1"))
        assertEquals("value3", result?.get("key3"))
    }

    @Test
    fun `concurrent hashmap creation ignores null values`() {
        val json = """
            {
                "key1": "value1",
                "key2": null,
                "key3": "value3"
            }
        """.trimIndent()
        val reader = JsonObjectReader(StringReader(json))
        val deserializedMap = reader.nextObjectOrNull() as Map<String, String>

        val result: Map<String, String>? = CollectionUtils.newConcurrentHashMap(deserializedMap)

        assertEquals(2, result?.size)
        assertEquals("value1", result?.get("key1"))
        assertEquals("value3", result?.get("key3"))
    }
}
