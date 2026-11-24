package io.sentry.compose.navigation3.internal.backstack.kotlinx

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_DEPTH
import io.sentry.compose.navigation3.internal.backstack.ArgumentDropReason
import io.sentry.compose.navigation3.internal.backstack.DropArguments
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxPropertiesEncoder.UNENCODABLE_SUBSTITUTE
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxPropertiesEncoder.VALUE_KEY
import io.sentry.util.UrlUtils
import kotlin.jvm.JvmInline
import kotlin.test.assertFailsWith
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.junit.Test

/**
 * Covers maps whose keys are structures, which [KotlinxPropertiesEncoder] records as lists of
 * `{"key": <key>, "value": <value>}` entries.
 */
class KotlinxPropertiesEncoderStructuredMapKeysTest {

  @Serializable data class Point(val x: Int)

  @Serializable @SerialName("everywhere") data object Everywhere

  @Serializable @JvmInline value class PointRef(val point: Point)

  @Serializable @JvmInline value class OuterPointRef(val ref: PointRef)

  /** A value class whose underlying property's name (`token`) is sensitive. */
  @Serializable @JvmInline value class TokenRef(val token: String)

  @Serializable
  sealed interface Origin {
    @Serializable @SerialName("search") data class Search(val query: String) : Origin
  }

  interface OpenOrigin

  /** Isn't `@Serializable`, so it can't be encoded as an [OpenOrigin] without a module. */
  class SearchOrigin : OpenOrigin

  @Serializable data class Credentials(val user: String, val password: String)

  @Serializable data class Session(val token: String)

  @Serializable data class WithPointMapKeys(val byPoint: Map<Point, Int>)

  @Serializable data class WithObjectMapKeys(val byRegion: Map<Everywhere, Int>)

  @Serializable data class WithListMapKeys(val byList: Map<List<String>, Int>)

  @Serializable data class WithMapMapKeys(val byMap: Map<Map<String, Int>, Int>)

  @Serializable data class WithOriginMapKeys(val byOrigin: Map<Origin, Int>)

  @Serializable data class WithPointRefMapKeys(val byPointRef: Map<PointRef, Int>)

  @Serializable data class WithOuterPointRefMapKeys(val byOuterRef: Map<OuterPointRef, Int>)

  @Serializable data class WithPointMapKeysToTokenRefs(val byPoint: Map<Point, TokenRef>)

  @Serializable data class WithNullablePointMapKeys(val byPoint: Map<Point?, Int>)

  @Serializable data class WithNestedPointMapKeys(val groups: List<Map<Point, Int>>)

  @Serializable data class WithPointMapKeysToLabels(val byPoint: Map<Point, String>)

  @Serializable data class WithPointMapKeysToLists(val byPoint: Map<Point, List<String>>)

  @Serializable data class WithCredentialsMapKeys(val byUser: Map<Credentials, Int>)

  @Serializable data class WithSessionMapKeys(val byLogin: Map<Session, String>)

  @Serializable data class WithPointMapKeysToCredentials(val byPoint: Map<Point, Credentials>)

  @Serializable data class WithOpenOriginMapKeys(val byOrigin: Map<@Polymorphic OpenOrigin, Int>)

  @Serializable
  internal data class WithContextualValuesUnderPointKeys(
    val byPoint: Map<Point, @Contextual Unregistered>,
  )

  @Serializable
  data class WithPartlyWrittenMapKeys(
    val byPoint: Map<@Serializable(PartlyWrittenKeySerializer::class) Point, Int>,
  )

  /** Begins a structure for the key, then throws, as a serializer that fails partway might. */
  object PartlyWrittenKeySerializer : KSerializer<Point> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("test.PartlyWritten")

    override fun serialize(encoder: Encoder, value: Point) {
      encoder.beginStructure(descriptor)
      throw SerializationException("partway")
    }

    override fun deserialize(decoder: Decoder): Point = throw UnsupportedOperationException()
  }

  /** A value class whose underlying property's name (`credentials`) is sensitive. */
  @Serializable @JvmInline value class CredentialsRef(val credentials: Point)

  @Serializable
  data class WithCredentialsRefMapKeys(
    val byOwner: Map<CredentialsRef, @Serializable(with = ThrowingSerializer::class) String>,
  )

  /** Fails the test if a value it should never see is encoded. */
  object ThrowingSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("test.Throwing", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String): Unit = error("serializer ran")

    override fun deserialize(decoder: Decoder): String = throw UnsupportedOperationException()
  }

  /** A class key that encodes as a [CredentialsRef] when [isSecret], or else as a [Point]. */
  @Serializable(with = MixedOwnerSerializer::class)
  data class MixedOwner(val x: Int, val isSecret: Boolean)

  object MixedOwnerSerializer : KSerializer<MixedOwner> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("test.MixedOwner")

    override fun serialize(encoder: Encoder, value: MixedOwner) {
      if (value.isSecret) {
        encoder.encodeSerializableValue(CredentialsRef.serializer(), CredentialsRef(Point(value.x)))
      } else {
        encoder.encodeSerializableValue(Point.serializer(), Point(value.x))
      }
    }

    override fun deserialize(decoder: Decoder): MixedOwner = throw UnsupportedOperationException()
  }

  @Serializable data class WithMixedOwnerMapKeys(val byOwner: Map<MixedOwner, String>)

  private val unencodableValues = mutableListOf<IllegalArgumentException>()

  // Encoded shapes

  @Test
  fun `encode() - records a map with class keys as a list of key and value entries`() {
    val entry = WithPointMapKeys(mapOf(Point(1) to 2))

    val encoded = encodeToMap(WithPointMapKeys.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(mapOf("byPoint" to listOf(mapOf("key" to mapOf("x" to 1), "value" to 2))))
  }

  @Test
  fun `encode() - records the entries of a map with class keys in map order`() {
    val entry = WithPointMapKeysToLabels(mapOf(Point(2) to "b", Point(1) to "a", Point(3) to "c"))

    val encoded = encodeToMap(WithPointMapKeysToLabels.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "byPoint" to
            listOf(
              mapOf("key" to mapOf("x" to 2), "value" to "b"),
              mapOf("key" to mapOf("x" to 1), "value" to "a"),
              mapOf("key" to mapOf("x" to 3), "value" to "c"),
            ),
        ),
      )
  }

  @Test
  fun `encode() - records a map with object keys as a list of key and value entries`() {
    val entry = WithObjectMapKeys(mapOf(Everywhere to 1))

    val encoded = encodeToMap(WithObjectMapKeys.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf("byRegion" to listOf(mapOf("key" to emptyMap<String, Any?>(), "value" to 1))),
      )
  }

  @Test
  fun `encode() - records a map with list keys as a list of key and value entries`() {
    val entry = WithListMapKeys(mapOf(listOf("a", "b") to 1))

    val encoded = encodeToMap(WithListMapKeys.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(mapOf("byList" to listOf(mapOf("key" to listOf("a", "b"), "value" to 1))))
  }

  @Test
  fun `encode() - records a map with map keys as a list of key and value entries`() {
    val entry = WithMapMapKeys(mapOf(mapOf("a" to 1) to 2))

    val encoded = encodeToMap(WithMapMapKeys.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(mapOf("byMap" to listOf(mapOf("key" to mapOf("a" to 1), "value" to 2))))
  }

  @Test
  fun `encode() - records a map with sealed keys as a list of entries with each key's serial name and value`() {
    val entry = WithOriginMapKeys(mapOf(Origin.Search("shoes") to 1))

    val encoded = encodeToMap(WithOriginMapKeys.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "byOrigin" to
            listOf(
              mapOf(
                "key" to mapOf("type" to "search", "value" to mapOf("query" to "shoes")),
                "value" to 1,
              ),
            ),
        ),
      )
  }

  @Test
  fun `encode() - records a map with value class keys that wrap a class as entries of the underlying keys`() {
    val entry = WithPointRefMapKeys(mapOf(PointRef(Point(1)) to 2))

    val encoded = encodeToMap(WithPointRefMapKeys.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(mapOf("byPointRef" to listOf(mapOf("key" to mapOf("x" to 1), "value" to 2))))
  }

  @Test
  fun `encode() - records a map with value class keys that wrap a value class wrapping a class as entries of the innermost keys`() {
    val entry = WithOuterPointRefMapKeys(mapOf(OuterPointRef(PointRef(Point(1))) to 2))

    val encoded = encodeToMap(WithOuterPointRefMapKeys.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(mapOf("byOuterRef" to listOf(mapOf("key" to mapOf("x" to 1), "value" to 2))))
  }

  @Test
  fun `encode() - records a null key of a map with nullable class keys as null`() {
    val entry = WithNullablePointMapKeys(mapOf(null to 1, Point(2) to 3))

    val encoded = encodeToMap(WithNullablePointMapKeys.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "byPoint" to
            listOf(
              mapOf("key" to null, "value" to 1),
              mapOf("key" to mapOf("x" to 2), "value" to 3),
            ),
        ),
      )
  }

  @Test
  fun `encode() - records structured values under class keys`() {
    val entry = WithPointMapKeysToLists(mapOf(Point(1) to listOf("a", "b")))

    val encoded = encodeToMap(WithPointMapKeysToLists.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf("byPoint" to listOf(mapOf("key" to mapOf("x" to 1), "value" to listOf("a", "b")))),
      )
  }

  @Test
  fun `encode() - records a map with class keys nested in a list as a list of key and value entries`() {
    val entry = WithNestedPointMapKeys(listOf(mapOf(Point(1) to 2), emptyMap()))

    val encoded = encodeToMap(WithNestedPointMapKeys.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "groups" to
            listOf(listOf(mapOf("key" to mapOf("x" to 1), "value" to 2)), emptyList<Any?>()),
        ),
      )
  }

  @Test
  fun `encode() - records an empty map with class keys as an empty list`() {
    val encoded = encodeToMap(WithPointMapKeys.serializer(), WithPointMapKeys(emptyMap()))

    assertThat(encoded).isEqualTo(mapOf("byPoint" to emptyList<Any?>()))
  }

  @Test
  fun `encode() - wraps a top-level map with class keys under the value key`() {
    val serializer = MapSerializer(Point.serializer(), Int.serializer())

    val encoded = encodeToMap(serializer, mapOf(Point(1) to 2))

    assertThat(encoded)
      .isEqualTo(mapOf(VALUE_KEY to listOf(mapOf("key" to mapOf("x" to 1), "value" to 2))))
  }

  // Sensitive data

  @Test
  fun `encode() - filters the sensitive properties of a class key`() {
    val entry = WithCredentialsMapKeys(mapOf(Credentials(user = "jane", password = "secret") to 1))

    val encoded = encodeToMap(WithCredentialsMapKeys.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "byUser" to
            listOf(
              mapOf(
                "key" to mapOf("user" to "jane", "password" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE),
                "value" to 1,
              ),
            ),
        ),
      )
  }

  @Test
  fun `encode() - filters the value under a key that's filtered as a whole, without running its serializer`() {
    val entry = WithCredentialsRefMapKeys(mapOf(CredentialsRef(Point(1)) to "LEAKED-SECRET"))

    val encoded = encodeToMap(WithCredentialsRefMapKeys.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "byOwner" to
            listOf(
              mapOf(
                "key" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE,
                "value" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE,
              ),
            ),
        ),
      )
  }

  @Test
  fun `encode() - filters only the value under a filtered key, not the values of the next entries`() {
    val entry =
      WithMixedOwnerMapKeys(
        mapOf(
          MixedOwner(1, isSecret = true) to "LEAKED-SECRET",
          MixedOwner(2, isSecret = false) to "VISIBLE",
        ),
      )

    val encoded = encodeToMap(WithMixedOwnerMapKeys.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "byOwner" to
            listOf(
              mapOf(
                "key" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE,
                "value" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE,
              ),
              mapOf("key" to mapOf("x" to 2), "value" to "VISIBLE"),
            ),
        ),
      )
  }

  @Test
  fun `encode() - doesn't filter a value under a class key based on the key's sensitive property names`() {
    // There's no key text to check, so only the key's own sensitive property is filtered.
    val entry = WithSessionMapKeys(mapOf(Session(token = "abc") to "VISIBLE"))

    val encoded = encodeToMap(WithSessionMapKeys.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "byLogin" to
            listOf(
              mapOf(
                "key" to mapOf("token" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE),
                "value" to "VISIBLE",
              ),
            ),
        ),
      )
  }

  @Test
  fun `encode() - filters the sensitive properties of a value under a class key`() {
    val entry =
      WithPointMapKeysToCredentials(
        mapOf(Point(1) to Credentials(user = "jane", password = "secret")),
      )

    val encoded = encodeToMap(WithPointMapKeysToCredentials.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "byPoint" to
            listOf(
              mapOf(
                "key" to mapOf("x" to 1),
                "value" to
                  mapOf("user" to "jane", "password" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE),
              ),
            ),
        ),
      )
  }

  @Test
  fun `encode() - filters a value under a class key when the value's underlying property is sensitive`() {
    val entry = WithPointMapKeysToTokenRefs(mapOf(Point(1) to TokenRef("abc123")))

    val encoded = encodeToMap(WithPointMapKeysToTokenRefs.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "byPoint" to
            listOf(mapOf("key" to mapOf("x" to 1), "value" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE)),
        ),
      )
  }

  // Unencodable keys and values

  @Test
  fun `encode() - records a structured key whose serializer can't encode it as the Unencodable substitute`() {
    val entry = WithOpenOriginMapKeys(mapOf(SearchOrigin() to 1))

    val encoded = encodeToMap(WithOpenOriginMapKeys.serializer(), entry, unencodableValues::add)

    assertThat(encoded)
      .isEqualTo(mapOf("byOrigin" to listOf(mapOf("key" to UNENCODABLE_SUBSTITUTE, "value" to 1))))
    assertThat(unencodableValues).hasSize(1)
    assertThat(unencodableValues[0]).isInstanceOf(SerializationException::class.java)
  }

  @Test
  fun `encode() - records a value under a class key whose serializer can't encode it as the Unencodable substitute`() {
    val entry = WithContextualValuesUnderPointKeys(mapOf(Point(1) to Unregistered("2")))

    val encoded =
      encodeToMap(WithContextualValuesUnderPointKeys.serializer(), entry, unencodableValues::add)

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "byPoint" to listOf(mapOf("key" to mapOf("x" to 1), "value" to UNENCODABLE_SUBSTITUTE)),
        ),
      )
    assertThat(unencodableValues).hasSize(1)
  }

  @Test
  fun `encode() - throws when a structured key's serializer throws after beginning a structure`() {
    val entry = WithPartlyWrittenMapKeys(mapOf(Point(1) to 2))

    assertFailsWith<SerializationException> {
      encodeToMap(WithPartlyWrittenMapKeys.serializer(), entry, unencodableValues::add)
    }

    assertThat(unencodableValues).isEmpty()
  }

  // Argument limits

  @Test
  fun `encode() - drops the arguments for invalid structure when a structured key is nested past the maximum depth`() {
    val (keySerializer, key) =
      boxed(depth = MAX_ARGUMENT_DEPTH, value = "leaf", String.serializer())
    val serializer = Box.serializer(MapSerializer(keySerializer, Int.serializer()))

    val exception = assertFailsWith<DropArguments> { encodeToMap(serializer, Box(mapOf(key to 1))) }

    assertThat(exception.reason).isEqualTo(ArgumentDropReason.INVALID_STRUCTURE)
  }

  @Test
  fun `encode() - drops the arguments for the character limit when a structured key is too long`() {
    // Converting this key to a string would copy the one 16 KiB string 4,000 times (~64 MiB).
    val longString = "a".repeat(16 * 1024)
    val entry = WithListMapKeys(mapOf(List(4_000) { longString } to 1))

    val exception =
      assertFailsWith<DropArguments> { encodeToMap(WithListMapKeys.serializer(), entry) }

    assertThat(exception.reason).isEqualTo(ArgumentDropReason.CHARACTER_LIMIT)
  }
}
