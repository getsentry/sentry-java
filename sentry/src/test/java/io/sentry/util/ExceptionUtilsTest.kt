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
  fun `rethrowIfFatal rethrows OutOfMemoryError`() {
    assertFails { ExceptionUtils.rethrowIfFatal(OutOfMemoryError()) }
  }

  @Test
  fun `rethrowIfFatal rethrows StackOverflowError`() {
    assertFails { ExceptionUtils.rethrowIfFatal(StackOverflowError()) }
  }

  @Test
  fun `rethrowIfFatal rethrows ThreadDeath`() {
    assertFails { ExceptionUtils.rethrowIfFatal(ThreadDeath()) }
  }

  @Test
  fun `rethrowIfFatal restores interrupt flag for InterruptedException without rethrowing`() {
    try {
      ExceptionUtils.rethrowIfFatal(InterruptedException())
      assertTrue(Thread.currentThread().isInterrupted)
    } finally {
      // clear the interrupt flag so it doesn't leak into other tests
      Thread.interrupted()
    }
  }

  @Test
  fun `rethrowIfFatal does nothing for regular exceptions`() {
    ExceptionUtils.rethrowIfFatal(RuntimeException())
    assertFalse(Thread.currentThread().isInterrupted)
  }
}
