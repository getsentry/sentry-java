package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals

class InstrumenterTest {
  @Test
  fun `can create from otel string`() {
    val instrumenter = Instrumenter.valueOf("OTEL")
    assertEquals(instrumenter, Instrumenter.OTEL)
  }

  @Test
  fun `can create from sentry string`() {
    val instrumenter = Instrumenter.valueOf("SENTRY")
    assertEquals(instrumenter, Instrumenter.SENTRY)
  }
}
