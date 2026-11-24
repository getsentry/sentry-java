package io.sentry.compose.navigation3.internal.backstack.kotlinx

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.internal.backstack.ArgumentDropReason
import java.math.BigInteger
import kotlin.jvm.JvmInline
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.junit.Test

class KotlinxPropertiesEncoderTest {

  @Serializable
  data class Product(
    val name: String,
    val inStock: Boolean,
    val price: Double,
    val grade: Char,
    val quantity: Long,
    val note: String?,
  )

  @Serializable data class Filters(val minPrice: Int, val brands: List<String>)

  @Serializable data class WithNestedClass(val query: String, val filters: Filters)

  @Serializable data class WithCollections(val names: List<String?>, val tags: Set<String>)

  @Serializable internal data class WithEnums(val primary: Sort, val secondary: Sort)

  @Serializable @JvmInline value class ProductId(val value: String)

  @Serializable data class WithValueClass(val productId: ProductId)

  @Serializable @JvmInline value class Price(val cents: Int)

  @Serializable data class WithNullablePrice(val price: Price?)

  @Serializable @JvmInline value class MaybeId(val value: String?)

  @Serializable data class WithMaybeId(val maybeId: MaybeId)

  @Serializable @JvmInline value class Rank(val value: UInt)

  @Serializable data class WithRank(val rank: Rank)

  @Serializable data class WithDefault(val query: String, val page: Int = 1)

  @Serializable
  data class WithTransient(val query: String, @Transient val cached: String = "not recorded")

  @Serializable
  sealed interface Origin {
    @Serializable @SerialName("search") data class Search(val query: String) : Origin

    @Serializable @SerialName("deeplink") data object DeepLink : Origin
  }

  @Serializable data class WithOrigin(val productId: String, val origin: Origin)

  @Serializable(with = CustomSerializer::class) data class CustomSerialized(val id: String)

  object CustomSerializer : KSerializer<CustomSerialized> {
    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("custom.Name", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CustomSerialized) {
      encoder.encodeString(value.id)
    }

    override fun deserialize(decoder: Decoder): CustomSerialized =
      CustomSerialized(decoder.decodeString())
  }

  @Serializable
  data class WithUnsignedNumbers(
    val uByte: UByte,
    val uShort: UShort,
    val uInt: UInt,
    val uLong: ULong,
  )

  @Serializable data class WithULong(val uLong: ULong)

  @Serializable data class WithULongList(val uLongs: List<ULong>)

  @Serializable
  data class WithSignedNumbers(val byte: Byte, val short: Short, val int: Int, val long: Long)

  @Serializable data class WithFloatingPointNumbers(val float: Float, val double: Double)

  @Serializable
  data class WithNonFiniteNumbers(
    val floatNaN: Float,
    val floatInfinity: Float,
    val doubleNegativeInfinity: Double,
  )

  @Serializable data class WithIntMapKeys(val counts: Map<Int, Long>)

  @Serializable
  data class WithUnsignedMapKeys(
    val byUByte: Map<UByte, Int>,
    val byUShort: Map<UShort, Int>,
    val byULong: Map<ULong, Int>,
  )

  @Serializable
  internal data class WithEncodedMapKeys(val bySort: Map<Sort, Int>, val byRank: Map<UInt, Int>)

  @Serializable data class WithNullableMapKeys(val counts: Map<String?, Int>)

  @Serializable
  data class WithWrappedMapKeys(
    val byProduct: Map<ProductId, Int>,
    val byCustom: Map<CustomSerialized, Int>,
  )

  /** A key that's recorded by its [name] alone, so keys that differ only in [version] collide. */
  @Serializable(with = VersionedNameSerializer::class)
  data class VersionedName(val name: String, val version: Int)

  object VersionedNameSerializer : KSerializer<VersionedName> {
    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("custom.VersionedName", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: VersionedName) {
      encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): VersionedName =
      VersionedName(decoder.decodeString(), version = 0)
  }

  @Serializable data class WithVersionedNameMapKeys(val byName: Map<VersionedName, Int>)

  /** The Kotlin compiler plugin gives `CharSequence` properties an open polymorphic serializer. */
  @Serializable data class WithLabel(val label: CharSequence)

  @Serializable data class WithNullableLabel(val label: CharSequence?)

  @Serializable data class WithLabelList(val labels: List<CharSequence>)

  @Serializable data class WithLabelMapKeys(val countsByLabel: Map<CharSequence, Int>)

  @Serializable data class WithNullableLabelMapKeys(val countsByLabel: Map<CharSequence?, Int>)

  /** A `CharSequence` that isn't a `String`. */
  class Label(private val text: String) : CharSequence by text {
    override fun toString(): String = text
  }

  /** A `CharSequence` whose class is `@Serializable`, so it has a serializer of its own. */
  @Serializable
  @SerialName("slug")
  class Slug(val text: String) : CharSequence by text {
    override fun toString(): String = text
  }

  // Encoded shapes

  @Test
  fun `encode() - encodes a class as a map from property name to value`() {
    val entry =
      Product(
        name = "shoes",
        inStock = false,
        price = 0.5,
        grade = 'A',
        quantity = 3L,
        note = null,
      )

    val encoded = encodeToMap(Product.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "name" to "shoes",
          "inStock" to false,
          "price" to 0.5,
          "grade" to "A",
          "quantity" to 3L,
          "note" to null,
        ),
      )
  }

  @Test
  fun `encode() - encodes a nested class as a nested map`() {
    val entry = WithNestedClass("shoes", Filters(minPrice = 10, brands = listOf("a")))

    val encoded = encodeToMap(WithNestedClass.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf("query" to "shoes", "filters" to mapOf("minPrice" to 10, "brands" to listOf("a"))),
      )
  }

  @Test
  fun `encode() - encodes lists and sets as lists`() {
    val entry = WithCollections(names = listOf("a", null), tags = setOf("new", "sale"))

    val encoded = encodeToMap(WithCollections.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(mapOf("names" to listOf("a", null), "tags" to listOf("new", "sale")))
  }

  @Test
  fun `encode() - encodes enums by their serial name`() {
    val encoded = encodeToMap(WithEnums.serializer(), WithEnums(Sort.PRICE_ASC, Sort.NEWEST))

    assertThat(encoded).isEqualTo(mapOf("primary" to "price_asc", "secondary" to "NEWEST"))
  }

  @Test
  fun `encode() - encodes value classes by their underlying value`() {
    val encoded = encodeToMap(WithValueClass.serializer(), WithValueClass(ProductId("p-1")))

    assertThat(encoded).isEqualTo(mapOf("productId" to "p-1"))
  }

  @Test
  fun `encode() - encodes a nullable value class property holding a value by its underlying value`() {
    val encoded = encodeToMap(WithNullablePrice.serializer(), WithNullablePrice(Price(999)))

    assertThat(encoded).isEqualTo(mapOf("price" to 999))
  }

  @Test
  fun `encode() - encodes a null value class property as null`() {
    val encoded = encodeToMap(WithNullablePrice.serializer(), WithNullablePrice(null))

    assertThat(encoded).isEqualTo(mapOf("price" to null))
  }

  @Test
  fun `encode() - encodes a value class wrapping a nullable value that's present by its underlying value`() {
    val encoded = encodeToMap(WithMaybeId.serializer(), WithMaybeId(MaybeId("p-1")))

    assertThat(encoded).isEqualTo(mapOf("maybeId" to "p-1"))
  }

  @Test
  fun `encode() - encodes a value class wrapping a null value as null`() {
    val encoded = encodeToMap(WithMaybeId.serializer(), WithMaybeId(MaybeId(null)))

    assertThat(encoded).isEqualTo(mapOf("maybeId" to null))
  }

  @Test
  fun `encode() - encodes properties that have their default values`() {
    val encoded = encodeToMap(WithDefault.serializer(), WithDefault(query = "shoes"))

    assertThat(encoded).isEqualTo(mapOf("query" to "shoes", "page" to 1))
  }

  @Test
  fun `encode() - omits @Transient properties`() {
    val encoded = encodeToMap(WithTransient.serializer(), WithTransient(query = "shoes"))

    assertThat(encoded).isEqualTo(mapOf("query" to "shoes"))
  }

  @Test
  fun `encode() - encodes polymorphic properties as their serial name and value`() {
    val search = encodeToMap(WithOrigin.serializer(), WithOrigin("p-1", Origin.Search("shoes")))
    val deepLink = encodeToMap(WithOrigin.serializer(), WithOrigin("p-1", Origin.DeepLink))

    assertThat(search)
      .isEqualTo(
        mapOf(
          "productId" to "p-1",
          "origin" to mapOf("type" to "search", "value" to mapOf("query" to "shoes")),
        ),
      )
    assertThat(deepLink)
      .isEqualTo(
        mapOf(
          "productId" to "p-1",
          "origin" to mapOf("type" to "deeplink", "value" to emptyMap<String, Any?>()),
        ),
      )
  }

  @Test
  fun `encode() - encodes an object without properties as an empty map`() {
    val encoded = encodeToMap(Home.serializer(), Home)

    assertThat(encoded).isEmpty()
  }

  @Test
  fun `encode() - encodes a top-level value that doesn't encode to a structure under the value key`() {
    val encoded = encodeToMap(CustomSerializer, CustomSerialized("abc"))

    assertThat(encoded).isEqualTo(mapOf(KotlinxPropertiesEncoder.VALUE_KEY to "abc"))
  }

  @Test
  fun `encode() - encodes a top-level list under the value key`() {
    val encoded = encodeToMap(ListSerializer(String.serializer()), listOf("a", "b"))

    assertThat(encoded).isEqualTo(mapOf(KotlinxPropertiesEncoder.VALUE_KEY to listOf("a", "b")))
  }

  @Test
  fun `encode() - encodes a top-level map's entries as the arguments`() {
    val encoded =
      encodeToMap(MapSerializer(String.serializer(), Int.serializer()), mapOf("page" to 2))

    assertThat(encoded).isEqualTo(mapOf("page" to 2))
  }

  @Test
  fun `encode() - encodes a top-level value class's underlying value under the value key`() {
    val encoded = encodeToMap(ProductId.serializer(), ProductId("p-1"))

    assertThat(encoded).isEqualTo(mapOf(KotlinxPropertiesEncoder.VALUE_KEY to "p-1"))
  }

  @Test
  fun `encode() - encodes a top-level enum's serial name under the value key`() {
    val encoded = encodeToMap(Sort.serializer(), Sort.PRICE_ASC)

    assertThat(encoded).isEqualTo(mapOf(KotlinxPropertiesEncoder.VALUE_KEY to "price_asc"))
  }

  @Test
  fun `encode() - encodes a top-level polymorphic value's serial name and value as the arguments`() {
    val encoded = encodeToMap(Origin.serializer(), Origin.Search("shoes"))

    assertThat(encoded).isEqualTo(mapOf("type" to "search", "value" to mapOf("query" to "shoes")))
  }

  // CharSequences

  @Test
  fun `encode() - encodes a CharSequence property as a string of its text`() {
    val encoded = encodeToMap(WithLabel.serializer(), WithLabel(StringBuilder("shoes")))

    assertThat(encoded).isEqualTo(mapOf("label" to "shoes"))
  }

  @Test
  fun `encode() - encodes a CharSequence property holding a @Serializable CharSequence as a string of its text`() {
    val encoded = encodeToMap(WithLabel.serializer(), WithLabel(Slug("shoes")))

    assertThat(encoded).isEqualTo(mapOf("label" to "shoes"))
  }

  @Test
  fun `encode() - encodes a nullable CharSequence property holding a value as a string of its text`() {
    val encoded =
      encodeToMap(WithNullableLabel.serializer(), WithNullableLabel(StringBuilder("shoes")))

    assertThat(encoded).isEqualTo(mapOf("label" to "shoes"))
  }

  @Test
  fun `encode() - encodes a null CharSequence property as null`() {
    val encoded = encodeToMap(WithNullableLabel.serializer(), WithNullableLabel(null))

    assertThat(encoded).isEqualTo(mapOf("label" to null))
  }

  @Test
  fun `encode() - encodes CharSequence list elements as strings`() {
    val entry = WithLabelList(listOf("shoes", StringBuilder("hats"), Label("socks")))

    val encoded = encodeToMap(WithLabelList.serializer(), entry)

    assertThat(encoded).isEqualTo(mapOf("labels" to listOf("shoes", "hats", "socks")))
  }

  @Test
  fun `encode() - encodes CharSequence map keys as strings`() {
    val entry =
      WithLabelMapKeys(mapOf("shoes" to 1, StringBuilder("hats") to 2, Label("socks") to 3))

    val encoded = encodeToMap(WithLabelMapKeys.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(mapOf("countsByLabel" to mapOf("shoes" to 1, "hats" to 2, "socks" to 3)))
  }

  @Test
  fun `encode() - encodes nullable CharSequence map keys as strings, with null as the string null`() {
    val entry = WithNullableLabelMapKeys(mapOf(StringBuilder("shoes") to 1, null to 2))

    val encoded = encodeToMap(WithNullableLabelMapKeys.serializer(), entry)

    assertThat(encoded).isEqualTo(mapOf("countsByLabel" to mapOf("shoes" to 1, "null" to 2)))
  }

  @Test
  fun `encode() - encodes a top-level CharSequence's text under the value key`() {
    val encoded = encodeToMap(charSequenceSerializer, StringBuilder("shoes"))

    assertThat(encoded).isEqualTo(mapOf(KotlinxPropertiesEncoder.VALUE_KEY to "shoes"))
  }

  // Numbers

  @Test
  fun `encode() - encodes unsigned numbers by their unsigned value`() {
    val entry =
      WithUnsignedNumbers(
        uByte = UByte.MAX_VALUE,
        uShort = UShort.MAX_VALUE,
        uInt = UInt.MAX_VALUE,
        uLong = Long.MAX_VALUE.toULong(),
      )

    val encoded = encodeToMap(WithUnsignedNumbers.serializer(), entry)

    val uByteMax: Int = 255
    val uShortMax: Int = 65_535
    val uIntMax: Long = 4_294_967_295L
    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "uByte" to uByteMax,
          "uShort" to uShortMax,
          "uInt" to uIntMax,
          "uLong" to Long.MAX_VALUE,
        ),
      )
  }

  @Test
  fun `encode() - encodes a top-level unsigned number's unsigned value under the value key`() {
    val encoded = encodeToMap(UInt.serializer(), UInt.MAX_VALUE)

    val uIntMax: Long = 4_294_967_295L
    assertThat(encoded).isEqualTo(mapOf(KotlinxPropertiesEncoder.VALUE_KEY to uIntMax))
  }

  @Test
  fun `encode() - encodes a ULong above Long MAX_VALUE as a BigInteger`() {
    val encoded = encodeToMap(WithULong.serializer(), WithULong(ULong.MAX_VALUE))

    assertThat(encoded).isEqualTo(mapOf("uLong" to BigInteger("18446744073709551615")))
  }

  @Test
  fun `encode() - encodes a ULong list element above Long MAX_VALUE as a BigInteger`() {
    val encoded =
      encodeToMap(WithULongList.serializer(), WithULongList(listOf(1uL, ULong.MAX_VALUE)))

    assertThat(encoded).isEqualTo(mapOf("uLongs" to listOf(1L, BigInteger("18446744073709551615"))))
  }

  @Test
  fun `encode() - encodes a value class wrapping an unsigned number by its unsigned value`() {
    val encoded = encodeToMap(WithRank.serializer(), WithRank(Rank(UInt.MAX_VALUE)))

    val uIntMax: Long = 4_294_967_295L
    assertThat(encoded).isEqualTo(mapOf("rank" to uIntMax))
  }

  @Test
  fun `encode() - encodes negative signed numbers as-is`() {
    val entry = WithSignedNumbers(byte = -1, short = -1, int = -1, long = -1L)

    val encoded = encodeToMap(WithSignedNumbers.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf("byte" to (-1).toByte(), "short" to (-1).toShort(), "int" to -1, "long" to -1L),
      )
  }

  @Test
  fun `encode() - encodes Float and Double properties as-is`() {
    val entry = WithFloatingPointNumbers(float = 0.5f, double = -2.25)

    val encoded = encodeToMap(WithFloatingPointNumbers.serializer(), entry)

    assertThat(encoded).isEqualTo(mapOf("float" to 0.5f, "double" to -2.25))
  }

  /** JSON has no NaN or infinity, so the sink records them as text. */
  @Test
  fun `encode() - encodes NaN and infinite Float and Double properties as strings`() {
    val entry =
      WithNonFiniteNumbers(
        floatNaN = Float.NaN,
        floatInfinity = Float.POSITIVE_INFINITY,
        doubleNegativeInfinity = Double.NEGATIVE_INFINITY,
      )

    val encoded = encodeToMap(WithNonFiniteNumbers.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "floatNaN" to "NaN",
          "floatInfinity" to "Infinity",
          "doubleNegativeInfinity" to "-Infinity",
        ),
      )
  }

  // Map keys

  @Test
  fun `encode() - encodes map keys as strings`() {
    val encoded = encodeToMap(WithIntMapKeys.serializer(), WithIntMapKeys(mapOf(1 to 2L)))

    assertThat(encoded).isEqualTo(mapOf("counts" to mapOf("1" to 2L)))
  }

  @Test
  fun `encode() - encodes enum and unsigned map keys as strings of their encoded values`() {
    val entry =
      WithEncodedMapKeys(bySort = mapOf(Sort.PRICE_ASC to 1), byRank = mapOf(UInt.MAX_VALUE to 2))

    val encoded = encodeToMap(WithEncodedMapKeys.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf("bySort" to mapOf("price_asc" to 1), "byRank" to mapOf("4294967295" to 2)),
      )
  }

  @Test
  fun `encode() - encodes UByte, UShort, and ULong map keys as strings of their unsigned values`() {
    val entry =
      WithUnsignedMapKeys(
        byUByte = mapOf(UByte.MAX_VALUE to 1),
        byUShort = mapOf(UShort.MAX_VALUE to 2),
        byULong = mapOf(ULong.MAX_VALUE to 3),
      )

    val encoded = encodeToMap(WithUnsignedMapKeys.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf(
          "byUByte" to mapOf("255" to 1),
          "byUShort" to mapOf("65535" to 2),
          "byULong" to mapOf("18446744073709551615" to 3),
        ),
      )
  }

  @Test
  fun `encode() - encodes a null map key as the string null`() {
    val encoded =
      encodeToMap(WithNullableMapKeys.serializer(), WithNullableMapKeys(mapOf(null to 1)))

    assertThat(encoded).isEqualTo(mapOf("counts" to mapOf("null" to 1)))
  }

  @Test
  fun `encode() - encodes value class and custom serializer map keys as strings`() {
    val entry =
      WithWrappedMapKeys(
        byProduct = mapOf(ProductId("p-1") to 1),
        byCustom = mapOf(CustomSerialized("c-1") to 2),
      )

    val encoded = encodeToMap(WithWrappedMapKeys.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(mapOf("byProduct" to mapOf("p-1" to 1), "byCustom" to mapOf("c-1" to 2)))
  }

  @Test
  fun `encode() - drops the arguments for a duplicate key when a null map key and the string null are both keys`() {
    val entry = WithNullableMapKeys(mapOf(null to 1, "null" to 2))

    assertDropsArguments(ArgumentDropReason.DUPLICATE_KEY) {
      encodeToMap(WithNullableMapKeys.serializer(), entry)
    }
  }

  @Test
  fun `encode() - drops the arguments for a duplicate key when a custom serializer encodes two keys as one string`() {
    val entry =
      WithVersionedNameMapKeys(mapOf(VersionedName("home", 1) to 1, VersionedName("home", 2) to 2))

    assertDropsArguments(ArgumentDropReason.DUPLICATE_KEY) {
      encodeToMap(WithVersionedNameMapKeys.serializer(), entry)
    }
  }
}
