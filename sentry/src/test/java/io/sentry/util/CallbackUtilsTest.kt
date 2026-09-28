package io.sentry.util

import com.google.common.truth.Truth.assertThat
import io.sentry.SentryOptions
import io.sentry.exception.ExceptionMechanismException
import io.sentry.exception.SentryCallbackError
import io.sentry.exception.SentryCallbackException
import io.sentry.protocol.Mechanism
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CallbackUtilsTest {
  @Test
  fun `cause traversal terminates on cycles without a marker`() {
    val first = IllegalStateException()
    val second = IllegalArgumentException(first)
    first.initCause(second)
    assertThat(CallbackUtils.isCallbackException(first)).isFalse()
    assertThat(CallbackUtils.isCallbackException(null)).isFalse()
  }

  @Test
  fun `traversal finds markers through mechanism wrappers and cycles`() {
    val cause = IllegalStateException()
    val marker = SentryCallbackException(cause)
    cause.initCause(marker)
    val mechanism = ExceptionMechanismException(Mechanism(), cause, Thread.currentThread())
    assertThat(CallbackUtils.isCallbackException(mechanism)).isTrue()
  }

  @Test
  fun `nested markers are not wrapped again`() {
    val options = SentryOptions().apply { isStrictCallbackMode = true }
    val marker = SentryCallbackException(IllegalStateException())
    assertThat(
        assertFailsWith<SentryCallbackException> {
          CallbackUtils.rethrowIfStrictCallbackMode(options, RuntimeException(marker))
        }
      )
      .isSameInstanceAs(marker)
    val error = SentryCallbackError(LinkageError())
    assertThat(
        assertFailsWith<SentryCallbackError> {
          CallbackUtils.rethrowIfStrictCallbackMode(options, RuntimeException(error))
        }
      )
      .isSameInstanceAs(error)
  }

  @Test
  fun `disabled mode still propagates existing markers`() {
    val options = SentryOptions()
    for (marker in
      listOf(
        SentryCallbackException(IllegalStateException()),
        SentryCallbackError(LinkageError()),
      )) {
      val thrown =
        kotlin.test.assertFails {
          CallbackUtils.rethrowIfStrictCallbackMode(options, RuntimeException(marker))
        }
      assertThat(thrown).isSameInstanceAs(marker)
    }
  }

  @Test
  fun `disabled mode does not wrap errors or exceptions`() {
    val options = SentryOptions()
    CallbackUtils.rethrowIfStrictCallbackMode(options, IllegalStateException())
    CallbackUtils.rethrowIfStrictCallbackMode(options, LinkageError())
    val original = IllegalStateException()
    assertThat(
        assertFailsWith<IllegalStateException> { CallbackUtils.run(options) { throw original } }
      )
      .isSameInstanceAs(original)
  }

  @Test
  fun `strict callback mode wraps checked callback exceptions too`() {
    val original = java.io.IOException()
    val thrown =
      assertFailsWith<SentryCallbackException> {
        CallbackUtils.run(SentryOptions().apply { isStrictCallbackMode = true }) { throw original }
      }
    assertThat(thrown.cause).isSameInstanceAs(original)
  }
}
