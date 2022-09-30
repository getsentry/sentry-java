package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals

class SentryMeasurementUnitTest {

    @Test
    fun `apiName converts Duration enum to lowercase`() {
        assertEquals("nanosecond", SentryMeasurementUnit.Duration.NANOSECOND.apiName())
        assertEquals("microsecond", SentryMeasurementUnit.Duration.MICROSECOND.apiName())
        assertEquals("millisecond", SentryMeasurementUnit.Duration.MILLISECOND.apiName())
        assertEquals("second", SentryMeasurementUnit.Duration.SECOND.apiName())
        assertEquals("minute", SentryMeasurementUnit.Duration.MINUTE.apiName())
        assertEquals("hour", SentryMeasurementUnit.Duration.HOUR.apiName())
        assertEquals("day", SentryMeasurementUnit.Duration.DAY.apiName())
        assertEquals("week", SentryMeasurementUnit.Duration.WEEK.apiName())
    }

    @Test
    fun `apiName converts Information enum to lowercase`() {
        assertEquals("bit", SentryMeasurementUnit.Information.BIT.apiName())
        assertEquals("byte", SentryMeasurementUnit.Information.BYTE.apiName())
        assertEquals("kilobyte", SentryMeasurementUnit.Information.KILOBYTE.apiName())
        assertEquals("kibibyte", SentryMeasurementUnit.Information.KIBIBYTE.apiName())
        assertEquals("megabyte", SentryMeasurementUnit.Information.MEGABYTE.apiName())
        assertEquals("mebibyte", SentryMeasurementUnit.Information.MEBIBYTE.apiName())
        assertEquals("gigabyte", SentryMeasurementUnit.Information.GIGABYTE.apiName())
        assertEquals("gibibyte", SentryMeasurementUnit.Information.GIBIBYTE.apiName())
        assertEquals("terabyte", SentryMeasurementUnit.Information.TERABYTE.apiName())
        assertEquals("tebibyte", SentryMeasurementUnit.Information.TEBIBYTE.apiName())
        assertEquals("petabyte", SentryMeasurementUnit.Information.PETABYTE.apiName())
        assertEquals("pebibyte", SentryMeasurementUnit.Information.PEBIBYTE.apiName())
        assertEquals("exabyte", SentryMeasurementUnit.Information.EXABYTE.apiName())
        assertEquals("exbibyte", SentryMeasurementUnit.Information.EXBIBYTE.apiName())
    }

    @Test
    fun `apiName converts Fraction enum to lowercase`() {
        assertEquals("ratio", SentryMeasurementUnit.Fraction.RATIO.apiName())
        assertEquals("percent", SentryMeasurementUnit.Fraction.PERCENT.apiName())
    }

    @Test
    fun `apiName converts Custom to lowercase`() {
        assertEquals("test", SentryMeasurementUnit.Custom("Test").apiName())
    }
}
