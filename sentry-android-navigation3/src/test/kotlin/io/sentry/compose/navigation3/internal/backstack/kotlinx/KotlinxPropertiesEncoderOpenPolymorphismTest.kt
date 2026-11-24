package io.sentry.compose.navigation3.internal.backstack.kotlinx

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_VALUES
import io.sentry.compose.navigation3.internal.backstack.ArgumentDropReason
import io.sentry.compose.navigation3.internal.backstack.DropArguments
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxPropertiesEncoder.UNENCODABLE_SUBSTITUTE
import io.sentry.util.UrlUtils
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertFailsWith
import kotlinx.serialization.Contextual
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
 * `@Polymorphic` properties), which [KotlinxPropertiesEncoder] encodes with their runtime class's
 * own serializer, as `{"type": <serial name>, "value": <value>}`, without a `SerializersModule`
 * registering it.
 */
class KotlinxPropertiesEncoderOpenPolymorphismTest {

  /** An open polymorphic type, so its properties are encoded with a `PolymorphicSerializer`. */
  interface Origin

  @Serializable @SerialName("search") data class Search(val term: String) : Origin

  /** Has no `@SerialName`, so its serial name is its fully qualified class name. */
  @Serializable data class Campaign(val name: String) : Origin

  @Serializable @SerialName("deeplink") data object DeepLink : Origin

  @Serializable
  @SerialName("source")
  enum class Source : Origin {
    PUSH,
  }

  /** Has an open polymorphic property of its own. */
  @Serializable @SerialName("referral") data class Referral(val from: Origin) : Origin

  @Serializable @SerialName("login") data class Login(val user: String, val token: String) : Origin

  @Serializable @SerialName("tagged") data class Tagged(val tags: List<Int>) : Origin

  @Serializable
  @SerialName("withContextual")
  internal data class ContextualOrigin(val id: String, @Contextual val other: Unregistered) : Origin

  /** `@Serializable` makes the abstract class's properties open polymorphic. */
  @Serializable abstract class Shape

  @Serializable @SerialName("circle") data class Circle(val radius: Int) : Shape()

  @Serializable data class WithOrigin(val id: String, val origin: Origin, val page: Int)

  @Serializable data class WithNullableOrigin(val id: String, val origin: Origin?)

  @Serializable data class WithShape(val shape: Shape)

  @Serializable data class WithAny(val id: String, @Polymorphic val extra: Any)

  @Serializable data class WithOriginList(val origins: List<Origin>)

  @Serializable data class WithOriginMap(val byName: Map<String, Origin>)

  @Serializable data class WithOriginMapKeys(val byOrigin: Map<Origin, Int>)

  // Runtime classes without a serializer to find

  class PlainOrigin : Origin

  enum class UnannotatedSource : Origin {
    PUSH,
  }

  @Serializable
  @SerialName("sourceWithBodies")
  enum class SourceWithBodies : Origin {
    PUSH {
      override val channel = "push"
    };

    abstract val channel: String
  }

  /** `@Serializable`, but generic, so its serializer can't be found without type arguments. */
  @Serializable @SerialName("generic") data class GenericOrigin<T>(val value: T) : Origin

  /** `@Serializable`, so its serializer is found, but its serializer throws without writing. */
  @Serializable(with = FailingOriginSerializer::class) internal class FailingOrigin : Origin

  internal object FailingOriginSerializer : NamingOnlySerializer<FailingOrigin>() {
    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("failingOrigin", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: FailingOrigin) =
      throw SerializationException("failing origin")
  }

  // A class whose serializer's initializer threw can't be initialized again for the rest of the
  // JVM's life, so each test that triggers one gets its own pair, separate from other suites'.

  @Serializable(with = UninitializableOriginSerializer::class)
  internal class UninitializableOrigin : Origin

  internal object UninitializableOriginSerializer : NamingOnlySerializer<UninitializableOrigin>() {
    init {
      if (System.nanoTime() != 0L) error("serializer init failed")
    }

    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("uninitializableOrigin", PrimitiveKind.STRING)
  }

  @Serializable(with = UnlinkableOriginSerializer::class) internal class UnlinkableOrigin : Origin

  internal object UnlinkableOriginSerializer : NamingOnlySerializer<UnlinkableOrigin>() {
    init {
      if (System.nanoTime() != 0L) throw NoSuchMethodError("serializer init failed to link")
    }

    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("unlinkableOrigin", PrimitiveKind.STRING)
  }

  @Serializable(with = FatallyUninitializableOriginSerializer::class)
  internal class FatallyUninitializableOrigin : Origin

  internal object FatallyUninitializableOriginSerializer :
    NamingOnlySerializer<FatallyUninitializableOrigin>() {
    init {
      if (System.nanoTime() != 0L) throw OutOfMemoryError("serializer init failed fatally")
    }

    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("fatallyUninitializableOrigin", PrimitiveKind.STRING)
  }

  /**
   * A serializer class, unlike an object, is constructed on each lookup, so its first construction
   * can fail while later ones succeed.
   */
  @Serializable(with = FatalOnceOriginSerializer::class) internal class FatalOnceOrigin : Origin

  internal class FatalOnceOriginSerializer : KSerializer<FatalOnceOrigin> {
    init {
      if (constructions.getAndIncrement() == 0) {
        throw OutOfMemoryError("first serializer construction failed fatally")
      }
    }

    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("fatalOnceOrigin", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: FatalOnceOrigin) {
      encoder.encodeString("encoded")
    }

    override fun deserialize(decoder: Decoder): FatalOnceOrigin =
      throw UnsupportedOperationException()

    private companion object {
      val constructions = AtomicInteger()
    }
  }

  private val unencodableValues = mutableListOf<IllegalArgumentException>()

  // Runtime classes that are @Serializable

  @Test
  fun `encode() - encodes an open polymorphic property as its runtime class's serial name and value`() {
    val entry = WithOrigin("1", Search("shoes"), page = 2)

    val encoded = encodeToMap(WithOrigin.serializer(), entry, unencodableValues::add)

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "id" to "1",
          "origin" to mapOf("type" to "search", "value" to mapOf("term" to "shoes")),
          "page" to 2,
        ),
      )
    assertThat(unencodableValues).isEmpty()
  }

  @Test
  fun `encode() - uses the class name as the type of an open polymorphic value without a @SerialName`() {
    val entry = WithOrigin("1", Campaign("spring"), page = 2)

    val encoded = encodeToMap(WithOrigin.serializer(), entry)

    assertThat(encoded["origin"])
      .isEqualTo(
        mapOf("type" to Campaign::class.java.canonicalName, "value" to mapOf("name" to "spring")),
      )
  }

  @Test
  fun `encode() - encodes an open polymorphic object as its serial name and an empty value`() {
    val entry = WithOrigin("1", DeepLink, page = 2)

    val encoded = encodeToMap(WithOrigin.serializer(), entry)

    assertThat(encoded["origin"])
      .isEqualTo(mapOf("type" to "deeplink", "value" to emptyMap<String, Any?>()))
  }

  @Test
  fun `encode() - encodes an open polymorphic enum as its serial name and its constant's name`() {
    val entry = WithOrigin("1", Source.PUSH, page = 2)

    val encoded = encodeToMap(WithOrigin.serializer(), entry)

    assertThat(encoded["origin"]).isEqualTo(mapOf("type" to "source", "value" to "PUSH"))
  }

  @Test
  fun `encode() - encodes a property of an abstract class type as its runtime class's serial name and value`() {
    val encoded = encodeToMap(WithShape.serializer(), WithShape(Circle(3)))

    assertThat(encoded)
      .isEqualTo(mapOf("shape" to mapOf("type" to "circle", "value" to mapOf("radius" to 3))))
  }

  @Test
  fun `encode() - encodes a @Polymorphic Any property as its runtime class's serial name and value`() {
    val encoded = encodeToMap(WithAny.serializer(), WithAny("1", Search("shoes")))

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "id" to "1",
          "extra" to mapOf("type" to "search", "value" to mapOf("term" to "shoes")),
        ),
      )
  }

  @Test
  fun `encode() - encodes a nullable open polymorphic property holding a value as its runtime class's serial name and value`() {
    val encoded =
      encodeToMap(WithNullableOrigin.serializer(), WithNullableOrigin("1", Search("shoes")))

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "id" to "1",
          "origin" to mapOf("type" to "search", "value" to mapOf("term" to "shoes")),
        ),
      )
  }

  @Test
  fun `encode() - encodes a null open polymorphic property as null`() {
    val encoded = encodeToMap(WithNullableOrigin.serializer(), WithNullableOrigin("1", null))

    assertThat(encoded).isEqualTo(mapOf("id" to "1", "origin" to null))
  }

  @Test
  fun `encode() - encodes a top-level open polymorphic value's serial name and value as the arguments`() {
    val encoded = encodeToMap(PolymorphicSerializer(Origin::class), Search("shoes"))

    assertThat(encoded).isEqualTo(mapOf("type" to "search", "value" to mapOf("term" to "shoes")))
  }

  // Nesting

  @Test
  fun `encode() - encodes open polymorphic list elements as their serial names and values`() {
    val entry = WithOriginList(listOf(Search("shoes"), DeepLink))

    val encoded = encodeToMap(WithOriginList.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "origins" to
            listOf(
              mapOf("type" to "search", "value" to mapOf("term" to "shoes")),
              mapOf("type" to "deeplink", "value" to emptyMap<String, Any?>()),
            ),
        ),
      )
  }

  @Test
  fun `encode() - encodes open polymorphic map values as their serial names and values`() {
    val entry = WithOriginMap(mapOf("first" to Search("shoes")))

    val encoded = encodeToMap(WithOriginMap.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "byName" to
            mapOf("first" to mapOf("type" to "search", "value" to mapOf("term" to "shoes"))),
        ),
      )
  }

  @Test
  fun `encode() - encodes open polymorphic map keys as structured keys of their serial names and values`() {
    val entry = WithOriginMapKeys(mapOf(Search("shoes") to 1))

    val encoded = encodeToMap(WithOriginMapKeys.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "byOrigin" to
            listOf(
              mapOf(
                "key" to mapOf("type" to "search", "value" to mapOf("term" to "shoes")),
                "value" to 1,
              ),
            ),
        ),
      )
  }

  @Test
  fun `encode() - encodes an open polymorphic property of an open polymorphic value's runtime class`() {
    val entry = WithOrigin("1", Referral(from = Search("shoes")), page = 2)

    val encoded = encodeToMap(WithOrigin.serializer(), entry)

    assertThat(encoded["origin"])
      .isEqualTo(
        mapOf(
          "type" to "referral",
          "value" to
            mapOf("from" to mapOf("type" to "search", "value" to mapOf("term" to "shoes"))),
        ),
      )
  }

  // Runtime classes without a serializer to find

  @Test
  fun `encode() - records an open polymorphic value that isn't @Serializable as the Unencodable substitute`() {
    val entry = WithOrigin("1", PlainOrigin(), page = 2)

    val encoded = encodeToMap(WithOrigin.serializer(), entry, unencodableValues::add)

    assertThat(encoded)
      .isEqualTo(mapOf("id" to "1", "origin" to UNENCODABLE_SUBSTITUTE, "page" to 2))
    assertThat(unencodableValues).hasSize(1)
    assertThat(unencodableValues[0]).isInstanceOf(SerializationException::class.java)
  }

  @Test
  fun `encode() - records an open polymorphic enum that isn't @Serializable as the Unencodable substitute`() {
    val entry = WithOrigin("1", UnannotatedSource.PUSH, page = 2)

    val encoded = encodeToMap(WithOrigin.serializer(), entry, unencodableValues::add)

    assertThat(encoded["origin"]).isEqualTo(UNENCODABLE_SUBSTITUTE)
    assertThat(unencodableValues).hasSize(1)
  }

  @Test
  fun `encode() - records a @Polymorphic Any property holding a built-in type as the Unencodable substitute`() {
    val encoded = encodeToMap(WithAny.serializer(), WithAny("1", "text"), unencodableValues::add)

    assertThat(encoded).isEqualTo(mapOf("id" to "1", "extra" to UNENCODABLE_SUBSTITUTE))
    assertThat(unencodableValues).hasSize(1)
  }

  /** The constant's runtime class is an anonymous subclass, which isn't `@Serializable`. */
  @Test
  fun `encode() - records an open polymorphic enum constant with a body as the Unencodable substitute`() {
    val entry = WithOrigin("1", SourceWithBodies.PUSH, page = 2)

    val encoded = encodeToMap(WithOrigin.serializer(), entry, unencodableValues::add)

    assertThat(encoded["origin"]).isEqualTo(UNENCODABLE_SUBSTITUTE)
    assertThat(unencodableValues).hasSize(1)
  }

  @Test
  fun `encode() - records an open polymorphic value of a generic class as the Unencodable substitute`() {
    val entry = WithOrigin("1", GenericOrigin("x"), page = 2)

    val encoded = encodeToMap(WithOrigin.serializer(), entry, unencodableValues::add)

    assertThat(encoded)
      .isEqualTo(mapOf("id" to "1", "origin" to UNENCODABLE_SUBSTITUTE, "page" to 2))
    assertThat(unencodableValues).hasSize(1)
  }

  @Test
  fun `encode() - records an open polymorphic value whose serializer init throws as the Unencodable substitute`() {
    val entry = WithOrigin("1", UninitializableOrigin(), page = 2)

    val encoded = encodeToMap(WithOrigin.serializer(), entry, unencodableValues::add)

    assertThat(encoded)
      .isEqualTo(mapOf("id" to "1", "origin" to UNENCODABLE_SUBSTITUTE, "page" to 2))
    assertThat(unencodableValues).hasSize(1)
    assertThat(unencodableValues[0]).isInstanceOf(SerializationException::class.java)
  }

  @Test
  fun `encode() - records an open polymorphic value whose serializer fails to link as the Unencodable substitute`() {
    val entry = WithOrigin("1", UnlinkableOrigin(), page = 2)

    val encoded = encodeToMap(WithOrigin.serializer(), entry, unencodableValues::add)

    assertThat(encoded)
      .isEqualTo(mapOf("id" to "1", "origin" to UNENCODABLE_SUBSTITUTE, "page" to 2))
    assertThat(unencodableValues).hasSize(1)
  }

  @Test
  fun `encode() - propagates a fatal error from an open polymorphic value's serializer init`() {
    val entry = WithOrigin("1", FatallyUninitializableOrigin(), page = 2)

    val thrown =
      assertFailsWith<OutOfMemoryError> {
        encodeToMap(WithOrigin.serializer(), entry, unencodableValues::add)
      }

    assertThat(thrown).hasMessageThat().isEqualTo("serializer init failed fatally")
    assertThat(unencodableValues).isEmpty()
  }

  @Test
  fun `encode() - looks up an open polymorphic value's serializer again after its lookup throws a fatal error`() {
    val entry = WithOrigin("1", FatalOnceOrigin(), page = 2)
    assertFailsWith<OutOfMemoryError> { encodeToMap(WithOrigin.serializer(), entry) }

    val encoded = encodeToMap(WithOrigin.serializer(), entry, unencodableValues::add)

    assertThat(encoded["origin"])
      .isEqualTo(mapOf("type" to "fatalOnceOrigin", "value" to "encoded"))
    assertThat(unencodableValues).isEmpty()
  }

  @Test
  fun `encode() - substitutes only the unencodable property inside an open polymorphic value`() {
    val entry = WithOrigin("1", ContextualOrigin("2", Unregistered("3")), page = 2)

    val encoded = encodeToMap(WithOrigin.serializer(), entry, unencodableValues::add)

    assertThat(encoded["origin"])
      .isEqualTo(
        mapOf(
          "type" to "withContextual",
          "value" to mapOf("id" to "2", "other" to UNENCODABLE_SUBSTITUTE),
        ),
      )
    assertThat(unencodableValues).hasSize(1)
  }

  /**
   * The type is written before the runtime class's serializer runs, and the value element is
   * substituted like any other, so the value keeps its type.
   */
  @Test
  fun `encode() - records the value of an open polymorphic value whose found serializer fails as the Unencodable substitute, keeping its type`() {
    val entry = WithOrigin("1", FailingOrigin(), page = 2)

    val encoded = encodeToMap(WithOrigin.serializer(), entry, unencodableValues::add)

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "id" to "1",
          "origin" to mapOf("type" to "failingOrigin", "value" to UNENCODABLE_SUBSTITUTE),
          "page" to 2,
        ),
      )
    assertThat(unencodableValues).hasSize(1)
    assertThat(unencodableValues[0]).hasMessageThat().isEqualTo("failing origin")
  }

  // Sensitive data and limits

  @Test
  fun `encode() - replaces sensitive properties of an open polymorphic value with the Filtered substitute`() {
    val entry = WithOrigin("1", Login(user = "jane", token = "abc"), page = 2)

    val encoded = encodeToMap(WithOrigin.serializer(), entry)

    assertThat(encoded["origin"])
      .isEqualTo(
        mapOf(
          "type" to "login",
          "value" to mapOf("user" to "jane", "token" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE),
        ),
      )
  }

  @Test
  fun `encode() - drops the arguments for count when an open polymorphic value has too many values`() {
    val entry = WithOrigin("1", Tagged(List(MAX_ARGUMENT_VALUES) { it }), page = 2)

    val exception = assertFailsWith<DropArguments> { encodeToMap(WithOrigin.serializer(), entry) }

    assertThat(exception.reason).isEqualTo(ArgumentDropReason.MAX_COUNT)
  }
}
