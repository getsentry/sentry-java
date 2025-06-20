package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals

class MeasurementUnitTest {
  @Test
  fun `apiName converts Duration enum to lowercase`() {
    assertEquals("nanosecond", MeasurementUnit.Duration.NANOSECOND.apiName())
    assertEquals("microsecond", MeasurementUnit.Duration.MICROSECOND.apiName())
    assertEquals("millisecond", MeasurementUnit.Duration.MILLISECOND.apiName())
    assertEquals("second", MeasurementUnit.Duration.SECOND.apiName())
    assertEquals("minute", MeasurementUnit.Duration.MINUTE.apiName())
    assertEquals("hour", MeasurementUnit.Duration.HOUR.apiName())
    assertEquals("day", MeasurementUnit.Duration.DAY.apiName())
    assertEquals("week", MeasurementUnit.Duration.WEEK.apiName())
  }

  @Test
  fun `apiName converts Information enum to lowercase`() {
    assertEquals("bit", MeasurementUnit.Information.BIT.apiName())
    assertEquals("byte", MeasurementUnit.Information.BYTE.apiName())
    assertEquals("kilobyte", MeasurementUnit.Information.KILOBYTE.apiName())
    assertEquals("kibibyte", MeasurementUnit.Information.KIBIBYTE.apiName())
    assertEquals("megabyte", MeasurementUnit.Information.MEGABYTE.apiName())
    assertEquals("mebibyte", MeasurementUnit.Information.MEBIBYTE.apiName())
    assertEquals("gigabyte", MeasurementUnit.Information.GIGABYTE.apiName())
    assertEquals("gibibyte", MeasurementUnit.Information.GIBIBYTE.apiName())
    assertEquals("terabyte", MeasurementUnit.Information.TERABYTE.apiName())
    assertEquals("tebibyte", MeasurementUnit.Information.TEBIBYTE.apiName())
    assertEquals("petabyte", MeasurementUnit.Information.PETABYTE.apiName())
    assertEquals("pebibyte", MeasurementUnit.Information.PEBIBYTE.apiName())
    assertEquals("exabyte", MeasurementUnit.Information.EXABYTE.apiName())
    assertEquals("exbibyte", MeasurementUnit.Information.EXBIBYTE.apiName())
  }

  @Test
  fun `apiName converts Fraction enum to lowercase`() {
    assertEquals("ratio", MeasurementUnit.Fraction.RATIO.apiName())
    assertEquals("percent", MeasurementUnit.Fraction.PERCENT.apiName())
  }

  @Test
  fun `apiName converts Custom to lowercase`() {
    assertEquals("test", MeasurementUnit.Custom("Test").apiName())
  }
}
