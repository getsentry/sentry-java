package io.sentry.compose.navigation3.internal.backstack.kotlinx

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_CHARACTERS
import io.sentry.compose.navigation3.internal.backstack.ArgumentDropReason
import io.sentry.compose.navigation3.internal.backstack.DropArguments
import io.sentry.compose.navigation3.internal.backstack.FailingText
import io.sentry.compose.navigation3.internal.backstack.TreeArgumentWriter
import io.sentry.compose.navigation3.internal.backstack.budgetWithValuesRemaining
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxPropertiesEncoder.UNENCODABLE_SUBSTITUTE
import io.sentry.util.UrlUtils
import kotlin.test.assertFailsWith
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.junit.Test

class KotlinxPropertiesEncoderUnencodableTest {

  @Serializable
  internal data class WithNullableContextual(val id: String, @Contextual val other: Unregistered?)

  @Serializable internal data class WithContextualList(val items: List<@Contextual Unregistered?>)

  @Serializable
  internal data class WithContextualMapValues(val byId: Map<String, @Contextual Unregistered?>)

  @Serializable
  internal data class WithContextualMapKeys(val byOther: Map<@Contextual Unregistered, Int>)

  @Serializable internal data class Inner(val name: String, @Contextual val other: Unregistered)

  @Serializable
  internal data class WithNestedContextual(val id: String, val inner: Inner, val page: Int)

  @Serializable
  internal data class WithContextualSecret(val id: String, @Contextual val authToken: Unregistered)

  @Serializable internal data class WithTextList(val labels: List<CharSequence>)

  @Serializable internal data class WithTextMapValues(val byId: Map<String, CharSequence>)

  @Serializable internal data class WithTextMapKeys(val countsByLabel: Map<CharSequence, Int>)

  /** Places [value] between two properties that encode, so its neighbors on both sides show. */
  @Serializable data class Labeled<T>(val id: String, val value: T, val page: Int)

  /** A map key whose descriptor claims a string, but whose serializer begins a structure. */
  @Serializable(with = StructureWritingKeySerializer::class)
  data class StructureWritingKey(val id: String)

  object StructureWritingKeySerializer : KSerializer<StructureWritingKey> {
    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("custom.StructureWritingKey", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: StructureWritingKey) {
      encoder.encodeSerializableValue(Detail.serializer(), Detail(value.id))
    }

    override fun deserialize(decoder: Decoder): StructureWritingKey =
      throw UnsupportedOperationException()
  }

  @Serializable
  internal data class WithStructureWritingMapKeys(val byCode: Map<StructureWritingKey, Int>)

  /** An encoder of another format, which [EncoderSpecificSerializer] requires. */
  private interface OtherFormatEncoder : Encoder

  /**
   * Requires an encoder of another format, throwing a plain [IllegalArgumentException] otherwise,
   * as savedstate's built-in serializers do with `require(encoder is SavedStateEncoder)`.
   */
  private object EncoderSpecificSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("test.EncoderSpecific", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) {
      require(encoder is OtherFormatEncoder) { "Requires the other format's encoder" }
      encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String = throw UnsupportedOperationException()
  }

  /** Runs [write] with the encoder, then throws [error]. */
  private class FailingSerializer(
    val error: Throwable,
    private val write: (Encoder) -> Unit = {},
  ) : KSerializer<String> {
    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("test.Failing", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) {
      write(encoder)
      throw error
    }

    override fun deserialize(decoder: Decoder): String = throw UnsupportedOperationException()
  }

  /**
   * Delegates to [delegate], but replaces a [DropArguments] it throws with an
   * [IllegalArgumentException], as a serializer that catches and rethrows everything might.
   */
  private class LimitReplacingSerializer<T>(private val delegate: KSerializer<T>) :
    KSerializer<T> by delegate {
    override fun serialize(encoder: Encoder, value: T) {
      try {
        delegate.serialize(encoder, value)
      } catch (e: DropArguments) {
        throw IllegalArgumentException("replaced", e)
      }
    }
  }

  private val unencodableValues = mutableListOf<IllegalArgumentException>()

  // Values recorded as the Unencodable substitute

  @Test
  fun `encode() - records an unregistered contextual property as the Unencodable substitute`() {
    val entry = WithContextual("1", Unregistered("2"))

    val encoded = encodeToMap(WithContextual.serializer(), entry)

    assertThat(encoded).isEqualTo(mapOf("id" to "1", "other" to UNENCODABLE_SUBSTITUTE))
  }

  @Test
  fun `encode() - keeps encoding the properties after one recorded as the Unencodable substitute`() {
    val serializer = Labeled.serializer(FailingSerializer(SerializationException("unencodable")))

    val encoded = encodeToMap(serializer, Labeled("1", "value", page = 2))

    assertThat(encoded)
      .isEqualTo(mapOf("id" to "1", "value" to UNENCODABLE_SUBSTITUTE, "page" to 2))
  }

  @Test
  fun `encode() - records a nullable contextual property that has a value as the Unencodable substitute`() {
    val entry = WithNullableContextual("1", Unregistered("2"))

    val encoded = encodeToMap(WithNullableContextual.serializer(), entry)

    assertThat(encoded).isEqualTo(mapOf("id" to "1", "other" to UNENCODABLE_SUBSTITUTE))
  }

  @Test
  fun `encode() - records a null contextual property as null`() {
    val entry = WithNullableContextual("1", null)

    val encoded = encodeToMap(WithNullableContextual.serializer(), entry, unencodableValues::add)

    assertThat(encoded).isEqualTo(mapOf("id" to "1", "other" to null))
    assertThat(unencodableValues).isEmpty()
  }

  @Test
  fun `encode() - records an unencodable list element as the Unencodable substitute and keeps the other elements`() {
    val entry = WithContextualList(listOf(null, Unregistered("2"), null))

    val encoded = encodeToMap(WithContextualList.serializer(), entry)

    assertThat(encoded).isEqualTo(mapOf("items" to listOf(null, UNENCODABLE_SUBSTITUTE, null)))
  }

  @Test
  fun `encode() - records an unencodable map value as the Unencodable substitute and keeps the other entries`() {
    val entry = WithContextualMapValues(mapOf("a" to null, "b" to Unregistered("2"), "c" to null))

    val encoded = encodeToMap(WithContextualMapValues.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(mapOf("byId" to mapOf("a" to null, "b" to UNENCODABLE_SUBSTITUTE, "c" to null)))
  }

  @Test
  fun `encode() - records only the unencodable property of a nested class as the Unencodable substitute`() {
    val entry = WithNestedContextual("1", Inner("inner", Unregistered("2")), page = 2)

    val encoded = encodeToMap(WithNestedContextual.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "id" to "1",
          "inner" to mapOf("name" to "inner", "other" to UNENCODABLE_SUBSTITUTE),
          "page" to 2,
        ),
      )
  }

  @Test
  fun `encode() - records a value whose serializer requires another encoder as the Unencodable substitute`() {
    val serializer = Labeled.serializer(EncoderSpecificSerializer)

    val encoded = encodeToMap(serializer, Labeled("1", "value", page = 2))

    assertThat(encoded)
      .isEqualTo(mapOf("id" to "1", "value" to UNENCODABLE_SUBSTITUTE, "page" to 2))
  }

  @Test
  fun `encode() - records a CharSequence property whose toString() throws as the Unencodable substitute`() {
    val text = FailingText(IllegalArgumentException("unreadable"))

    val encoded =
      encodeToMap(Labeled.serializer(charSequenceSerializer), Labeled("1", text, page = 2))

    assertThat(encoded)
      .isEqualTo(mapOf("id" to "1", "value" to UNENCODABLE_SUBSTITUTE, "page" to 2))
  }

  @Test
  fun `encode() - records a CharSequence list element whose toString() throws as the Unencodable substitute`() {
    val text = FailingText(IllegalArgumentException("unreadable"))
    val entry = WithTextList(listOf("a", text, "c"))

    val encoded = encodeToMap(WithTextList.serializer(), entry)

    assertThat(encoded).isEqualTo(mapOf("labels" to listOf("a", UNENCODABLE_SUBSTITUTE, "c")))
  }

  @Test
  fun `encode() - records a CharSequence map value whose toString() throws as the Unencodable substitute`() {
    val text = FailingText(IllegalArgumentException("unreadable"))
    val entry = WithTextMapValues(mapOf("a" to "first", "b" to text, "c" to "third"))

    val encoded = encodeToMap(WithTextMapValues.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf("byId" to mapOf("a" to "first", "b" to UNENCODABLE_SUBSTITUTE, "c" to "third")),
      )
  }

  // Reporting values recorded as the Unencodable substitute

  @Test
  fun `encode() - reports the exception of a value's serializer that can't encode it to onUnencodableValue`() {
    val failing = FailingSerializer(IllegalArgumentException("unencodable"))

    encodeToMap(
      Labeled.serializer(failing),
      Labeled("1", "value", page = 2),
      unencodableValues::add,
    )

    assertThat(unencodableValues).containsExactly(failing.error)
  }

  @Test
  fun `encode() - reports the exception a CharSequence's toString() throws to onUnencodableValue`() {
    val text = FailingText(IllegalArgumentException("unreadable"))

    encodeToMap(
      Labeled.serializer(charSequenceSerializer),
      Labeled("1", text, page = 2),
      unencodableValues::add,
    )

    assertThat(unencodableValues).containsExactly(text.error)
  }

  @Test
  fun `encode() - reports each value recorded as the Unencodable substitute to onUnencodableValue`() {
    val entry = WithContextualList(listOf(Unregistered("1"), null, Unregistered("2")))

    encodeToMap(WithContextualList.serializer(), entry, unencodableValues::add)

    assertThat(unencodableValues).hasSize(2)
    assertThat(unencodableValues[0]).isInstanceOf(SerializationException::class.java)
    assertThat(unencodableValues[1]).isInstanceOf(SerializationException::class.java)
  }

  @Test
  fun `encode() - reports nothing to onUnencodableValue when every value encodes`() {
    encodeToMap(Detail.serializer(), Detail("1"), unencodableValues::add)

    assertThat(unencodableValues).isEmpty()
  }

  // Sensitive values

  @Test
  fun `encode() - filters a sensitive property whose serializer can't encode it, without reporting it`() {
    val entry = WithContextualSecret("1", Unregistered("secret"))

    val encoded = encodeToMap(WithContextualSecret.serializer(), entry, unencodableValues::add)

    assertThat(encoded)
      .isEqualTo(mapOf("id" to "1", "authToken" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE))
    assertThat(unencodableValues).isEmpty()
  }

  @Test
  fun `encode() - filters a sensitive map value whose serializer can't encode it, without reporting it`() {
    val entry = WithContextualMapValues(mapOf("authToken" to Unregistered("secret")))

    val encoded = encodeToMap(WithContextualMapValues.serializer(), entry, unencodableValues::add)

    assertThat(encoded)
      .isEqualTo(mapOf("byId" to mapOf("authToken" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE)))
    assertThat(unencodableValues).isEmpty()
  }

  // Failures that still throw

  @Test
  fun `encode() - propagates the exception a value's serializer throws after writing a scalar`() {
    val failing =
      FailingSerializer(IllegalArgumentException("after writing")) { it.encodeString("partial") }

    val thrown =
      assertFailsWith<IllegalArgumentException> {
        encodeToMap(
          Labeled.serializer(failing),
          Labeled("1", "value", page = 2),
          unencodableValues::add,
        )
      }

    assertThat(thrown).isSameInstanceAs(failing.error)
    assertThat(unencodableValues).isEmpty()
  }

  @Test
  fun `encode() - propagates the exception a value's serializer throws after beginning a structure`() {
    val failing =
      FailingSerializer(IllegalArgumentException("inside structure")) {
        it.beginStructure(Detail.serializer().descriptor)
      }

    val thrown =
      assertFailsWith<IllegalArgumentException> {
        encodeToMap(
          Labeled.serializer(failing),
          Labeled("1", "value", page = 2),
          unencodableValues::add,
        )
      }

    assertThat(thrown).isSameInstanceAs(failing.error)
    assertThat(unencodableValues).isEmpty()
  }

  @Test
  fun `encode() - propagates a nested value's serializer exception rather than recording a partly written class as the Unencodable substitute`() {
    // The nested class wrote its id before its value's serializer failed after writing.
    val failing =
      FailingSerializer(IllegalArgumentException("after writing")) { it.encodeString("partial") }
    val serializer = Labeled.serializer(Labeled.serializer(failing))

    val thrown =
      assertFailsWith<IllegalArgumentException> {
        encodeToMap(serializer, Labeled("1", Labeled("2", "value", page = 3), page = 2))
      }

    assertThat(thrown).isSameInstanceAs(failing.error)
  }

  @Test
  fun `encode() - propagates a SerializationException from the entry's own serializer`() {
    val error = SerializationException("entry unencodable")
    val serializer = RecordingSerializer(Detail.serializer()).apply { this.error = error }

    val thrown =
      assertFailsWith<SerializationException> {
        encodeToMap(serializer, Detail("1"), unencodableValues::add)
      }

    assertThat(thrown).isSameInstanceAs(error)
    assertThat(unencodableValues).isEmpty()
  }

  @Test
  fun `encode() - propagates the exception of an entry's own serializer that requires another encoder`() {
    assertFailsWith<IllegalArgumentException> {
      encodeToMap(EncoderSpecificSerializer, "value", unencodableValues::add)
    }

    assertThat(unencodableValues).isEmpty()
  }

  @Test
  fun `encode() - propagates a value's serializer exception that isn't an IllegalArgumentException`() {
    val failing = FailingSerializer(IllegalStateException("boom"))

    val thrown =
      assertFailsWith<IllegalStateException> {
        encodeToMap(
          Labeled.serializer(failing),
          Labeled("1", "value", page = 2),
          unencodableValues::add,
        )
      }

    assertThat(thrown).isSameInstanceAs(failing.error)
    assertThat(unencodableValues).isEmpty()
  }

  @Test
  fun `encode() - propagates a CharSequence's toString() exception that isn't an IllegalArgumentException`() {
    val text = FailingText(IllegalStateException("unreadable"))

    val thrown =
      assertFailsWith<IllegalStateException> {
        encodeToMap(
          Labeled.serializer(charSequenceSerializer),
          Labeled("1", text, page = 2),
          unencodableValues::add,
        )
      }

    assertThat(thrown).isSameInstanceAs(text.error)
    assertThat(unencodableValues).isEmpty()
  }

  @Test
  fun `encode() - propagates the exception of a map key's serializer that can't encode it`() {
    val entry = WithContextualMapKeys(mapOf(Unregistered("1") to 1))

    assertFailsWith<SerializationException> {
      encodeToMap(WithContextualMapKeys.serializer(), entry, unencodableValues::add)
    }

    assertThat(unencodableValues).isEmpty()
  }

  @Test
  fun `encode() - propagates the exception a CharSequence map key's toString() throws`() {
    val text = FailingText(IllegalArgumentException("unreadable"))
    val entry = WithTextMapKeys(mapOf(text to 1))

    val thrown =
      assertFailsWith<IllegalArgumentException> {
        encodeToMap(WithTextMapKeys.serializer(), entry, unencodableValues::add)
      }

    assertThat(thrown).isSameInstanceAs(text.error)
    assertThat(unencodableValues).isEmpty()
  }

  @Test
  fun `encode() - throws for a map key whose serializer begins a structure despite a scalar descriptor`() {
    val entry = WithStructureWritingMapKeys(mapOf(StructureWritingKey("a") to 1))

    val thrown =
      assertFailsWith<SerializationException> {
        encodeToMap(WithStructureWritingMapKeys.serializer(), entry, unencodableValues::add)
      }

    assertThat(thrown).hasMessageThat().contains("map key that's a structure (Detail)")
    assertThat(unencodableValues).isEmpty()
  }

  // Argument limits

  @Test
  fun `encode() - drops the arguments for the value limit when a CharSequence whose toString() throws needs a value besides its substitute`() {
    // The id, the failed text, its substitute, and the page need four values.
    val sink = TreeArgumentWriter(budgetWithValuesRemaining(3))
    val text = FailingText(IllegalArgumentException("unreadable"))

    assertDropsArguments(ArgumentDropReason.MAX_COUNT) {
      encodeInto(Labeled.serializer(charSequenceSerializer), Labeled("1", text, page = 2), sink)
    }
  }

  @Test
  fun `encode() - drops the arguments for the nesting limit when a value's serializer rethrows the limit failure as an IllegalArgumentException`() {
    // The link chain writes nothing before the encoder's nesting limit rejects it.
    val serializer = Labeled.serializer(LimitReplacingSerializer(LinkSerializer))
    val entry = Labeled("1", linked(length = KotlinxPropertiesEncoder.MAX_SERIALIZER_NESTING), 2)

    assertDropsArguments(ArgumentDropReason.INVALID_STRUCTURE) {
      encodeToMap(serializer, entry, unencodableValues::add)
    }

    assertThat(unencodableValues).isEmpty()
  }

  @Test
  fun `encode() - drops the arguments for the character limit when a value's serializer rethrows the limit failure as an IllegalArgumentException`() {
    val serializer = Labeled.serializer(LimitReplacingSerializer(String.serializer()))
    val entry = Labeled("1", "x".repeat(MAX_ARGUMENT_CHARACTERS + 1), page = 2)

    assertDropsArguments(ArgumentDropReason.CHARACTER_LIMIT) {
      encodeToMap(serializer, entry, unencodableValues::add)
    }

    assertThat(unencodableValues).isEmpty()
  }

  @Test
  fun `encode() - drops the arguments for the character limit when a map key's serializer rethrows the limit failure as an IllegalArgumentException`() {
    val serializer =
      Labeled.serializer(
        MapSerializer(LimitReplacingSerializer(String.serializer()), Int.serializer()),
      )
    val entry = Labeled("1", mapOf("k".repeat(MAX_ARGUMENT_CHARACTERS + 1) to 1), page = 2)

    assertDropsArguments(ArgumentDropReason.CHARACTER_LIMIT) {
      encodeToMap(serializer, entry, unencodableValues::add)
    }

    assertThat(unencodableValues).isEmpty()
  }
}
