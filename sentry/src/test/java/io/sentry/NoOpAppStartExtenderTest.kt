package io.sentry

import kotlin.test.Test
import kotlin.test.assertNull

class NoOpAppStartExtenderTest {
  private val extender = NoOpAppStartExtender.getInstance()

  @Test fun `extendAppStart does not throw`() = extender.extendAppStart()

  @Test fun `finishExtendedAppStart does not throw`() = extender.finishExtendedAppStart()

  @Test
  fun `getExtendedAppStartSpan returns null`() {
    assertNull(extender.extendedAppStartSpan)
  }
}
