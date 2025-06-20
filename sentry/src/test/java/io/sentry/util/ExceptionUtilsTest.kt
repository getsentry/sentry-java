package io.sentry.util

import java.lang.RuntimeException
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
