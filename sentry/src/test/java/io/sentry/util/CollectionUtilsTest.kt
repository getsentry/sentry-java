package io.sentry.util

import kotlin.test.Test
import kotlin.test.assertEquals

class CollectionUtilsTest {

    @Test
    fun `filters out map not maching predicate`() {
        val map = mapOf("key1" to 1, "key2" to 2, "key3" to 3)
        val result = CollectionUtils.filterMapEntries(map) {
            it.value % 2 == 0
        }
        assertEquals(2, result["key2"])
        assertEquals(1, result.size)
    }
}
