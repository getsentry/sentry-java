package io.sentry.compose.navigation3.internal.backstack

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.internal.backstack.HostArgumentSourceTest.Event.BeginList
import io.sentry.compose.navigation3.internal.backstack.HostArgumentSourceTest.Event.BeginMap
import io.sentry.compose.navigation3.internal.backstack.HostArgumentSourceTest.Event.End
import io.sentry.compose.navigation3.internal.backstack.HostArgumentSourceTest.Event.Key
import io.sentry.compose.navigation3.internal.backstack.HostArgumentSourceTest.Event.Scalar
import io.sentry.compose.navigation3.internal.backstack.HostArgumentSourceTest.Event.UncomputedScalar
import java.math.BigDecimal
import java.util.AbstractCollection
import kotlin.test.assertFailsWith
import org.junit.Test

class HostArgumentSourceTest {

  private enum class PrivacyMode {
    PUBLIC,
    PRIVATE,
  }

  private class OpaqueValue {
    override fun toString(): String = "opaque-value"
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

    override fun toString(): String {
      toStringCalls++
      return "counting-value"
    }
  }

  private val unsupportedTypeNames = mutableListOf<String?>()
  private val sink = RecordingArgumentSink()

  // Arguments

  @Test
  fun `writeTo() - writes each argument as its key followed by its value`() {
    val source = hostArgumentSource(mapOf("id" to "123", "page" to 2))

    source.writeTo(sink)

    assertThat(sink.events)
      .containsExactly(Key("id"), Scalar("123"), Key("page"), Scalar(2))
      .inOrder()
  }

  @Test
  fun `writeTo() - writes nothing for empty arguments`() {
    val source = hostArgumentSource(emptyMap<String, Any?>())

    source.writeTo(sink)

    assertThat(sink.events).isEmpty()
  }

  @Test
  fun `writeTo() - writes map keys by toString`() {
    val key =
      object {
        override fun toString(): String = "custom-key"
      }
    val source =
      hostArgumentSource(mapOf(1 to "a", PrivacyMode.PUBLIC to "b", key to "c", null to "d"))

    source.writeTo(sink)

    assertThat(sink.events.filterIsInstance<Key>())
      .containsExactly(Key("1"), Key("PUBLIC"), Key("custom-key"), Key("null"))
      .inOrder()
  }

  // Containers

  @Test
  fun `writeTo() - writes a nested map between beginMap() and end()`() {
    val nested = mapOf(1 to "a")
    val source = hostArgumentSource(mapOf("nested" to nested))

    source.writeTo(sink)

    assertThat(sink.events)
      .containsExactly(Key("nested"), BeginMap(nested), Key("1"), Scalar("a"), End)
      .inOrder()
  }

  @Test
  fun `writeTo() - passes a nested map as its own identity`() {
    val nested = mapOf("k" to "v")
    val source = hostArgumentSource(mapOf("nested" to nested))

    source.writeTo(sink)

    assertThat(sink.events.filterIsInstance<BeginMap>().single().identity).isSameInstanceAs(nested)
  }

  @Test
  fun `writeTo() - writes a collection as a list`() {
    val set = linkedSetOf("a", "b")
    val source = hostArgumentSource(mapOf("set" to set))

    source.writeTo(sink)

    assertThat(sink.events)
      .containsExactly(Key("set"), BeginList(set), Scalar("a"), Scalar("b"), End)
      .inOrder()
  }

  @Test
  fun `writeTo() - passes a collection as its own identity`() {
    val list = listOf("a")
    val source = hostArgumentSource(mapOf("list" to list))

    source.writeTo(sink)

    assertThat(sink.events.filterIsInstance<BeginList>().single().identity).isSameInstanceAs(list)
  }

  @Test
  fun `writeTo() - writes an object array as a list`() {
    val array = arrayOf<Any?>("a", 1)
    val source = hostArgumentSource(mapOf("array" to array))

    source.writeTo(sink)

    assertThat(sink.events)
      .containsExactly(Key("array"), BeginList(array), Scalar("a"), Scalar(1), End)
      .inOrder()
  }

  @Test
  fun `writeTo() - writes primitive arrays as lists of their elements`() {
    val source =
      hostArgumentSource(
        mapOf(
          "booleans" to booleanArrayOf(true),
          "bytes" to byteArrayOf(1),
          "shorts" to shortArrayOf(2),
          "ints" to intArrayOf(3),
          "longs" to longArrayOf(4L),
          "floats" to floatArrayOf(5f),
          "doubles" to doubleArrayOf(6.0),
          "chars" to charArrayOf('7'),
        ),
      )

    source.writeTo(sink)

    assertThat(sink.events.filterIsInstance<Scalar>())
      .containsExactly(
        Scalar(true),
        Scalar(1.toByte()),
        Scalar(2.toShort()),
        Scalar(3),
        Scalar(4L),
        Scalar(5f),
        Scalar(6.0),
        Scalar('7'),
      )
      .inOrder()
    assertThat(sink.events.count { it is BeginList }).isEqualTo(8)
  }

  @Test
  fun `writeTo() - passes a primitive array as its own identity`() {
    val ints = intArrayOf(1, 2)
    val source = hostArgumentSource(mapOf("ints" to ints))

    source.writeTo(sink)

    assertThat(sink.events.filterIsInstance<BeginList>().single().identity).isSameInstanceAs(ints)
  }

  @Test
  fun `writeTo() - writes containers nested in containers recursively`() {
    val inner = listOf(1)
    val outer = mapOf("inner" to inner)
    val source = hostArgumentSource(mapOf("outer" to listOf(outer)))

    source.writeTo(sink)

    assertThat(sink.events.drop(1))
      .containsExactly(
        BeginList(listOf(outer)),
        BeginMap(outer),
        Key("inner"),
        BeginList(inner),
        Scalar(1),
        End,
        End,
        End,
      )
      .inOrder()
  }

  @Test
  fun `writeTo() - doesn't read the size of a collection`() {
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
    val source = hostArgumentSource(mapOf("values" to values))

    source.writeTo(sink)

    assertThat(values.wasSizeRead).isFalse()
  }

  // Scalars

  @Test
  fun `writeTo() - passes null, Boolean, and Number values unchanged`() {
    val bigDecimal = BigDecimal("1.25")
    val source =
      hostArgumentSource(
        mapOf("null" to null, "boolean" to true, "nan" to Double.NaN, "bigDecimal" to bigDecimal),
      )

    source.writeTo(sink)

    assertThat(sink.events.filterIsInstance<Scalar>())
      .containsExactly(Scalar(null), Scalar(true), Scalar(Double.NaN), Scalar(bigDecimal))
      .inOrder()
  }

  @Test
  fun `writeTo() - passes CharSequence and Char values unchanged`() {
    val text = StringBuilder("hello")
    val source = hostArgumentSource(mapOf("text" to text, "char" to 'x'))

    source.writeTo(sink)

    val scalars = sink.events.filterIsInstance<Scalar>()
    assertThat(scalars[0].value).isSameInstanceAs(text)
    assertThat(scalars[1].value).isEqualTo('x')
  }

  @Test
  fun `writeTo() - writes enums by name`() {
    val source = hostArgumentSource(mapOf("mode" to PrivacyMode.PRIVATE))

    source.writeTo(sink)

    assertThat(sink.events).containsExactly(Key("mode"), Scalar("PRIVATE")).inOrder()
  }

  @Test
  fun `writeTo() - doesn't report supported values as unsupported`() {
    val source =
      hostArgumentSource(
        mapOf(
          "string" to "a",
          "char" to 'b',
          "number" to 1,
          "boolean" to false,
          "null" to null,
          "enum" to PrivacyMode.PUBLIC,
          "list" to listOf(1),
          "map" to mapOf("k" to "v"),
          "array" to intArrayOf(1),
        ),
      )

    source.writeTo(sink)

    assertThat(unsupportedTypeNames).isEmpty()
  }

  // Unsupported values

  @Test
  fun `writeTo() - writes an unsupported value by toString`() {
    val source = hostArgumentSource(mapOf("opaque" to OpaqueValue()))

    source.writeTo(sink)

    assertThat(sink.events).containsExactly(Key("opaque"), Scalar("opaque-value")).inOrder()
  }

  @Test
  fun `writeTo() - reports an unsupported value with its simple type name`() {
    val source = hostArgumentSource(mapOf("opaque" to OpaqueValue()))

    source.writeTo(sink)

    assertThat(unsupportedTypeNames).containsExactly("OpaqueValue")
  }

  @Test
  fun `writeTo() - writes an Iterable that isn't a Collection as an unsupported value`() {
    val source = hostArgumentSource(mapOf("iterable" to OpaqueIterable()))

    source.writeTo(sink)

    assertThat(sink.events).containsExactly(Key("iterable"), Scalar("opaque-iterable")).inOrder()
    assertThat(unsupportedTypeNames).containsExactly("OpaqueIterable")
  }

  @Test
  fun `writeTo() - writes a Sequence as an unsupported value`() {
    val source = hostArgumentSource(mapOf("sequence" to OpaqueSequence()))

    source.writeTo(sink)

    assertThat(sink.events).containsExactly(Key("sequence"), Scalar("opaque-sequence")).inOrder()
    assertThat(unsupportedTypeNames).containsExactly("OpaqueSequence")
  }

  @Test
  fun `writeTo() - reports an unsupported value of an anonymous type with a null type name`() {
    val anonymous =
      object {
        override fun toString(): String = "anonymous"
      }
    val source = hostArgumentSource(mapOf("anonymous" to anonymous))

    source.writeTo(sink)

    assertThat(unsupportedTypeNames).containsExactly(null)
  }

  @Test
  fun `writeTo() - doesn't call an unsupported value's toString() or report it until the sink computes the value`() {
    val value = CountingValue()
    val sink = RecordingArgumentSink(computesScalars = false)
    val source = hostArgumentSource(mapOf("counting" to value))

    source.writeTo(sink)

    assertThat(sink.events).containsExactly(Key("counting"), UncomputedScalar).inOrder()
    assertThat(value.toStringCalls).isEqualTo(0)
    assertThat(unsupportedTypeNames).isEmpty()
  }

  // Failures

  @Test
  fun `writeTo() - stops writing when the sink throws`() {
    val failure = DropArguments(ArgumentDropReason.MAX_COUNT, DropArguments.MAX_COUNT_WARNING)
    val sink = RecordingArgumentSink(failOnKey = "second", keyFailure = failure)
    val source = hostArgumentSource(mapOf("first" to 1, "second" to 2, "third" to 3))

    val thrown = assertFailsWith<DropArguments> { source.writeTo(sink) }

    assertThat(thrown).isSameInstanceAs(failure)
    assertThat(sink.events).containsExactly(Key("first"), Scalar(1)).inOrder()
  }

  private fun hostArgumentSource(arguments: Map<*, *>): HostArgumentSource =
    HostArgumentSource(arguments) { unsupportedTypeNames += it }

  sealed interface Event {
    data class Key(val name: String) : Event

    data class Scalar(val value: Any?) : Event

    data class BeginMap(val identity: Any?) : Event

    data class BeginList(val identity: Any?) : Event

    object End : Event {
      override fun toString(): String = "End"
    }

    /** A computed scalar the sink declined to compute. */
    object UncomputedScalar : Event {
      override fun toString(): String = "UncomputedScalar"
    }
  }

  /**
   * Records every call as an [Event], and throws [keyFailure] when asked for the key [failOnKey].
   * With `computesScalars = false`, records each computed scalar without computing it.
   */
  private class RecordingArgumentSink(
    private val failOnKey: String? = null,
    private val keyFailure: RuntimeException? = null,
    private val computesScalars: Boolean = true,
  ) : ArgumentSink {

    val events = mutableListOf<Event>()

    override val failure: DropArguments? = null

    override fun beginMap(identity: Any?) {
      events += BeginMap(identity)
    }

    override fun beginList(identity: Any?) {
      events += BeginList(identity)
    }

    override fun key(name: String, describeDuplicate: (() -> String)?) {
      if (name == failOnKey && keyFailure != null) throw keyFailure
      events += Key(name)
    }

    override fun scalar(value: Any?) {
      events += Scalar(value)
    }

    override fun computedScalar(compute: () -> Any?) {
      if (computesScalars) scalar(compute()) else events += UncomputedScalar
    }

    override fun end() {
      events += End
    }

    override fun reject(reason: ArgumentDropReason, warning: String): Nothing =
      throw UnsupportedOperationException("HostArgumentSource enforces no limits of its own")
  }
}
