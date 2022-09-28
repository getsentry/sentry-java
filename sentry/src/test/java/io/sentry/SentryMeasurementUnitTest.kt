package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals

class SentryMeasurementUnitTest {

    @Test
    fun `apiName converts enum to lowercase`() {
        assertEquals("nanosecond", SentryMeasurementUnit.NANOSECOND.apiName())
        assertEquals("microsecond", SentryMeasurementUnit.MICROSECOND.apiName())
        assertEquals("millisecond", SentryMeasurementUnit.MILLISECOND.apiName())
        assertEquals("second", SentryMeasurementUnit.SECOND.apiName())
        assertEquals("minute", SentryMeasurementUnit.MINUTE.apiName())
        assertEquals("hour", SentryMeasurementUnit.HOUR.apiName())
        assertEquals("day", SentryMeasurementUnit.DAY.apiName())
        assertEquals("week", SentryMeasurementUnit.WEEK.apiName())
        assertEquals("none", SentryMeasurementUnit.NONE.apiName())
    }
}
