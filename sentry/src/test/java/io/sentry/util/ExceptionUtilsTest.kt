package io.sentry.util

import java.lang.RuntimeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExceptionUtilsTest {
  @Test
  fun `returns same exception when there is no cause`() {
    val ex = RuntimeException()
    assertEquals(ex, ExceptionUtils.findRootCause(ex))
  }

  @Test
  fun `returns first cause when there are multiple causes`() {
    val rootCause = RuntimeException()
    val cause = RuntimeException(rootCause)
    val ex = RuntimeException(cause)
    assertEquals(rootCause, ExceptionUtils.findRootCause(ex))
  }

  @Test
  fun `handleFatal rethrows OutOfMemoryError`() {
    assertFails { ExceptionUtils.handleFatal(OutOfMemoryError()) }
  }

  @Test
  fun `handleFatal rethrows StackOverflowError`() {
    assertFails { ExceptionUtils.handleFatal(StackOverflowError()) }
  }

  @Test
  fun `handleFatal rethrows ThreadDeath`() {
    assertFails { ExceptionUtils.handleFatal(ThreadDeath()) }
  }

  @Test
  fun `handleFatal restores interrupt flag for InterruptedException without rethrowing`() {
    try {
      ExceptionUtils.handleFatal(InterruptedException())
      assertTrue(Thread.currentThread().isInterrupted)
    } finally {
      // clear the interrupt flag so it doesn't leak into other tests
      Thread.interrupted()
    }
  }

  @Test
  fun `handleFatal does nothing for regular exceptions`() {
    ExceptionUtils.handleFatal(RuntimeException())
    assertFalse(Thread.currentThread().isInterrupted)
  }
}
