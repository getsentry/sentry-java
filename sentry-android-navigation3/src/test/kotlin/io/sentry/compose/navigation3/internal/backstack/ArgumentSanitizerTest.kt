package io.sentry.compose.navigation3.internal.backstack

import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.SentryLevel.WARNING
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_CHARACTERS
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_VALUES
import io.sentry.compose.navigation3.internal.backstack.BackStackConverter.ArgumentSanitizer
import io.sentry.compose.navigation3.internal.backstack.BackStackConverter.SanitizedArguments
import io.sentry.compose.navigation3.internal.backstack.BackStackConverter.WarningState
import kotlin.test.assertFailsWith
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class ArgumentSanitizerTest {

  private class OpaqueValue {
    override fun toString(): String = "opaque-value"
  }

  private class ThrowingValue(val failure: IllegalStateException = IllegalStateException("boom")) {
    @Suppress("ExceptionRaisedInUnexpectedLocation") override fun toString(): String = throw failure
  }

  private val logger = mock<ILogger>()
  private val sanitizer = ArgumentSanitizer(logger, WarningState())

  private val laterArguments = mapOf("id" to "123")

  private companion object {
    private const val UNSUPPORTED_VALUE_WARNING =
      "Nav3 argument type %s is unsupported. Using toString(). Return scalars, enums, maps, " +
        "collections, or arrays from BackStackEntryMapper."
  }

  // Plain maps

  @Test
  fun `sanitizeEntry() - returns the arguments of a plain map as written`() {
    val arguments =
      mapOf("id" to "123", "filters" to mapOf("tags" to listOf("a", "b")), "page" to 2)

    val sanitized = sanitizer.sanitizeEntry(arguments)

    assertThat(sanitized).isEqualTo(SanitizedArguments(arguments))
  }

  @Test
  fun `sanitizeEntry() - returns empty arguments without a drop reason for an empty map`() {
    val sanitized = sanitizer.sanitizeEntry(emptyMap())

    assertThat(sanitized).isEqualTo(SanitizedArguments(emptyMap(), dropReason = null))
  }

  // Unsupported values

  @Test
  fun `sanitizeEntry() - logs the unsupported value warning with the value type`() {
    sanitizer.sanitizeEntry(mapOf("opaque" to OpaqueValue()))

    verify(logger).log(eq(WARNING), eq(UNSUPPORTED_VALUE_WARNING), eq("OpaqueValue"))
  }

  @Test
  fun `the unsupported value warning is logged once across entries sanitized by one sanitizer`() {
    sanitizer.sanitizeEntry(mapOf("opaque" to OpaqueValue(), "alsoOpaque" to OpaqueValue()))
    sanitizer.sanitizeEntry(mapOf("opaque" to OpaqueValue()))

    verify(logger, times(1)).log(eq(WARNING), eq(UNSUPPORTED_VALUE_WARNING), eq("OpaqueValue"))
  }

  // Dropped arguments

  @Test
  fun `sanitizeEntry() - drops arguments over the value budget and logs the value budget warning`() {
    // The list and its elements are one value more than the budget.
    val sanitized = sanitizer.sanitizeEntry(mapOf("values" to List(MAX_ARGUMENT_VALUES) { it }))

    assertThat(sanitized).isEqualTo(SanitizedArguments(emptyMap(), ArgumentDropReason.MAX_COUNT))
    verify(logger).log(eq(WARNING), eq(ArgumentSanitizer.MAX_COUNT_WARNING))
  }

  @Test
  fun `sanitizeEntry() - drops arguments over the character budget and logs the character budget warning`() {
    // The key and the value are one character more than the budget.
    val sanitized = sanitizer.sanitizeEntry(mapOf("v" to "x".repeat(MAX_ARGUMENT_CHARACTERS)))

    assertThat(sanitized)
      .isEqualTo(SanitizedArguments(emptyMap(), ArgumentDropReason.CHARACTER_LIMIT))
    verify(logger).log(eq(WARNING), eq(ArgumentSanitizer.MAX_CHARACTER_WARNING))
  }

  @Test
  fun `an entry whose own map contains itself is dropped without writing its contents again`() {
    // The entry's own map is the cycle check's root, so "self" is rejected as soon as it begins,
    // once the 30 scalars and "self" have spent 31 values. Otherwise its contents would be written
    // once more before the cycle is found, spending another 31 values of the shared budget.
    val selfContaining = mutableMapOf<String, Any?>()
    repeat(30) { selfContaining["k$it"] = it }
    selfContaining["self"] = selfContaining
    // A list and its elements, which spend exactly the values left.
    val rest = mapOf("values" to List(MAX_ARGUMENT_VALUES - 31 - 1) { it })

    val dropped = sanitizer.sanitizeEntry(selfContaining)
    val later = sanitizer.sanitizeEntry(rest)

    assertThat(dropped)
      .isEqualTo(SanitizedArguments(emptyMap(), ArgumentDropReason.INVALID_STRUCTURE))
    assertThat(later).isEqualTo(SanitizedArguments(rest))
  }

  // Budgets shared across entries

  @Test
  fun `entries sanitized by one sanitizer share the value budget`() {
    // Each of the first two entries uses half of the value budget: a list and its elements.
    val half = MAX_ARGUMENT_VALUES / 2
    val first = sanitizer.sanitizeEntry(mapOf("values" to List(half - 1) { it }))
    val second = sanitizer.sanitizeEntry(mapOf("values" to List(half - 1) { it }))
    val third = sanitizer.sanitizeEntry(mapOf("one" to 1))

    assertThat(first.dropReason).isNull()
    assertThat(second.dropReason).isNull()
    assertThat(third).isEqualTo(SanitizedArguments(emptyMap(), ArgumentDropReason.MAX_COUNT))
  }

  @Test
  fun `entries sanitized by one sanitizer share the character budget`() {
    // Each of the first two entries uses half of the character budget: a key and its value.
    val half = MAX_ARGUMENT_CHARACTERS / 2
    val first = sanitizer.sanitizeEntry(mapOf("a" to "x".repeat(half - 1)))
    val second = sanitizer.sanitizeEntry(mapOf("b" to "x".repeat(half - 1)))
    val third = sanitizer.sanitizeEntry(mapOf("c" to null))

    assertThat(first.dropReason).isNull()
    assertThat(second.dropReason).isNull()
    assertThat(third).isEqualTo(SanitizedArguments(emptyMap(), ArgumentDropReason.CHARACTER_LIMIT))
  }

  // Failures

  @Test
  fun `sanitizeEntry() - drops arguments for a sanitization failure when a value throws`() {
    val sanitized = sanitizer.sanitizeEntry(mapOf("ok" to 1, "throwing" to ThrowingValue()))

    assertThat(sanitized)
      .isEqualTo(SanitizedArguments(emptyMap(), ArgumentDropReason.SANITIZATION_FAILED))
  }

  @Test
  fun `sanitizeEntry() - logs the structure warning with the exception when a value throws`() {
    val throwing = ThrowingValue()

    sanitizer.sanitizeEntry(mapOf("throwing" to throwing))

    verify(logger).log(eq(WARNING), eq(ArgumentSanitizer.STRUCTURE_WARNING), eq(throwing.failure))
  }

  @Test
  fun `sanitizeEntry() - rethrows fatal errors thrown during sanitization`() {
    val fatal =
      object {
        override fun toString(): String = throw StackOverflowError("fatal")
      }

    assertFailsWith<StackOverflowError> {
      sanitizer.sanitizeEntry(mapOf("fatal" to fatal))
    }
    verify(logger, never())
      .log(eq(WARNING), eq(ArgumentSanitizer.STRUCTURE_WARNING), any<Throwable>())
  }

  @Test
  fun `a container from an entry that failed sanitization isn't treated as a cycle in later entries`() {
    val holder = mutableListOf<Any?>(ThrowingValue())
    sanitizer.sanitizeEntry(mapOf("holder" to holder))
    holder.clear()

    val sanitized = sanitizer.sanitizeEntry(mapOf("holder" to holder))

    assertThat(sanitized).isEqualTo(SanitizedArguments(mapOf("holder" to emptyList<Any?>())))
  }

  // Later entries after a drop

  @Test
  fun `entries after the value budget is exhausted are dropped without another warning`() {
    sanitizer.sanitizeEntry(mapOf("values" to List(MAX_ARGUMENT_VALUES) { it }))

    val later = sanitizer.sanitizeEntry(laterArguments)

    assertThat(later).isEqualTo(SanitizedArguments(emptyMap(), ArgumentDropReason.MAX_COUNT))
    verify(logger, times(1)).log(eq(WARNING), eq(ArgumentSanitizer.MAX_COUNT_WARNING))
  }

  @Test
  fun `entries after the character budget is exhausted are dropped without another warning`() {
    sanitizer.sanitizeEntry(mapOf("v" to "x".repeat(MAX_ARGUMENT_CHARACTERS)))

    val later = sanitizer.sanitizeEntry(laterArguments)

    assertThat(later).isEqualTo(SanitizedArguments(emptyMap(), ArgumentDropReason.CHARACTER_LIMIT))
    verify(logger, times(1)).log(eq(WARNING), eq(ArgumentSanitizer.MAX_CHARACTER_WARNING))
  }

  @Test
  fun `an entry dropped for invalid structure doesn't drop later entries`() {
    val cyclic = mutableListOf<Any?>()
    cyclic.add(cyclic)
    sanitizer.sanitizeEntry(mapOf("cyclic" to cyclic))

    val later = sanitizer.sanitizeEntry(laterArguments)

    assertThat(later).isEqualTo(SanitizedArguments(laterArguments))
  }

  @Test
  fun `an entry dropped for a duplicate map key doesn't drop later entries`() {
    sanitizer.sanitizeEntry(mapOf("nested" to mapOf(1 to "a", "1" to "b")))

    val later = sanitizer.sanitizeEntry(laterArguments)

    assertThat(later).isEqualTo(SanitizedArguments(laterArguments))
  }

  @Test
  fun `an entry that failed sanitization doesn't drop later entries`() {
    sanitizer.sanitizeEntry(mapOf("throwing" to ThrowingValue()))

    val later = sanitizer.sanitizeEntry(laterArguments)

    assertThat(later).isEqualTo(SanitizedArguments(laterArguments))
  }
}
