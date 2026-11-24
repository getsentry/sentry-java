package io.sentry.compose.navigation3.internal.backstack.kotlinx

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxPropertiesEncoder.MAX_SERIALIZER_NESTING
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxPropertiesEncoder.UNENCODABLE_SUBSTITUTE
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxPropertiesEncoder.VALUE_KEY
import io.sentry.util.UrlUtils
import java.lang.reflect.Proxy
import kotlin.jvm.JvmInline
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.junit.Test

/**
 * Covers maps whose keys are structures (classes, objects, collections, or polymorphic values),
 * which [KotlinxPropertiesEncoder] records as [UNENCODABLE_SUBSTITUTE], as their keys can't be
 * recorded as text. Scalar map keys are covered by [KotlinxPropertiesEncoderTest].
 */
class KotlinxPropertiesEncoderStructuredMapKeysTest {

  @Serializable data class Point(val x: Int)

  @Serializable @SerialName("everywhere") data object Everywhere

  @Serializable @JvmInline value class PointRef(val point: Point)

  @Serializable
  sealed interface Origin {
    @Serializable @SerialName("search") data class Search(val query: String) : Origin
  }

  interface OpenOrigin

  @Serializable @SerialName("openSearch") data class OpenSearch(val query: String) : OpenOrigin

  /** A map between two other properties, so tests can check that those are kept. */
  @Serializable data class WithKeyedMap<K>(val id: String, val counts: Map<K, Int>, val page: Int)

  /** Has a map under a sensitive property name (`credentials`). */
  @Serializable data class WithSensitiveKeyedMap(val credentials: Map<Point, Int>)

  @Serializable
  data class WithThrowingMapEntries(
    val id: String,
    val byPoint:
      Map<
        @Serializable(ThrowingPointSerializer::class)
        Point,
        @Serializable(ThrowingSerializer::class)
        String,
      >,
  )

  /** Fails the test if a key it should never see is encoded. */
  object ThrowingPointSerializer : KSerializer<Point> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("test.ThrowingPoint")

    override fun serialize(encoder: Encoder, value: Point): Unit = error("key serializer ran")

    override fun deserialize(decoder: Decoder): Point = throw UnsupportedOperationException()
  }

  /**
   * A [descriptor] of a value class that wraps [wrapped], as a custom descriptor may claim to, even
   * when [wrapped] is [descriptor] itself.
   *
   * Implemented with a [Proxy] because kotlinx.serialization 1.8+ requires an opt-in to implement
   * [SerialDescriptor] in Kotlin, and the opt-in's marker doesn't exist in the 1.7.3 floor that the
   * tests also compile against.
   */
  private class ValueClassDescriptor(private val serialName: String) {
    lateinit var wrapped: SerialDescriptor

    val descriptor: SerialDescriptor =
      Proxy.newProxyInstance(
        SerialDescriptor::class.java.classLoader,
        arrayOf(SerialDescriptor::class.java),
      ) { proxy, method, args ->
        when (method.name) {
          "getSerialName",
          "toString" -> serialName
          "getKind" -> StructureKind.CLASS
          "isInline" -> true
          "isNullable",
          "isElementOptional" -> false
          "getElementsCount" -> 1
          "getElementName" -> "value"
          "getElementIndex" -> if (args[0] == "value") 0 else CompositeDecoder.UNKNOWN_NAME
          "getAnnotations",
          "getElementAnnotations" -> emptyList<Annotation>()
          "getElementDescriptor" -> wrapped
          "equals" -> proxy === args[0]
          "hashCode" -> System.identityHashCode(proxy)
          else -> throw UnsupportedOperationException("SerialDescriptor.${method.name}")
        }
      } as SerialDescriptor
  }

  /**
   * Encodes keys with [delegate] while claiming [descriptor], which describes value classes that
   * wrap [delegate]'s type.
   */
  private class WrappedKeySerializer<T>(
    override val descriptor: SerialDescriptor,
    private val delegate: KSerializer<T>,
  ) : KSerializer<T> {
    override fun serialize(encoder: Encoder, value: T) {
      encoder.encodeSerializableValue(delegate, value)
    }

    override fun deserialize(decoder: Decoder): T = throw UnsupportedOperationException()
  }

  private var unencodableValueCount = 0
  private val onUnencodableValue: () -> Unit = { unencodableValueCount++ }

  /** The arguments of a [WithKeyedMap] whose map is recorded as [UNENCODABLE_SUBSTITUTE]. */
  private val unencodableMapArguments =
    mapOf("id" to "1", "counts" to UNENCODABLE_SUBSTITUTE, "page" to 2)

  // Maps with structured keys

  @Test
  fun `encode() - records a map with class keys as the Unencodable substitute, keeping the other properties`() {
    val encoded = encodeKeyedMap(Point.serializer(), mapOf(Point(1) to 1, Point(2) to 2))

    assertThat(encoded).isEqualTo(unencodableMapArguments)
  }

  @Test
  fun `encode() - records a map with object keys as the Unencodable substitute`() {
    val encoded = encodeKeyedMap(Everywhere.serializer(), mapOf(Everywhere to 1))

    assertThat(encoded).isEqualTo(unencodableMapArguments)
  }

  @Test
  fun `encode() - records a map with list keys as the Unencodable substitute`() {
    val encoded = encodeKeyedMap(ListSerializer(String.serializer()), mapOf(listOf("a") to 1))

    assertThat(encoded).isEqualTo(unencodableMapArguments)
  }

  @Test
  fun `encode() - records a map with map keys as the Unencodable substitute`() {
    val keySerializer = MapSerializer(String.serializer(), Int.serializer())

    val encoded = encodeKeyedMap(keySerializer, mapOf(mapOf("a" to 1) to 1))

    assertThat(encoded).isEqualTo(unencodableMapArguments)
  }

  @Test
  fun `encode() - records a map with sealed keys as the Unencodable substitute`() {
    val encoded = encodeKeyedMap(Origin.serializer(), mapOf(Origin.Search("shoes") to 1))

    assertThat(encoded).isEqualTo(unencodableMapArguments)
  }

  @Test
  fun `encode() - records a map with open polymorphic keys as the Unencodable substitute`() {
    val keySerializer = PolymorphicSerializer(OpenOrigin::class)

    val encoded = encodeKeyedMap(keySerializer, mapOf(OpenSearch("shoes") to 1))

    assertThat(encoded).isEqualTo(unencodableMapArguments)
  }

  @Test
  fun `encode() - records a map with value class keys wrapping a class as the Unencodable substitute`() {
    val encoded = encodeKeyedMap(PointRef.serializer(), mapOf(PointRef(Point(1)) to 1))

    assertThat(encoded).isEqualTo(unencodableMapArguments)
  }

  @Test
  fun `encode() - records an empty map with class keys as the Unencodable substitute`() {
    val encoded = encodeKeyedMap(Point.serializer(), emptyMap())

    assertThat(encoded).isEqualTo(unencodableMapArguments)
  }

  @Test
  fun `encode() - records a top-level map with class keys as the Unencodable substitute under the value key`() {
    val serializer = MapSerializer(Point.serializer(), Int.serializer())

    val encoded = encodeToMap(serializer, mapOf(Point(1) to 2), onUnencodableValue)

    assertThat(encoded).isEqualTo(mapOf(VALUE_KEY to UNENCODABLE_SUBSTITUTE))
    assertThat(unencodableValueCount).isEqualTo(1)
  }

  @Test
  fun `encode() - doesn't run the key or value serializers of a map with class keys`() {
    val entry = WithThrowingMapEntries("1", mapOf(Point(1) to "a", Point(2) to "b"))

    val encoded = encodeToMap(WithThrowingMapEntries.serializer(), entry)

    assertThat(encoded).isEqualTo(mapOf("id" to "1", "byPoint" to UNENCODABLE_SUBSTITUTE))
  }

  @Test
  fun `encode() - reports one unencodable value for a map with several class keys`() {
    encodeKeyedMap(Point.serializer(), mapOf(Point(1) to 1, Point(2) to 2, Point(3) to 3))

    assertThat(unencodableValueCount).isEqualTo(1)
  }

  @Test
  fun `encode() - filters a map with class keys under a sensitive property without reporting an unencodable value`() {
    val entry = WithSensitiveKeyedMap(mapOf(Point(1) to 1))

    val encoded = encodeToMap(WithSensitiveKeyedMap.serializer(), entry, onUnencodableValue)

    assertThat(encoded).isEqualTo(mapOf("credentials" to UrlUtils.SENSITIVE_DATA_SUBSTITUTE))
    assertThat(unencodableValueCount).isEqualTo(0)
  }

  // Maps with scalar keys

  @Test
  fun `encode() - encodes a map with contextual keys as a map, as contextual keys count as scalars`() {
    val encoded = encodeKeyedMap(ContextualSerializer(Unregistered::class), emptyMap())

    assertThat(encoded)
      .isEqualTo(mapOf("id" to "1", "counts" to emptyMap<String, Any?>(), "page" to 2))
    assertThat(unencodableValueCount).isEqualTo(0)
  }

  // Value class key unwrapping

  @Test
  fun `encode() - encodes a map with keys wrapping the maximum value classes around a string as a map`() {
    val keySerializer =
      WrappedKeySerializer(
        valueClassDescriptors(MAX_SERIALIZER_NESTING, String.serializer()),
        String.serializer(),
      )

    val encoded = encodeKeyedMap(keySerializer, mapOf("a" to 1))

    assertThat(encoded).isEqualTo(mapOf("id" to "1", "counts" to mapOf("a" to 1), "page" to 2))
    assertThat(unencodableValueCount).isEqualTo(0)
  }

  @Test
  fun `encode() - records a map with keys wrapping too many value classes as the Unencodable substitute`() {
    val keySerializer =
      WrappedKeySerializer(
        valueClassDescriptors(MAX_SERIALIZER_NESTING + 1, String.serializer()),
        String.serializer(),
      )

    val encoded = encodeKeyedMap(keySerializer, mapOf("a" to 1))

    assertThat(encoded).isEqualTo(unencodableMapArguments)
    assertThat(unencodableValueCount).isEqualTo(1)
  }

  @Test
  fun `encode() - records a map whose key descriptor wraps itself as the Unencodable substitute`() {
    val keyDescriptor =
      ValueClassDescriptor("test.SelfWrapping").apply { wrapped = descriptor }.descriptor
    val keySerializer = WrappedKeySerializer(keyDescriptor, String.serializer())

    val encoded = encodeKeyedMap(keySerializer, mapOf("a" to 1))

    assertThat(encoded).isEqualTo(unencodableMapArguments)
    assertThat(unencodableValueCount).isEqualTo(1)
  }

  /** Encodes a [WithKeyedMap] with id `1`, [counts], and page `2`. */
  private fun <K> encodeKeyedMap(keySerializer: KSerializer<K>, counts: Map<K, Int>) =
    encodeToMap(
      WithKeyedMap.serializer(keySerializer),
      WithKeyedMap("1", counts, page = 2),
      onUnencodableValue,
    )

  /** Returns descriptors of [count] value classes, each wrapping the next, around [innermost]. */
  private fun valueClassDescriptors(count: Int, innermost: KSerializer<*>): SerialDescriptor {
    var descriptor = innermost.descriptor
    repeat(count) { level ->
      val wrapped = descriptor
      descriptor =
        ValueClassDescriptor("test.ValueClass$level").apply { this.wrapped = wrapped }.descriptor
    }
    return descriptor
  }
}
