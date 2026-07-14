package io.sentry.util

import java.lang.RuntimeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ExceptionUtilsTest {
  private class CircularCauseThrowable : RuntimeException() {
    private var nextCause: Throwable? = null
    private var causeReads = 0

    fun linkTo(cause: Throwable) {
      nextCause = cause
    }

    override val cause: Throwable?
      get() {
        check(causeReads++ < 10) { "Throwable cause cycle was not detected" }
        return nextCause
      }
  }

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
  fun `does not loop indefinitely for cyclic cause chain`() {
    val first = CircularCauseThrowable()
    val second = CircularCauseThrowable()
    first.linkTo(second)
    second.linkTo(first)

    assertSame(second, ExceptionUtils.findRootCause(first))
  }
}
