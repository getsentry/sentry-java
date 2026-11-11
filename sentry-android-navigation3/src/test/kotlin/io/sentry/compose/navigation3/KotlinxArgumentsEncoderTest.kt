package io.sentry.compose.navigation3

import com.google.common.truth.Truth.assertThat
import kotlin.jvm.JvmInline
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.junit.Test

class KotlinxArgumentsEncoderTest {

  @Serializable data object Home

  @Serializable
  enum class Sort {
    @SerialName("price_asc") PRICE_ASC,
    NEWEST,
  }

  @Serializable data class Filters(val minPrice: Int, val brands: List<String>)

  @Serializable @JvmInline value class ProductId(val value: String)

  @Serializable
  sealed interface Origin {
    @Serializable @SerialName("search") data class Search(val query: String) : Origin

    @Serializable @SerialName("deeplink") data object DeepLink : Origin
  }

  @Serializable
  data class Search(
    val query: String,
    val page: Int = 1,
    val exact: Boolean,
    val score: Double,
    val initial: Char,
    val sort: Sort,
    val filters: Filters,
    val tags: Set<String>,
    val counts: Map<Int, Long>,
    val productId: ProductId,
    val previous: String?,
    @Transient val cached: String = "not recorded",
  )

  @Serializable data class Detail(val productId: String, val origin: Origin)

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

  class Unregistered(val id: String)

  @Serializable data class WithContextual(val id: String, @Contextual val other: Unregistered)

  interface OpenOrigin

  @Serializable data class WithOpenPolymorphic(val id: String, @Polymorphic val origin: OpenOrigin)

  private val sut = KotlinxSerialNameLookup

  @Test
  fun `encodes properties as plain maps lists and scalars`() {
    val key =
      Search(
        query = "shoes",
        exact = false,
        score = 0.5,
        initial = 's',
        sort = Sort.PRICE_ASC,
        filters = Filters(minPrice = 10, brands = listOf("a", "b")),
        tags = setOf("new"),
        counts = mapOf(1 to 2L),
        productId = ProductId("p-1"),
        previous = null,
      )

    assertThat(sut.argumentsOrNull(key))
      .isEqualTo(
        mapOf(
          "query" to "shoes",
          "page" to 1,
          "exact" to false,
          "score" to 0.5,
          "initial" to 's',
          "sort" to "price_asc",
          "filters" to mapOf("minPrice" to 10, "brands" to listOf("a", "b")),
          "tags" to listOf("new"),
          "counts" to mapOf("1" to 2L),
          "productId" to "p-1",
          "previous" to null,
        )
      )
  }

  @Test
  fun `encodes polymorphic properties with their serial name`() {
    assertThat(sut.argumentsOrNull(Detail("p-1", Origin.Search("shoes"))))
      .isEqualTo(
        mapOf(
          "productId" to "p-1",
          "origin" to mapOf("type" to "search", "value" to mapOf("query" to "shoes")),
        )
      )
    assertThat(sut.argumentsOrNull(Detail("p-1", Origin.DeepLink)))
      .isEqualTo(
        mapOf(
          "productId" to "p-1",
          "origin" to mapOf("type" to "deeplink", "value" to emptyMap<String, Any?>()),
        )
      )
  }

  @Test
  fun `encodes objects as empty maps`() {
    assertThat(sut.argumentsOrNull(Home)).isEmpty()
  }

  @Test
  fun `wraps values that don't encode to a structure`() {
    assertThat(sut.argumentsOrNull(CustomSerialized("abc"))).isEqualTo(mapOf("value" to "abc"))
  }

  @Test
  fun `returns null for contextual properties without a registered serializer`() {
    assertThat(sut.argumentsOrNull(WithContextual("1", Unregistered("2")))).isNull()
  }

  @Test
  fun `returns null for open polymorphic properties`() {
    assertThat(sut.argumentsOrNull(WithOpenPolymorphic("1", object : OpenOrigin {}))).isNull()
  }

  @Test
  fun `returns null for types without a serializer`() {
    assertThat(sut.argumentsOrNull("home")).isNull()
    assertThat(sut.argumentsOrNull(Unregistered("1"))).isNull()
  }
}
