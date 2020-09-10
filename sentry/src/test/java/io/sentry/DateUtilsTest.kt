package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals

class DateUtilsTest {

    @Test
    fun `When ISO date has milliseconds`() {
        val date = DateUtils.getDateTime("2020-03-27T08:52:58.015Z")
        val result = DateUtils.getTimestamp(date)
        assertEquals("2020-03-27T08:52:58.015Z", result)
    }

    @Test
    fun `When ISO date has only seconds`() {
        val date = DateUtils.getDateTime("2020-03-27T08:52:58Z")
        val result = DateUtils.getTimestamp(date)
        assertEquals("2020-03-27T08:52:58.000Z", result)
    }
}
