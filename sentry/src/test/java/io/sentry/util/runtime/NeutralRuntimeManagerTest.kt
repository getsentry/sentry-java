package io.sentry.util.runtime

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NeutralRuntimeManagerTest {

  val sut = NeutralRuntimeManager()

  @Test
  fun `runWithRelaxedPolicy runs the code`() {
    var called = false

    called = sut.runWithRelaxedPolicy<Boolean> { true }

    // Ensure the code ran
    assertTrue(called)
  }

  @Test
  fun `runWithRelaxedPolicy with runnable runs the code`() {
    var called = false

    sut.runWithRelaxedPolicy { called = true }

    // Ensure the code ran
    assertTrue(called)
  }

  @Test
  fun `runWithRelaxedPolicy propagates exception`() {
    var exceptionPropagated = false

    try {
      sut.runWithRelaxedPolicy<Unit> {
        throw IOException("test")
      }
    } catch (e: IOException) {
      assertEquals("test", e.message)
      exceptionPropagated = true
    }

    // Ensure the exception was propagated
    assertTrue(exceptionPropagated)
  }

  @Test
  fun `runWithRelaxedPolicy with runnable propagates exception`() {
    var exceptionPropagated = false

    try {
      sut.runWithRelaxedPolicy {
        throw IOException("test")
      }
    } catch (e: IOException) {
      assertEquals("test", e.message)
      exceptionPropagated = true
    }

    // Ensure the exception was propagated
    assertTrue(exceptionPropagated)
  }
}
