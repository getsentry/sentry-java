package io.sentry

import kotlin.test.Test
import kotlin.test.assertSame

class NoOpAppStartExtenderTest {
  private val extender = NoOpAppStartExtender.getInstance()

  @Test fun `extendAppStart does not throw`() = extender.extendAppStart()

  @Test fun `finishAppStart does not throw`() = extender.finishAppStart()

  @Test
  fun `getExtendedAppStartSpan returns NoOpSpan`() {
    assertSame(NoOpSpan.getInstance(), extender.extendedAppStartSpan)
  }
}
