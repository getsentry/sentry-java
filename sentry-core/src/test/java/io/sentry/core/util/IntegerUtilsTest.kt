package io.sentry.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IntegerUtilsTest {

    @Test
    fun `Number String is converted to Integer`() {
        assertEquals(1, IntegerUtils.getNumber("1"))
    }

    @Test
    fun `Empty String returns null`() {
        assertNull(IntegerUtils.getNumber(""))
    }

    @Test
    fun `Null String returns null`() {
        assertNull(IntegerUtils.getNumber(null))
    }

    @Test
    fun `Non number String returns null`() {
        assertNull(IntegerUtils.getNumber("abc"))
    }
}
