package io.sentry.compose.navigation3.internal.backstack

import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.SentryLevel.WARNING
import io.sentry.compose.navigation3.internal.backstack.BackStackConverter.ArgumentSanitizer
import io.sentry.compose.navigation3.internal.backstack.BackStackConverter.SanitizedArguments
import io.sentry.compose.navigation3.internal.backstack.BackStackConverter.WarningState
import io.sentry.compose.navigation3.internal.contract.ArgumentConstraints.MAX_ARGUMENT_CHARACTERS
import io.sentry.compose.navigation3.internal.contract.ArgumentConstraints.MAX_ARGUMENT_DEPTH
import io.sentry.compose.navigation3.internal.contract.ArgumentConstraints.MAX_ARGUMENT_VALUES
import io.sentry.compose.navigation3.internal.contract.ArgumentDropReason
import java.math.BigDecimal
import java.util.AbstractCollection
import kotlin.test.assertFailsWith
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class ArgumentSanitizerTest {

  private class OpaqueValue {
    override fun toString(): String = "opaque-value"
  }

  private enum class PrivacyMode {
    PUBLIC,
    PRIVATE;

    override fun toString(): String = name.lowercase()
  }

  private class UserId(private val value: String) {
    override fun toString(): String = value
  }

  private class OpaqueIterable : Iterable<String> {
    override fun iterator(): Iterator<String> = listOf("a").iterator()

    override fun toString(): String = "opaque-iterable"
  }

  private class OpaqueSequence : Sequence<String> {
    override fun iterator(): Iterator<String> = listOf("a").iterator()

    override fun toString(): String = "opaque-sequence"
  }

  private class CountingValue {
    var toStringCalls = 0
      private set

    override fun toString(): String {
      toStringCalls++
      return "counting-value"
    }
  }

  private class ThrowingValue(val failure: IllegalStateException = IllegalStateException("boom")) {
    @Suppress("ExceptionRaisedInUnexpectedLocation") override fun toString(): String = throw failure
  }

  private val logger = mock<ILogger>()
  private val sanitizer = ArgumentSanitizer(logger, WarningState())

  private val laterArguments = mapOf("id" to "123")

  /** Sanitizes an entry that leaves only [remaining] values of the shared budget. */
  private fun spendValuesExcept(remaining: Int) {
    // A list and its elements.
    val spent =
      sanitizer.sanitizeEntry(mapOf("v" to List(MAX_ARGUMENT_VALUES - remaining - 1) { 0 }))
    check(spent.dropReason == null)
  }

  /** Sanitizes an entry that leaves only [remaining] characters of the shared budget. */
  private fun spendCharactersExcept(remaining: Int) {
    val spent =
      sanitizer.sanitizeEntry(mapOf("" to "x".repeat(MAX_ARGUMENT_CHARACTERS - remaining)))
    check(spent.dropReason == null)
  }

  /** Returns a root map whose [leaf] is nested in [levels] more maps, i.e., at depth levels + 1. */
  private fun nested(levels: Int, leaf: Any?): Map<String, Any?> {
    var value: Any? = leaf
    repeat(levels) { value = mapOf("n" to value) }
    return mapOf("n" to value)
  }

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

  @Test
  fun `sanitizeEntry() - keeps arguments in the order of the map`() {
    val arguments = linkedMapOf("b" to 1, "a" to 2, "c" to 3)

    val sanitized = sanitizer.sanitizeEntry(arguments)

    assertThat(sanitized.values.keys).containsExactly("b", "a", "c").inOrder()
  }

  @Test
  fun `sanitizeEntry() - records map keys by toString()`() {
    val sanitized = sanitizer.sanitizeEntry(mapOf("nested" to mapOf(1 to "a", UserId("u") to "b")))

    assertThat(sanitized.values).isEqualTo(mapOf("nested" to mapOf("1" to "a", "u" to "b")))
  }

  // Containers

  @Test
  fun `sanitizeEntry() - records collections and object arrays as lists`() {
    val sanitized =
      sanitizer.sanitizeEntry(
        mapOf("set" to linkedSetOf("a", "b"), "array" to arrayOf<Any?>(1, "x", null)),
      )

    assertThat(sanitized.values)
      .isEqualTo(mapOf("set" to listOf("a", "b"), "array" to listOf(1, "x", null)))
  }

  @Test
  fun `sanitizeEntry() - records primitive arrays as lists of their elements`() {
    val sanitized =
      sanitizer.sanitizeEntry(
        mapOf(
          "booleans" to booleanArrayOf(true),
          "bytes" to byteArrayOf(1),
          "shorts" to shortArrayOf(2),
          "ints" to intArrayOf(3),
          "longs" to longArrayOf(4),
          "floats" to floatArrayOf(5f),
          "doubles" to doubleArrayOf(6.0),
          "chars" to charArrayOf('c'),
        ),
      )

    assertThat(sanitized.values)
      .isEqualTo(
        mapOf(
          "booleans" to listOf(true),
          "bytes" to listOf(1.toByte()),
          "shorts" to listOf(2.toShort()),
          "ints" to listOf(3),
          "longs" to listOf(4L),
          "floats" to listOf(5f),
          "doubles" to listOf(6.0),
          "chars" to listOf("c"),
        ),
      )
  }

  @Test
  fun `sanitizeEntry() - records a map inside a list`() {
    val sanitized = sanitizer.sanitizeEntry(mapOf("items" to listOf(mapOf(1 to "a"))))

    assertThat(sanitized.values).isEqualTo(mapOf("items" to listOf(mapOf("1" to "a"))))
  }

  @Test
  fun `sanitizeEntry() - doesn't read the size of a collection`() {
    val values =
      object : AbstractCollection<Int>() {
        var wasSizeRead = false

        override val size: Int
          get() {
            wasSizeRead = true
            return Int.MAX_VALUE
          }

        override fun iterator(): MutableIterator<Int> = mutableListOf(1, 2).iterator()
      }

    val sanitized = sanitizer.sanitizeEntry(mapOf("values" to values))

    assertThat(sanitized.values).isEqualTo(mapOf("values" to listOf(1, 2)))
    assertThat(values.wasSizeRead).isFalse()
  }

  @Test
  fun `sanitizeEntry() - records the same container in sibling positions twice`() {
    val shared = listOf(1)

    val sanitized = sanitizer.sanitizeEntry(mapOf("a" to shared, "b" to shared))

    assertThat(sanitized.values).isEqualTo(mapOf("a" to listOf(1), "b" to listOf(1)))
  }

  @Test
  fun `sanitizeEntry() - records the same key in a nested map and its parent`() {
    val sanitized = sanitizer.sanitizeEntry(mapOf("id" to 1, "nested" to mapOf("id" to 2)))

    assertThat(sanitized.values).isEqualTo(mapOf("id" to 1, "nested" to mapOf("id" to 2)))
  }

  // Scalars

  @Test
  fun `sanitizeEntry() - records null, Boolean, and finite Number values unchanged`() {
    val bigDecimal = BigDecimal("1.25")

    val sanitized =
      sanitizer.sanitizeEntry(mapOf("null" to null, "boolean" to true, "big" to bigDecimal))

    assertThat(sanitized.values)
      .isEqualTo(mapOf("null" to null, "boolean" to true, "big" to bigDecimal))
  }

  @Test
  fun `sanitizeEntry() - records CharSequence and Char values as Strings`() {
    val sanitized =
      sanitizer.sanitizeEntry(mapOf("builder" to StringBuilder("text"), "char" to 'c'))

    assertThat(sanitized.values).isEqualTo(mapOf("builder" to "text", "char" to "c"))
  }

  @Test
  fun `sanitizeEntry() - records non-finite Double and Float values as Strings`() {
    val sanitized =
      sanitizer.sanitizeEntry(
        mapOf(
          "nan" to Double.NaN,
          "infinity" to Double.POSITIVE_INFINITY,
          "negativeInfinity" to Float.NEGATIVE_INFINITY,
        ),
      )

    assertThat(sanitized.values)
      .isEqualTo(mapOf("nan" to "NaN", "infinity" to "Infinity", "negativeInfinity" to "-Infinity"))
  }

  @Test
  fun `sanitizeEntry() - records enums by name rather than toString()`() {
    val sanitized = sanitizer.sanitizeEntry(mapOf("mode" to PrivacyMode.PRIVATE))

    assertThat(sanitized.values).isEqualTo(mapOf("mode" to "PRIVATE"))
  }

  // Unsupported values

  @Test
  fun `sanitizeEntry() - records an unsupported value by toString()`() {
    val sanitized = sanitizer.sanitizeEntry(mapOf("opaque" to OpaqueValue()))

    assertThat(sanitized.values).isEqualTo(mapOf("opaque" to "opaque-value"))
  }

  @Test
  fun `sanitizeEntry() - records an Iterable that isn't a Collection and a Sequence as unsupported values`() {
    val sanitized =
      sanitizer.sanitizeEntry(mapOf("iterable" to OpaqueIterable(), "sequence" to OpaqueSequence()))

    assertThat(sanitized.values)
      .isEqualTo(mapOf("iterable" to "opaque-iterable", "sequence" to "opaque-sequence"))
  }

  @Test
  fun `sanitizeEntry() - doesn't log supported values as unsupported`() {
    sanitizer.sanitizeEntry(
      mapOf(
        "string" to "s",
        "number" to 1,
        "enum" to PrivacyMode.PUBLIC,
        "list" to listOf(1),
        "array" to intArrayOf(1),
        "map" to mapOf("k" to "v"),
      ),
    )

    verify(logger, never()).log(eq(WARNING), eq(UNSUPPORTED_VALUE_WARNING), any<String>())
  }

  @Test
  fun `sanitizeEntry() - logs an unsupported value of an anonymous type with a null type name`() {
    val anonymous =
      object {
        override fun toString(): String = "anonymous"
      }

    sanitizer.sanitizeEntry(mapOf("anonymous" to anonymous))

    verify(logger).log(eq(WARNING), eq(UNSUPPORTED_VALUE_WARNING), isNull<String>())
  }

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
  fun `sanitizeEntry() - drops arguments for invalid structure and logs the structure warning`() {
    val cyclic = mutableListOf<Any?>()
    cyclic.add(cyclic)

    val sanitized = sanitizer.sanitizeEntry(mapOf("cyclic" to cyclic))

    assertThat(sanitized)
      .isEqualTo(SanitizedArguments(emptyMap(), ArgumentDropReason.INVALID_STRUCTURE))
    verify(logger).log(eq(WARNING), eq(ArgumentSanitizer.STRUCTURE_WARNING))
  }

  @Test
  fun `sanitizeEntry() - drops arguments for a duplicate map key and logs the warning naming the key types`() {
    val sanitized = sanitizer.sanitizeEntry(mapOf("nested" to mapOf(1 to "a", "1" to "b")))

    assertThat(sanitized)
      .isEqualTo(SanitizedArguments(emptyMap(), ArgumentDropReason.DUPLICATE_KEY))
    verify(logger)
      .log(
        eq(WARNING),
        eq(
          "A Nav3 map key (String) stringifies to the same string as another key (Int). " +
            "Dropping this entry's arguments.",
        ),
      )
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

  @Test
  fun `sanitizeEntry() - describes a duplicate key by the types of the colliding keys rather than their values`() {
    sanitizer.sanitizeEntry(
      mapOf("nested" to linkedMapOf(UserId("ada@example.com") to "a", "ada@example.com" to "b")),
    )

    verify(logger)
      .log(
        eq(WARNING),
        eq(
          "A Nav3 map key (String) stringifies to the same string as another key (UserId). " +
            "Dropping this entry's arguments.",
        ),
      )
  }

  @Test
  fun `sanitizeEntry() - describes a null or anonymous map key that collides with another key as null`() {
    val anonymous =
      object {
        override fun toString(): String = "id"
      }

    sanitizer.sanitizeEntry(mapOf("nested" to linkedMapOf("null" to "a", null to "b")))
    sanitizer.sanitizeEntry(mapOf("nested" to linkedMapOf("id" to "a", anonymous to "b")))

    verify(logger, times(2))
      .log(
        eq(WARNING),
        eq(
          "A Nav3 map key (null) stringifies to the same string as another key (String). " +
            "Dropping this entry's arguments.",
        ),
      )
  }

  @Test
  fun `sanitizeEntry() - drops arguments for a duplicate key whose value is a container`() {
    val withMap =
      sanitizer.sanitizeEntry(mapOf("nested" to linkedMapOf(1 to "a", "1" to mapOf("k" to "v"))))
    val withList =
      sanitizer.sanitizeEntry(mapOf("nested" to linkedMapOf(1 to "a", "1" to listOf(1))))

    assertThat(withMap.dropReason).isEqualTo(ArgumentDropReason.DUPLICATE_KEY)
    assertThat(withList.dropReason).isEqualTo(ArgumentDropReason.DUPLICATE_KEY)
  }

  @Test
  fun `sanitizeEntry() - drops arguments for a duplicate key, not the value limit, with no values left`() {
    // Enough for the nested map and "a" only.
    spendValuesExcept(remaining = 2)

    val sanitized = sanitizer.sanitizeEntry(mapOf("nested" to linkedMapOf(1 to "a", "1" to "b")))

    assertThat(sanitized.dropReason).isEqualTo(ArgumentDropReason.DUPLICATE_KEY)
  }

  @Test
  fun `sanitizeEntry() - doesn't spend a value for a duplicate key`() {
    // Enough for the nested map, "a", and the later entry's value.
    spendValuesExcept(remaining = 3)
    sanitizer.sanitizeEntry(mapOf("nested" to linkedMapOf(1 to "a", "1" to "b")))

    val later = sanitizer.sanitizeEntry(mapOf("one" to 1))

    assertThat(later).isEqualTo(SanitizedArguments(mapOf("one" to 1)))
  }

  @Test
  fun `sanitizeEntry() - doesn't convert a value under a duplicate key`() {
    val value = CountingValue()

    sanitizer.sanitizeEntry(mapOf("nested" to linkedMapOf(1 to "a", "1" to value)))

    assertThat(value.toStringCalls).isEqualTo(0)
  }

  // Depth

  @Test
  fun `sanitizeEntry() - records a scalar and a container at the maximum depth`() {
    val scalarAtMax = nested(MAX_ARGUMENT_DEPTH - 1, leaf = 1)
    val containerAtMax = nested(MAX_ARGUMENT_DEPTH - 1, leaf = emptyList<Any?>())

    val scalar = sanitizer.sanitizeEntry(scalarAtMax)
    val container = sanitizer.sanitizeEntry(containerAtMax)

    assertThat(scalar).isEqualTo(SanitizedArguments(scalarAtMax))
    assertThat(container).isEqualTo(SanitizedArguments(containerAtMax))
  }

  @Test
  fun `sanitizeEntry() - drops arguments for invalid structure past the maximum depth`() {
    val sanitized = sanitizer.sanitizeEntry(nested(MAX_ARGUMENT_DEPTH, leaf = 1))

    assertThat(sanitized)
      .isEqualTo(SanitizedArguments(emptyMap(), ArgumentDropReason.INVALID_STRUCTURE))
  }

  @Test
  fun `sanitizeEntry() - doesn't convert a value past the maximum depth`() {
    val value = CountingValue()

    sanitizer.sanitizeEntry(nested(MAX_ARGUMENT_DEPTH, leaf = value))

    assertThat(value.toStringCalls).isEqualTo(0)
  }

  @Test
  fun `sanitizeEntry() - counts depth only through containers a value is nested in`() {
    // Each sibling reaches the maximum depth on its own, which together they'd exceed.
    val arguments =
      mapOf(
        "first" to nested(MAX_ARGUMENT_DEPTH - 2, leaf = 1),
        "second" to nested(MAX_ARGUMENT_DEPTH - 2, leaf = 2),
      )

    val sanitized = sanitizer.sanitizeEntry(arguments)

    assertThat(sanitized).isEqualTo(SanitizedArguments(arguments))
  }

  // Cycles

  @Test
  fun `sanitizeEntry() - drops arguments for invalid structure when a map contains itself`() {
    val cyclic = mutableMapOf<String, Any?>()
    cyclic["self"] = cyclic

    val sanitized = sanitizer.sanitizeEntry(mapOf("cyclic" to cyclic))

    assertThat(sanitized)
      .isEqualTo(SanitizedArguments(emptyMap(), ArgumentDropReason.INVALID_STRUCTURE))
  }

  @Test
  fun `sanitizeEntry() - drops arguments for invalid structure when an array contains itself`() {
    val cyclic = arrayOfNulls<Any?>(1)
    cyclic[0] = cyclic

    val sanitized = sanitizer.sanitizeEntry(mapOf("cyclic" to cyclic))

    assertThat(sanitized)
      .isEqualTo(SanitizedArguments(emptyMap(), ArgumentDropReason.INVALID_STRUCTURE))
  }

  // Value and character budgets

  @Test
  fun `sanitizeEntry() - spends one value per scalar and container but none for the root map or keys`() {
    // "list" and its 2 elements, and "scalar": 4 values.
    sanitizer.sanitizeEntry(mapOf("list" to listOf(1, 2), "scalar" to 3))
    val rest = mapOf("values" to List(MAX_ARGUMENT_VALUES - 4 - 1) { it })

    val later = sanitizer.sanitizeEntry(rest)

    assertThat(later).isEqualTo(SanitizedArguments(rest))
  }

  @Test
  fun `sanitizeEntry() - doesn't convert a value over the value budget`() {
    spendValuesExcept(remaining = 0)
    val value = CountingValue()

    // A fresh sanitizer would record it, so the shared budget is what stops it.
    val sanitized = sanitizer.sanitizeEntry(mapOf("counting" to value))

    assertThat(sanitized.dropReason).isEqualTo(ArgumentDropReason.MAX_COUNT)
    assertThat(value.toStringCalls).isEqualTo(0)
    verify(logger, never()).log(eq(WARNING), eq(UNSUPPORTED_VALUE_WARNING), any<String>())
  }

  @Test
  fun `sanitizeEntry() - records text exactly as long as the remaining characters`() {
    val sanitized = sanitizer.sanitizeEntry(mapOf("" to "x".repeat(MAX_ARGUMENT_CHARACTERS)))

    assertThat(sanitized.dropReason).isNull()
  }

  @Test
  fun `sanitizeEntry() - spends the characters of keys and of every text value`() {
    // The values spend "cd" + "NaN" + "PUBLIC" + "e" + "opaque-value" = 2 + 3 + 6 + 1 + 12 = 24
    // characters, and the keys 2 + 0 + 1 + 2 + 3 = 8, for 32 in all.
    sanitizer.sanitizeEntry(
      linkedMapOf(
        "ab" to "cd",
        "" to Double.NaN,
        " " to PrivacyMode.PUBLIC,
        "  " to 'e',
        "   " to OpaqueValue(),
      ),
    )
    val rest = mapOf("" to "x".repeat(MAX_ARGUMENT_CHARACTERS - 32))

    val later = sanitizer.sanitizeEntry(rest)
    val overflowing = sanitizer.sanitizeEntry(mapOf("" to "y"))

    assertThat(later).isEqualTo(SanitizedArguments(rest))
    assertThat(overflowing.dropReason).isEqualTo(ArgumentDropReason.CHARACTER_LIMIT)
  }

  @Test
  fun `sanitizeEntry() - doesn't spend characters for finite numbers, Booleans, or null`() {
    sanitizer.sanitizeEntry(mapOf("" to 12345, " " to true, "  " to null))
    // The keys above spend 0 + 1 + 2 = 3 characters.
    val rest = mapOf("" to "x".repeat(MAX_ARGUMENT_CHARACTERS - 3))

    val later = sanitizer.sanitizeEntry(rest)
    val overflowing = sanitizer.sanitizeEntry(mapOf("" to "y"))

    assertThat(later).isEqualTo(SanitizedArguments(rest))
    assertThat(overflowing.dropReason).isEqualTo(ArgumentDropReason.CHARACTER_LIMIT)
  }

  @Test
  fun `sanitizeEntry() - doesn't convert text whose reported length doesn't fit`() {
    spendCharactersExcept(remaining = 1)
    val text = CountingText(length = 2, text = "ab")

    val sanitized = sanitizer.sanitizeEntry(mapOf("" to text))

    assertThat(sanitized.dropReason).isEqualTo(ArgumentDropReason.CHARACTER_LIMIT)
    assertThat(text.toStringCalls).isEqualTo(0)
  }

  @Test
  fun `sanitizeEntry() - drops text longer than its reported length when its text doesn't fit`() {
    spendCharactersExcept(remaining = 1)

    val sanitized = sanitizer.sanitizeEntry(mapOf("" to CountingText(length = 1, text = "ab")))

    assertThat(sanitized.dropReason).isEqualTo(ArgumentDropReason.CHARACTER_LIMIT)
  }

  @Test
  fun `sanitizeEntry() - spends the characters of text rather than its reported length`() {
    spendCharactersExcept(remaining = 2)
    sanitizer.sanitizeEntry(mapOf("" to CountingText(length = 0, text = "ab")))

    val later = sanitizer.sanitizeEntry(mapOf("" to "c"))

    assertThat(later.dropReason).isEqualTo(ArgumentDropReason.CHARACTER_LIMIT)
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
  fun `sanitizeEntry() - logs a warning with the exception when a value throws`() {
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

/**
 * Host app text that reports [length], which may differ from [text]'s, and counts its conversions
 * to a `String` in [toStringCalls].
 */
internal class CountingText(override val length: Int, private val text: String) : CharSequence {
  var toStringCalls = 0
    private set

  override fun get(index: Int): Char = text[index]

  override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
    text.subSequence(startIndex, endIndex)

  override fun toString(): String {
    toStringCalls++
    return text
  }
}
