package io.sentry.compose.navigation3.internal.backstack.kotlinx

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxPropertiesEncoder.UNENCODABLE_SUBSTITUTE
import io.sentry.util.UrlUtils
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertFailsWith
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.junit.Test

/**
 * Covers values of open polymorphic types (non-sealed interfaces, abstract classes, and
 * `@Polymorphic` properties), which [KotlinxPropertiesEncoder] can't encode without the app's
 * `SerializersModule`, so it records them as [UNENCODABLE_SUBSTITUTE] whatever their runtime
 * classes. `CharSequence` properties, which are open polymorphic too, are covered by
 * [KotlinxPropertiesEncoderTest].
 */
class KotlinxPropertiesEncoderOpenPolymorphismTest {

  interface Origin

  @Serializable @SerialName("search") data class Search(val term: String) : Origin

  class PlainOrigin : Origin

  @Serializable
  @SerialName("sourceWithBodies")
  enum class SourceWithBodies : Origin {
    PUSH {
      override val channel = "push"
    };

    abstract val channel: String
  }

  @Serializable abstract class Shape

  @Serializable @SerialName("circle") data class Circle(val radius: Int) : Shape()

  @Serializable data class WithOrigin(val id: String, val origin: Origin, val page: Int)

  @Serializable data class WithNullableOrigin(val id: String, val origin: Origin?)

  @Serializable data class WithShape(val id: String, val shape: Shape)

  @Serializable data class WithAny(val id: String, @Polymorphic val extra: Any)

  @Serializable data class WithOriginList(val origins: List<Origin>)

  @Serializable data class WithOriginMap(val byName: Map<String, Origin>)

  @Serializable data class WithSecretOrigin(val id: String, val authToken: Origin)

  /** Counts its serializer's initializations, which a lookup of that serializer would trigger. */
  @Serializable(with = WatchedOriginSerializer::class) internal class WatchedOrigin : Origin

  internal object WatchedOriginSerializer : KSerializer<WatchedOrigin> {
    init {
      watchedOriginSerializerRuns.incrementAndGet()
    }

    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("watchedOrigin", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: WatchedOrigin) {
      watchedOriginSerializerRuns.incrementAndGet()
      encoder.encodeString("encoded")
    }

    override fun deserialize(decoder: Decoder): WatchedOrigin =
      throw UnsupportedOperationException()
  }

  private var unencodableValueCount = 0
  private val onUnencodableValue: () -> Unit = { unencodableValueCount++ }

  // Properties

  @Test
  fun `encode() - records an open polymorphic property of a @Serializable class as the Unencodable substitute`() {
    val entry = WithOrigin("1", Search("shoes"), page = 2)

    val encoded = encodeToMap(WithOrigin.serializer(), entry, onUnencodableValue)

    assertThat(encoded)
      .isEqualTo(mapOf("id" to "1", "origin" to UNENCODABLE_SUBSTITUTE, "page" to 2))
    assertThat(unencodableValueCount).isEqualTo(1)
  }

  @Test
  fun `encode() - records an open polymorphic property of a non-@Serializable class as the Unencodable substitute`() {
    val entry = WithOrigin("1", PlainOrigin(), page = 2)

    val encoded = encodeToMap(WithOrigin.serializer(), entry, onUnencodableValue)

    assertThat(encoded)
      .isEqualTo(mapOf("id" to "1", "origin" to UNENCODABLE_SUBSTITUTE, "page" to 2))
    assertThat(unencodableValueCount).isEqualTo(1)
  }

  @Test
  fun `encode() - records a property of an abstract class type as the Unencodable substitute`() {
    val encoded = encodeToMap(WithShape.serializer(), WithShape("1", Circle(3)), onUnencodableValue)

    assertThat(encoded).isEqualTo(mapOf("id" to "1", "shape" to UNENCODABLE_SUBSTITUTE))
    assertThat(unencodableValueCount).isEqualTo(1)
  }

  @Test
  fun `encode() - records a @Polymorphic Any property as the Unencodable substitute`() {
    val encoded =
      encodeToMap(WithAny.serializer(), WithAny("1", Search("shoes")), onUnencodableValue)

    assertThat(encoded).isEqualTo(mapOf("id" to "1", "extra" to UNENCODABLE_SUBSTITUTE))
    assertThat(unencodableValueCount).isEqualTo(1)
  }

  @Test
  fun `encode() - records an open polymorphic enum constant with a body as the Unencodable substitute`() {
    val entry = WithOrigin("1", SourceWithBodies.PUSH, page = 2)

    val encoded = encodeToMap(WithOrigin.serializer(), entry, onUnencodableValue)

    assertThat(encoded)
      .isEqualTo(mapOf("id" to "1", "origin" to UNENCODABLE_SUBSTITUTE, "page" to 2))
    assertThat(unencodableValueCount).isEqualTo(1)
  }

  @Test
  fun `encode() - records a nullable open polymorphic property holding a value as the Unencodable substitute`() {
    val entry = WithNullableOrigin("1", Search("shoes"))

    val encoded = encodeToMap(WithNullableOrigin.serializer(), entry, onUnencodableValue)

    assertThat(encoded).isEqualTo(mapOf("id" to "1", "origin" to UNENCODABLE_SUBSTITUTE))
    assertThat(unencodableValueCount).isEqualTo(1)
  }

  @Test
  fun `encode() - encodes a null open polymorphic property as null without reporting it`() {
    val entry = WithNullableOrigin("1", null)

    val encoded = encodeToMap(WithNullableOrigin.serializer(), entry, onUnencodableValue)

    assertThat(encoded).isEqualTo(mapOf("id" to "1", "origin" to null))
    assertThat(unencodableValueCount).isEqualTo(0)
  }

  @Test
  fun `encode() - doesn't run the serializer of an open polymorphic value's runtime class`() {
    val entry = WithOrigin("1", WatchedOrigin(), page = 2)

    val encoded = encodeToMap(WithOrigin.serializer(), entry, onUnencodableValue)

    assertThat(encoded["origin"]).isEqualTo(UNENCODABLE_SUBSTITUTE)
    assertThat(watchedOriginSerializerRuns.get()).isEqualTo(0)
  }

  // Nesting

  @Test
  fun `encode() - records each open polymorphic list element as the Unencodable substitute`() {
    val entry = WithOriginList(listOf(Search("shoes"), PlainOrigin()))

    val encoded = encodeToMap(WithOriginList.serializer(), entry, onUnencodableValue)

    assertThat(encoded)
      .isEqualTo(mapOf("origins" to listOf(UNENCODABLE_SUBSTITUTE, UNENCODABLE_SUBSTITUTE)))
    assertThat(unencodableValueCount).isEqualTo(2)
  }

  @Test
  fun `encode() - records each open polymorphic map value as the Unencodable substitute`() {
    val entry = WithOriginMap(mapOf("first" to Search("shoes"), "second" to PlainOrigin()))

    val encoded = encodeToMap(WithOriginMap.serializer(), entry, onUnencodableValue)

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "byName" to mapOf("first" to UNENCODABLE_SUBSTITUTE, "second" to UNENCODABLE_SUBSTITUTE),
        ),
      )
    assertThat(unencodableValueCount).isEqualTo(2)
  }

  // Sensitive data

  @Test
  fun `encode() - filters an open polymorphic property with a sensitive name without reporting it`() {
    val entry = WithSecretOrigin("1", Search("shoes"))

    val encoded = encodeToMap(WithSecretOrigin.serializer(), entry, onUnencodableValue)

    assertThat(encoded)
      .isEqualTo(mapOf("id" to "1", "authToken" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE))
    assertThat(unencodableValueCount).isEqualTo(0)
  }

  // Entries

  @Test
  fun `encode() - propagates the exception of an entry's own open polymorphic serializer`() {
    assertFailsWith<SerializationException> {
      encodeToMap(PolymorphicSerializer(Origin::class), Search("shoes"), onUnencodableValue)
    }

    assertThat(unencodableValueCount).isEqualTo(0)
  }

  private companion object {
    val watchedOriginSerializerRuns = AtomicInteger()
  }
}
