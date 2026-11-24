package io.sentry.compose.navigation3.internal.backstack.kotlinx

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.internal.backstack.ArgumentBudget
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_CHARACTERS
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_DEPTH
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_VALUES
import io.sentry.compose.navigation3.internal.backstack.ArgumentDropReason
import io.sentry.compose.navigation3.internal.backstack.CountingText
import io.sentry.compose.navigation3.internal.backstack.DropArguments
import io.sentry.compose.navigation3.internal.backstack.TreeArgumentWriter
import io.sentry.compose.navigation3.internal.backstack.budgetWithValuesRemaining
import kotlin.jvm.JvmInline
import kotlin.test.assertFailsWith
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.junit.Test

class KotlinxPropertiesEncoderLimitsTest {

  @Serializable
  class SelfReferencing {
    var next: SelfReferencing? = null
  }

  /** Nests a polymorphic and a value class serializer at every other level. */
  @Serializable @JvmInline value class LayerRef(val layer: Layer)

  @Serializable
  sealed interface Layer {
    @Serializable @SerialName("node") data class Node(val child: LayerRef) : Layer

    @Serializable @SerialName("leaf") data class Leaf(val value: String) : Layer
  }

  /**
   * Encodes a link with [LinkSerializer], and if that throws [DropArguments], records it and
   * encodes a fallback with [recover] instead.
   */
  internal class RecoveringLinkSerializer(private val recover: (Encoder) -> Unit) :
    KSerializer<Link> {
    var caught: DropArguments? = null

    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("test.RecoveringLink", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Link) {
      try {
        encoder.encodeSerializableValue(LinkSerializer, value)
      } catch (e: DropArguments) {
        caught = e
        recover(encoder)
      }
    }

    override fun deserialize(decoder: Decoder): Link = Link(decoder.decodeString())
  }

  /** A value that [CatchingWriteSerializer] encodes. */
  private class Code(val code: String)

  /** Writes nothing, counting its calls in [calls]. */
  private class WritesNothingSerializer : KSerializer<String> {
    var calls = 0

    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("test.WritesNothing", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) {
      calls++
    }

    override fun deserialize(decoder: Decoder): String = throw UnsupportedOperationException()
  }

  /**
   * Writes the code, recording a [DropArguments] that throws in [caught], then encodes a fallback
   * with [fallback].
   */
  private class CatchingWriteSerializer(private val fallback: KSerializer<String>) :
    KSerializer<Code> {
    var caught: DropArguments? = null

    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("test.CatchingWrite", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Code) {
      try {
        encoder.encodeString(value.code)
      } catch (e: DropArguments) {
        caught = e
      }
      encoder.encodeSerializableValue(fallback, "fallback")
    }

    override fun deserialize(decoder: Decoder): Code = throw UnsupportedOperationException()
  }

  private val textKeyedMapSerializer =
    MapSerializer(PolymorphicSerializer(CharSequence::class), Int.serializer())

  // Character limit

  @Test
  fun `encode() - encodes an entry with the maximum number of characters, counting property names`() {
    // The Box's property name and its value together are the maximum.
    val value = "x".repeat(MAX_ARGUMENT_CHARACTERS - "value".length)
    val (serializer, entry) = boxed(depth = 1, value = value, String.serializer())

    val encoded = encodeToMap(serializer, entry)

    assertThat(encoded).isEqualTo(mapOf("value" to value))
  }

  @Test
  fun `encode() - drops the arguments for the character limit when property names put an entry one character over`() {
    val value = "x".repeat(MAX_ARGUMENT_CHARACTERS - "value".length + 1)
    val (serializer, entry) = boxed(depth = 1, value = value, String.serializer())

    val exception = assertFailsWith<DropArguments> { encodeToMap(serializer, entry) }

    assertThat(exception.reason).isEqualTo(ArgumentDropReason.CHARACTER_LIMIT)
    assertThat(exception.warning).isEqualTo(DropArguments.MAX_CHARACTER_WARNING)
  }

  @Test
  fun `encode() - encodes a CharSequence with the maximum number of characters, counting property names`() {
    val text = "x".repeat(MAX_ARGUMENT_CHARACTERS - "value".length)
    val (serializer, entry) =
      boxed(depth = 1, value = StringBuilder(text), PolymorphicSerializer(CharSequence::class))

    val encoded = encodeToMap(serializer, entry)

    assertThat(encoded).isEqualTo(mapOf("value" to text))
  }

  @Test
  fun `encode() - drops the arguments for the character limit when a CharSequence puts an entry one character over`() {
    val text = "x".repeat(MAX_ARGUMENT_CHARACTERS - "value".length + 1)
    val (serializer, entry) =
      boxed(depth = 1, value = StringBuilder(text), PolymorphicSerializer(CharSequence::class))

    val exception = assertFailsWith<DropArguments> { encodeToMap(serializer, entry) }

    assertThat(exception.reason).isEqualTo(ArgumentDropReason.CHARACTER_LIMIT)
    assertThat(exception.warning).isEqualTo(DropArguments.MAX_CHARACTER_WARNING)
  }

  @Test
  fun `encode() - encodes a map key of the maximum number of characters`() {
    // A top-level map has no property names, and Int values, so the key is the only text counted.
    val value = mapOf("k".repeat(MAX_ARGUMENT_CHARACTERS) to 1)
    val (serializer, entry) =
      boxed(depth = 0, value = value, MapSerializer(String.serializer(), Int.serializer()))

    val encoded = encodeToMap(serializer, entry)

    assertThat(encoded).isEqualTo(value)
  }

  @Test
  fun `encode() - drops the arguments for the character limit when a map key is too long`() {
    val (serializer, entry) =
      boxed(
        depth = 0,
        value = mapOf("k".repeat(MAX_ARGUMENT_CHARACTERS + 1) to 1),
        MapSerializer(String.serializer(), Int.serializer()),
      )

    val exception = assertFailsWith<DropArguments> { encodeToMap(serializer, entry) }

    assertThat(exception.reason).isEqualTo(ArgumentDropReason.CHARACTER_LIMIT)
    assertThat(exception.warning).isEqualTo(DropArguments.MAX_CHARACTER_WARNING)
  }

  @Test
  fun `encode() - encodes a CharSequence map key of the maximum number of characters`() {
    val key =
      CountingText(length = MAX_ARGUMENT_CHARACTERS, text = "k".repeat(MAX_ARGUMENT_CHARACTERS))
    val (serializer, entry) =
      boxed(depth = 0, value = mapOf<CharSequence, Int>(key to 1), textKeyedMapSerializer)

    val encoded = encodeToMap(serializer, entry)

    assertThat(encoded).isEqualTo(mapOf("k".repeat(MAX_ARGUMENT_CHARACTERS) to 1))
  }

  @Test
  fun `encode() - drops for the character limit without converting a CharSequence map key that's too long`() {
    val key = CountingText(length = MAX_ARGUMENT_CHARACTERS + 1, text = "short")
    val (serializer, entry) =
      boxed(depth = 0, value = mapOf<CharSequence, Int>(key to 1), textKeyedMapSerializer)

    val exception = assertFailsWith<DropArguments> { encodeToMap(serializer, entry) }

    assertThat(exception.reason).isEqualTo(ArgumentDropReason.CHARACTER_LIMIT)
    assertThat(exception.warning).isEqualTo(DropArguments.MAX_CHARACTER_WARNING)
    assertThat(key.toStringCalls).isEqualTo(0)
  }

  @Test
  fun `encode() - drops for the character limit without converting a CharSequence property that's too long`() {
    val text = CountingText(length = MAX_ARGUMENT_CHARACTERS - "value".length + 1, text = "short")
    val (serializer, entry) =
      boxed(depth = 1, value = text, PolymorphicSerializer(CharSequence::class))

    val exception = assertFailsWith<DropArguments> { encodeToMap(serializer, entry) }

    assertThat(exception.reason).isEqualTo(ArgumentDropReason.CHARACTER_LIMIT)
    assertThat(text.toStringCalls).isEqualTo(0)
  }

  // Depth limit

  @Test
  fun `encode() - encodes a value at the maximum depth`() {
    val (serializer, entry) = boxed(depth = MAX_ARGUMENT_DEPTH, value = "leaf", String.serializer())

    val encoded = encodeToMap(serializer, entry)

    assertThat(encoded).isEqualTo(boxedArguments(depth = MAX_ARGUMENT_DEPTH, value = "leaf"))
  }

  @Test
  fun `encode() - encodes polymorphic and value class properties at the maximum depth`() {
    // The leaf's value sits at depth 2, and each node nests its child two levels deeper.
    val nodes = (MAX_ARGUMENT_DEPTH - 2) / 2
    var layer: Layer = Layer.Leaf("leaf")
    repeat(nodes) { layer = Layer.Node(LayerRef(layer)) }

    val encoded = encodeToMap(LayerRef.serializer(), LayerRef(layer))

    var expected: Map<String, Any?> = mapOf("type" to "leaf", "value" to mapOf("value" to "leaf"))
    repeat(nodes) { expected = mapOf("type" to "node", "value" to mapOf("child" to expected)) }
    assertThat(encoded).isEqualTo(expected)
  }

  @Test
  fun `encode() - drops the arguments for invalid structure when a value is nested past the maximum depth`() {
    val (serializer, entry) =
      boxed(depth = MAX_ARGUMENT_DEPTH + 1, value = "leaf", String.serializer())

    val exception = assertFailsWith<DropArguments> { encodeToMap(serializer, entry) }

    assertThat(exception.reason).isEqualTo(ArgumentDropReason.INVALID_STRUCTURE)
    assertThat(exception.warning).isEqualTo(DropArguments.STRUCTURE_WARNING)
  }

  @Test
  fun `encode() - adds one level of depth for a list, so an empty list fits at the maximum depth`() {
    val (serializer, entry) =
      boxed(depth = MAX_ARGUMENT_DEPTH, value = emptyList(), ListSerializer(String.serializer()))

    val encoded = encodeToMap(serializer, entry)

    assertThat(encoded)
      .isEqualTo(boxedArguments(depth = MAX_ARGUMENT_DEPTH, value = emptyList<String>()))
  }

  @Test
  fun `encode() - adds one level of depth for a list, so a list's elements can't be past the maximum depth`() {
    val (serializer, entry) =
      boxed(depth = MAX_ARGUMENT_DEPTH, value = listOf("a"), ListSerializer(String.serializer()))

    val exception = assertFailsWith<DropArguments> { encodeToMap(serializer, entry) }

    assertThat(exception.reason).isEqualTo(ArgumentDropReason.INVALID_STRUCTURE)
    assertThat(exception.warning).isEqualTo(DropArguments.STRUCTURE_WARNING)
  }

  @Test
  fun `encode() - adds one level of depth for a map, so a map's values can't be past the maximum depth`() {
    val (serializer, entry) =
      boxed(
        depth = MAX_ARGUMENT_DEPTH,
        value = mapOf("a" to 1),
        MapSerializer(String.serializer(), Int.serializer()),
      )

    val exception = assertFailsWith<DropArguments> { encodeToMap(serializer, entry) }

    assertThat(exception.reason).isEqualTo(ArgumentDropReason.INVALID_STRUCTURE)
    assertThat(exception.warning).isEqualTo(DropArguments.STRUCTURE_WARNING)
  }

  @Test
  fun `encode() - adds one level of depth for an object, so an object without properties fits at the maximum depth`() {
    val (serializer, entry) = boxed(depth = MAX_ARGUMENT_DEPTH, value = Home, Home.serializer())

    val encoded = encodeToMap(serializer, entry)

    assertThat(encoded)
      .isEqualTo(boxedArguments(depth = MAX_ARGUMENT_DEPTH, value = emptyMap<String, Any?>()))
  }

  @Test
  fun `encode() - stops at the depth limit when an entry references itself`() {
    val entry = SelfReferencing().apply { next = this }

    val exception =
      assertFailsWith<DropArguments> { encodeToMap(SelfReferencing.serializer(), entry) }

    assertThat(exception.reason).isEqualTo(ArgumentDropReason.INVALID_STRUCTURE)
    assertThat(exception.warning).isEqualTo(DropArguments.STRUCTURE_WARNING)
  }

  // Value limit

  @Test
  fun `encode() - encodes an entry with the maximum number of values`() {
    // The list property and its elements are the maximum.
    val values = List(MAX_ARGUMENT_VALUES - 1) { it }
    val (serializer, entry) = boxed(depth = 1, value = values, ListSerializer(Int.serializer()))

    val encoded = encodeToMap(serializer, entry)

    assertThat(encoded).isEqualTo(mapOf("value" to values))
  }

  @Test
  fun `encode() - drops the arguments for count when an entry has one value too many`() {
    val (serializer, entry) =
      boxed(depth = 1, value = List(MAX_ARGUMENT_VALUES) { it }, ListSerializer(Int.serializer()))

    val exception = assertFailsWith<DropArguments> { encodeToMap(serializer, entry) }

    assertThat(exception.reason).isEqualTo(ArgumentDropReason.MAX_COUNT)
    assertThat(exception.warning).isEqualTo(DropArguments.MAX_COUNT_WARNING)
  }

  @Test
  fun `encode() - doesn't count map keys as values`() {
    // The map property and its values are the maximum; counting the keys would exceed it.
    val values = (0 until MAX_ARGUMENT_VALUES - 1).associate { "item$it" to it }
    val (serializer, entry) =
      boxed(depth = 1, value = values, MapSerializer(String.serializer(), Int.serializer()))

    val encoded = encodeToMap(serializer, entry)

    assertThat(encoded).isEqualTo(mapOf("value" to values))
  }

  @Test
  fun `encode() - drops the arguments for count when a map has one value too many`() {
    val (serializer, entry) =
      boxed(
        depth = 1,
        value = (0 until MAX_ARGUMENT_VALUES).associate { "item$it" to it },
        MapSerializer(String.serializer(), Int.serializer()),
      )

    val exception = assertFailsWith<DropArguments> { encodeToMap(serializer, entry) }

    assertThat(exception.reason).isEqualTo(ArgumentDropReason.MAX_COUNT)
    assertThat(exception.warning).isEqualTo(DropArguments.MAX_COUNT_WARNING)
  }

  // Serializer nesting limit

  @Test
  fun `encode() - encodes custom serializers nested up to the maximum number of calls`() {
    // The top-level serializer is the first call, and each next link nests one more.
    val entry = linked(length = KotlinxPropertiesEncoder.MAX_SERIALIZER_NESTING)

    val encoded = encodeToMap(LinkSerializer, entry)

    assertThat(encoded).isEqualTo(mapOf("value" to "leaf"))
  }

  @Test
  fun `encode() - counts only nested serializer calls toward the nesting limit`() {
    // Each element's serializer call returns before the next element's begins.
    val values = List(KotlinxPropertiesEncoder.MAX_SERIALIZER_NESTING + 1) { it }

    val encoded = encodeToMap(ListSerializer(Int.serializer()), values)

    assertThat(encoded).isEqualTo(mapOf(KotlinxPropertiesEncoder.VALUE_KEY to values))
  }

  @Test
  fun `encode() - drops the arguments for invalid structure when custom serializers nest too many calls deep`() {
    val entry = linked(length = KotlinxPropertiesEncoder.MAX_SERIALIZER_NESTING + 1)

    val exception = assertFailsWith<DropArguments> { encodeToMap(LinkSerializer, entry) }

    assertThat(exception.reason).isEqualTo(ArgumentDropReason.INVALID_STRUCTURE)
    assertThat(exception.warning).isEqualTo(DropArguments.STRUCTURE_WARNING)
  }

  @Test
  fun `encode() - drops the arguments for invalid structure when a custom serializer recurses through a cycle`() {
    val entry = Link("leaf").apply { next = this }

    val exception = assertFailsWith<DropArguments> { encodeToMap(LinkSerializer, entry) }

    assertThat(exception.reason).isEqualTo(ArgumentDropReason.INVALID_STRUCTURE)
    assertThat(exception.warning).isEqualTo(DropArguments.STRUCTURE_WARNING)
  }

  @Test
  fun `encode() - records the nesting failure on the sink`() {
    val sink = TreeArgumentWriter(ArgumentBudget())
    val entry = linked(length = KotlinxPropertiesEncoder.MAX_SERIALIZER_NESTING + 1)

    val exception = assertFailsWith<DropArguments> { encodeInto(LinkSerializer, entry, sink) }

    assertThat(sink.failure).isSameInstanceAs(exception)
  }

  @Test
  fun `encode() - keeps failing when a serializer catches the nesting failure and calls another serializer`() {
    // The recovering serializer is the first call, so its link chain nests one call too many.
    val serializer = RecoveringLinkSerializer {
      it.encodeSerializableValue(String.serializer(), "fallback")
    }
    val entry = linked(length = KotlinxPropertiesEncoder.MAX_SERIALIZER_NESTING)

    val exception = assertFailsWith<DropArguments> { encodeToMap(serializer, entry) }

    assertThat(exception).isSameInstanceAs(serializer.caught)
    assertThat(exception.reason).isEqualTo(ArgumentDropReason.INVALID_STRUCTURE)
    assertThat(exception.warning).isEqualTo(DropArguments.STRUCTURE_WARNING)
  }

  @Test
  fun `encode() - throws the caught nesting failure when a serializer catches it and writes a value directly`() {
    val serializer = RecoveringLinkSerializer { it.encodeString("fallback") }
    val entry = linked(length = KotlinxPropertiesEncoder.MAX_SERIALIZER_NESTING)

    val exception = assertFailsWith<DropArguments> { encodeToMap(serializer, entry) }

    assertThat(exception).isSameInstanceAs(serializer.caught)
    assertThat(exception.reason).isEqualTo(ArgumentDropReason.INVALID_STRUCTURE)
    assertThat(exception.warning).isEqualTo(DropArguments.STRUCTURE_WARNING)
  }

  @Test
  fun `encode() - keeps the nesting failure on the sink when a serializer catches it and returns`() {
    val sink = TreeArgumentWriter(ArgumentBudget())
    val serializer = RecoveringLinkSerializer {}
    val entry = linked(length = KotlinxPropertiesEncoder.MAX_SERIALIZER_NESTING)

    encodeInto(serializer, entry, sink)

    val exception = assertFailsWith<DropArguments> { sink.argumentsOrThrow() }
    assertThat(exception).isSameInstanceAs(serializer.caught)
    assertThat(exception.reason).isEqualTo(ArgumentDropReason.INVALID_STRUCTURE)
  }

  @Test
  fun `encode() - propagates a serializer's exception in place of the nesting failure, which the sink keeps`() {
    val error = SerializationException("replaced")
    val sink = TreeArgumentWriter(ArgumentBudget())
    val serializer = RecoveringLinkSerializer { throw error }
    val entry = linked(length = KotlinxPropertiesEncoder.MAX_SERIALIZER_NESTING)

    val thrown = assertFailsWith<SerializationException> { encodeInto(serializer, entry, sink) }

    assertThat(thrown).isSameInstanceAs(error)
    assertThat(sink.failure).isSameInstanceAs(serializer.caught)
    assertThat(sink.failure?.reason).isEqualTo(ArgumentDropReason.INVALID_STRUCTURE)
  }

  @Test
  fun `encode() - doesn't run another serializer after a serializer catches the sink's broken limit`() {
    val fallback = WritesNothingSerializer()
    val serializer = CatchingWriteSerializer(fallback)
    val sink = TreeArgumentWriter(budgetWithValuesRemaining(0))

    val exception = assertFailsWith<DropArguments> { encodeInto(serializer, Code("x"), sink) }

    assertThat(exception).isSameInstanceAs(serializer.caught)
    assertThat(exception.reason).isEqualTo(ArgumentDropReason.MAX_COUNT)
    assertThat(fallback.calls).isEqualTo(0)
  }
}
