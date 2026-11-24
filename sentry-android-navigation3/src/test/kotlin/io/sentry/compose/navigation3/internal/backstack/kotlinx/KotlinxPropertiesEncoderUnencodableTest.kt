package io.sentry.compose.navigation3.internal.backstack.kotlinx

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_CHARACTERS
import io.sentry.compose.navigation3.internal.backstack.ArgumentDropReason
import io.sentry.compose.navigation3.internal.backstack.DropArguments
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxPropertiesEncoder.UNENCODABLE_SUBSTITUTE
import io.sentry.util.UrlUtils
import kotlin.test.assertFailsWith
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
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

  /** Places [value] between two properties that encode, so its neighbors on both sides show. */
  @Serializable data class Labeled<T>(val id: String, val value: T, val page: Int)

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

  // Reporting values recorded as the Unencodable substitute

  @Test
  fun `encode() - passes the serializer's exception to onUnencodableValue for an unencodable value`() {
    val failing = FailingSerializer(IllegalArgumentException("unencodable"))

    encodeToMap(
      Labeled.serializer(failing),
      Labeled("1", "value", page = 2),
      unencodableValues::add,
    )

    assertThat(unencodableValues).containsExactly(failing.error)
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
  fun `encode() - doesn't call onUnencodableValue when every value encodes`() {
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
  fun `encode() - throws when a value's serializer throws after writing a scalar`() {
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
  fun `encode() - throws when a value's serializer throws after beginning a structure`() {
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
  fun `encode() - throws rather than recording a partly written nested class as the Unencodable substitute`() {
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
  fun `encode() - throws when a map key's serializer can't encode it`() {
    val entry = WithContextualMapKeys(mapOf(Unregistered("1") to 1))

    assertFailsWith<SerializationException> {
      encodeToMap(WithContextualMapKeys.serializer(), entry, unencodableValues::add)
    }

    assertThat(unencodableValues).isEmpty()
  }

  @Test
  fun `encode() - throws when the entry's own serializer throws a SerializationException`() {
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
  fun `encode() - throws when the entry's own serializer requires another encoder`() {
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
  fun `encode() - drops the arguments for the nesting limit a serializer rethrew as an IllegalArgumentException`() {
    // The link chain writes nothing before the encoder's nesting limit rejects it.
    val serializer = Labeled.serializer(LimitReplacingSerializer(LinkSerializer))
    val entry = Labeled("1", linked(length = KotlinxPropertiesEncoder.MAX_SERIALIZER_NESTING), 2)

    val thrown =
      assertFailsWith<DropArguments> { encodeToMap(serializer, entry, unencodableValues::add) }

    assertThat(thrown.reason).isEqualTo(ArgumentDropReason.INVALID_STRUCTURE)
    assertThat(unencodableValues).isEmpty()
  }

  @Test
  fun `encode() - drops the arguments for the character limit a serializer rethrew as an IllegalArgumentException`() {
    val serializer = Labeled.serializer(LimitReplacingSerializer(String.serializer()))
    val entry = Labeled("1", "x".repeat(MAX_ARGUMENT_CHARACTERS + 1), page = 2)

    val thrown =
      assertFailsWith<DropArguments> { encodeToMap(serializer, entry, unencodableValues::add) }

    assertThat(thrown.reason).isEqualTo(ArgumentDropReason.CHARACTER_LIMIT)
    assertThat(unencodableValues).isEmpty()
  }
}
