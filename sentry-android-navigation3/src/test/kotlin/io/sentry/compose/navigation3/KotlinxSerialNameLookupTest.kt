package io.sentry.compose.navigation3

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.junit.Test

class KotlinxSerialNameLookupTest {

  @Serializable data object Home

  @Serializable @SerialName("ProductDetail") data class Detail(val productId: String)

  @Serializable
  sealed interface Route {
    @Serializable data object Settings : Route

    @Serializable @SerialName("route.profile") data class Profile(val userId: String) : Route
  }

  @Serializable
  enum class Tab {
    FEED,
    SEARCH,
  }

  enum class UnannotatedTab {
    FEED
  }

  data class NotSerializable(val id: String)

  @Serializable private data object PrivateObject

  @Serializable private data class PrivateClass(val id: String)

  @Serializable data class Generic<T>(val value: T)

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

  private val sut = KotlinxSerialNameLookup

  @Test
  fun `uses the fully qualified name when there is no SerialName`() {
    assertThat(sut.serialNameOrNull(Home::class.java))
      .isEqualTo("io.sentry.compose.navigation3.KotlinxSerialNameLookupTest.Home")
  }

  @Test
  fun `uses the SerialName unchanged when present`() {
    assertThat(sut.serialNameOrNull(Detail::class.java)).isEqualTo("ProductDetail")
    assertThat(sut.serialNameOrNull(Route.Profile::class.java)).isEqualTo("route.profile")
  }

  @Test
  fun `names sealed subtypes after their own serial names`() {
    assertThat(sut.serialNameOrNull(Route.Settings::class.java))
      .isEqualTo("io.sentry.compose.navigation3.KotlinxSerialNameLookupTest.Route.Settings")
  }

  @Test
  fun `names Serializable enums`() {
    assertThat(sut.serialNameOrNull(Tab::class.java))
      .isEqualTo("io.sentry.compose.navigation3.KotlinxSerialNameLookupTest.Tab")
  }

  @Test
  fun `uses the custom serializer's serial name`() {
    assertThat(sut.serialNameOrNull(CustomSerialized::class.java)).isEqualTo("custom.Name")
  }

  @Test
  fun `returns null for classes that aren't Serializable`() {
    assertThat(sut.serialNameOrNull(NotSerializable::class.java)).isNull()
  }

  @Test
  fun `returns null for built-in types and unannotated enums`() {
    assertThat(sut.serialNameOrNull(String::class.java)).isNull()
    assertThat(sut.serialNameOrNull(Int::class.javaObjectType)).isNull()
    assertThat(sut.serialNameOrNull(ArrayList::class.java)).isNull()
    assertThat(sut.serialNameOrNull(UnannotatedTab::class.java)).isNull()
  }

  @Test
  fun `names private objects`() {
    assertThat(sut.serialNameOrNull(PrivateObject::class.java))
      .isEqualTo("io.sentry.compose.navigation3.KotlinxSerialNameLookupTest.PrivateObject")
  }

  @Test
  fun `names private classes`() {
    assertThat(sut.serialNameOrNull(PrivateClass::class.java))
      .isEqualTo("io.sentry.compose.navigation3.KotlinxSerialNameLookupTest.PrivateClass")
  }

  @Test
  fun `returns null for generic classes`() {
    assertThat(sut.serialNameOrNull(Generic::class.java)).isNull()
  }
}
