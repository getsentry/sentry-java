package io.sentry.util

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
}
