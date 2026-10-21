package io.sentry.vendor

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SentryMathTest {
  @Test
  fun `floorDiv matches Java Math floorDiv`() {
    values.forEach { x ->
      divisors.forEach { y -> assertThat(SentryMath.floorDiv(x, y)).isEqualTo(Math.floorDiv(x, y)) }
    }
  }

  @Test
  fun `floorMod matches Java Math floorMod`() {
    values.forEach { x ->
      divisors.forEach { y -> assertThat(SentryMath.floorMod(x, y)).isEqualTo(Math.floorMod(x, y)) }
    }
  }

  @Test
  fun `floorDiv throws when dividing by zero`() {
    assertFailsWith<ArithmeticException> { SentryMath.floorDiv(1L, 0L) }
  }

  @Test
  fun `floorMod throws when dividing by zero`() {
    assertFailsWith<ArithmeticException> { SentryMath.floorMod(1L, 0L) }
  }

  private companion object {
    val values =
      listOf(
        Long.MIN_VALUE,
        -86_400_001L,
        -86_400_000L,
        -86_399_999L,
        -401L,
        -400L,
        -399L,
        -2L,
        -1L,
        0L,
        1L,
        2L,
        399L,
        400L,
        401L,
        86_399_999L,
        86_400_000L,
        86_400_001L,
        Long.MAX_VALUE,
      )

    val divisors = listOf(Long.MIN_VALUE, -146_097L, -400L, -1L, 1L, 400L, 146_097L, Long.MAX_VALUE)
  }
}
