package io.sentry.util.runtime

import kotlin.test.Test
import kotlin.test.assertTrue

class NeutralRuntimeManagerTest {

  val sut = NeutralRuntimeManager()

  @Test
  fun `runWithRelaxedPolicy runs the code`() {
    var called = false

    called = sut.runWithRelaxedPolicy { true }

    // Ensure the code ran
    assertTrue(called)
  }
}
