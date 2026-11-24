package io.sentry.compose.navigation3.internal.backstack.kotlinx

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.internal.backstack.ArgumentDropReason
import io.sentry.compose.navigation3.internal.backstack.DropArguments
import java.math.BigInteger
import kotlin.jvm.JvmInline
import kotlin.test.assertFailsWith
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
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

  @Serializable
  enum class Sort {
    @SerialName("price_asc") PRICE_ASC,
    NEWEST,
  }

  @Serializable data class WithEnums(val primary: Sort, val secondary: Sort)

  @Serializable @JvmInline value class ProductId(val value: String)

  @Serializable data class WithValueClass(val productId: ProductId)

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

  @Serializable
  data class WithSignedNumbers(val byte: Byte, val short: Short, val int: Int, val long: Long)

  @Serializable data class WithIntMapKeys(val counts: Map<Int, Long>)

  @Serializable
  data class WithEncodedMapKeys(val bySort: Map<Sort, Int>, val byRank: Map<UInt, Int>)

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

  @Serializable data class WithStructureWritingMapKeys(val byCode: Map<StructureWritingKey, Int>)

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
  fun `encode() - wraps a top-level value that doesn't encode to a structure under the value key`() {
    val encoded = encodeToMap(CustomSerializer, CustomSerialized("abc"))

    assertThat(encoded).isEqualTo(mapOf(KotlinxPropertiesEncoder.VALUE_KEY to "abc"))
  }

  @Test
  fun `encode() - wraps a top-level list under the value key`() {
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
  fun `encode() - wraps a top-level value class's underlying value under the value key`() {
    val encoded = encodeToMap(ProductId.serializer(), ProductId("p-1"))

    assertThat(encoded).isEqualTo(mapOf(KotlinxPropertiesEncoder.VALUE_KEY to "p-1"))
  }

  @Test
  fun `encode() - wraps a top-level enum's serial name under the value key`() {
    val encoded = encodeToMap(Sort.serializer(), Sort.PRICE_ASC)

    assertThat(encoded).isEqualTo(mapOf(KotlinxPropertiesEncoder.VALUE_KEY to "price_asc"))
  }

  @Test
  fun `encode() - encodes a top-level polymorphic value's serial name and value as the arguments`() {
    val encoded = encodeToMap(Origin.serializer(), Origin.Search("shoes"))

    assertThat(encoded).isEqualTo(mapOf("type" to "search", "value" to mapOf("query" to "shoes")))
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
  fun `encode() - wraps a top-level unsigned number's unsigned value under the value key`() {
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
  fun `encode() - encodes negative signed numbers as-is`() {
    val entry = WithSignedNumbers(byte = -1, short = -1, int = -1, long = -1L)

    val encoded = encodeToMap(WithSignedNumbers.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf("byte" to (-1).toByte(), "short" to (-1).toShort(), "int" to -1, "long" to -1L),
      )
  }

  // Map keys

  @Test
  fun `encode() - converts map keys to strings`() {
    val encoded = encodeToMap(WithIntMapKeys.serializer(), WithIntMapKeys(mapOf(1 to 2L)))

    assertThat(encoded).isEqualTo(mapOf("counts" to mapOf("1" to 2L)))
  }

  @Test
  fun `encode() - converts enum and unsigned map keys to strings of their encoded values`() {
    val entry =
      WithEncodedMapKeys(bySort = mapOf(Sort.PRICE_ASC to 1), byRank = mapOf(UInt.MAX_VALUE to 2))

    val encoded = encodeToMap(WithEncodedMapKeys.serializer(), entry)

    assertThat(encoded)
      .isEqualTo(
        mapOf("bySort" to mapOf("price_asc" to 1), "byRank" to mapOf("4294967295" to 2)),
      )
  }

  @Test
  fun `encode() - converts a null map key to the string null`() {
    val encoded =
      encodeToMap(WithNullableMapKeys.serializer(), WithNullableMapKeys(mapOf(null to 1)))

    assertThat(encoded).isEqualTo(mapOf("counts" to mapOf("null" to 1)))
  }

  @Test
  fun `encode() - converts value class and custom serializer map keys to strings`() {
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

    val exception =
      assertFailsWith<DropArguments> {
        encodeToMap(WithNullableMapKeys.serializer(), entry)
      }

    assertThat(exception.reason).isEqualTo(ArgumentDropReason.DUPLICATE_KEY)
  }

  @Test
  fun `encode() - drops the arguments for a duplicate key when a custom serializer encodes two keys as one string`() {
    val entry =
      WithVersionedNameMapKeys(mapOf(VersionedName("home", 1) to 1, VersionedName("home", 2) to 2))

    val exception =
      assertFailsWith<DropArguments> {
        encodeToMap(WithVersionedNameMapKeys.serializer(), entry)
      }

    assertThat(exception.reason).isEqualTo(ArgumentDropReason.DUPLICATE_KEY)
  }

  @Test
  fun `encode() - throws for a map key whose serializer begins a structure despite a scalar descriptor`() {
    val entry = WithStructureWritingMapKeys(mapOf(StructureWritingKey("a") to 1))

    assertFailsWith<SerializationException> {
      encodeToMap(WithStructureWritingMapKeys.serializer(), entry)
    }
  }

  // Serializer failures

  @Test
  fun `encode() - propagates a serializer's exception unchanged`() {
    val error = IllegalStateException("boom")
    val serializer = RecordingSerializer(Detail.serializer()).apply { this.error = error }

    val thrown = assertFailsWith<IllegalStateException> { encodeToMap(serializer, Detail("1")) }

    assertThat(thrown).isSameInstanceAs(error)
  }
}
