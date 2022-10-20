package io.sentry.util

import java.util.Calendar
import java.util.concurrent.atomic.AtomicIntegerArray
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonSerializationUtilsTest {

    @Test
    fun `serializes calendar to map`() {
        val calendar = Calendar.getInstance()
        calendar.set(2022, 0, 1, 11, 59, 58)

        val actual = JsonSerializationUtils.calendarToMap(calendar)

        val expected = mapOf<String, Any?>(
            "month" to 0L,
            "year" to 2022L,
            "dayOfMonth" to 1L,
            "hourOfDay" to 11L,
            "minute" to 59L,
            "second" to 58L
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `serializes AtomicIntegerArray to list`() {
        val actual = JsonSerializationUtils.atomicIntegerArrayToList(AtomicIntegerArray(arrayOf(1, 2, 3).toIntArray()))
        assertEquals(listOf(1, 2, 3), actual)
    }
}
