package io.sentry.compose.navigation3

import android.os.Parcel
import android.os.Parcelable
import android.util.Size
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.serialization.SavedStateConfiguration
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Checks that if Nav3 can save a destination, [BackStackEntryMapper.SerialNameAndProperties]
 * records its name and every property it can represent, with a placeholder for each one it can't.
 *
 * Destinations are saved as Nav3 1.1's `rememberNavBackStack` overloads save them: with
 * `encodeToSavedState`, a [NavBackStackSerializer], and either [NavKeySerializer] with the default
 * configuration, or a polymorphic [NavKey] serializer with an app-provided configuration.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class Nav3SavedStateParityTest {

  @Serializable
  @SerialName("Product")
  data class Product(val productId: String, val tags: List<String>, val page: Int) : NavKey

  data class PaymentMethod(val last4: String) : Parcelable {
    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
      dest.writeString(last4)
    }

    companion object CREATOR : Parcelable.Creator<PaymentMethod> {
      override fun createFromParcel(source: Parcel): PaymentMethod =
        PaymentMethod(source.readString()!!)

      override fun newArray(size: Int): Array<PaymentMethod?> = arrayOfNulls(size)
    }
  }

  @Serializable
  @SerialName("Checkout")
  data class Checkout(val orderId: String, val payment: Parcelable, val page: Int) : NavKey

  data class Money(val cents: Long)

  object MoneySerializer : KSerializer<Money> {
    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("test.Money", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Money) {
      encoder.encodeLong(value.cents)
    }

    override fun deserialize(decoder: Decoder): Money = Money(decoder.decodeLong())
  }

  @Serializable
  @SerialName("Cart")
  data class Cart(val cartId: String, @Contextual val total: Money, val page: Int) : NavKey

  @Serializable
  @SerialName("Photo")
  data class Photo(val photoId: String, @Contextual val size: Size, val page: Int) : NavKey

  interface Origin

  @Serializable @SerialName("SearchOrigin") data class SearchOrigin(val query: String) : Origin

  @Serializable
  @SerialName("Results")
  data class Results(val category: String, val origin: Origin, val page: Int) : NavKey

  @Test
  fun `a destination Nav3 saves with only representable properties keeps its name and every property`() {
    val product = Product(productId = "p1", tags = listOf("new", "sale"), page = 2)
    assertThat(saveAndRestoreByDefault(product)).containsExactly(product)

    val mapped = BackStackEntryMapper.SerialNameAndProperties.map(product)

    assertThat(mapped?.name).isEqualTo("Product")
    assertThat(mapped?.arguments)
      .containsExactly("productId", "p1", "tags", listOf("new", "sale"), "page", 2)
  }

  @Test
  fun `a Parcelable property Nav3 saves by default is a placeholder beside the name and other properties`() {
    val checkout = Checkout(orderId = "o1", payment = PaymentMethod("4242"), page = 2)
    assertThat(saveAndRestoreByDefault(checkout)).containsExactly(checkout)

    val mapped = BackStackEntryMapper.SerialNameAndProperties.map(checkout)

    assertThat(mapped?.name).isEqualTo("Checkout")
    assertThat(mapped?.arguments)
      .containsExactly("orderId", "o1", "payment", UNENCODABLE, "page", 2)
  }

  /** savedstate's default module registers [Size] as contextual, so no app module is needed. */
  @Test
  fun `a contextual property Nav3 saves by default is a placeholder beside the name and other properties`() {
    val photo = Photo(photoId = "ph1", size = Size(640, 480), page = 2)
    assertThat(saveAndRestoreByDefault(photo)).containsExactly(photo)

    val mapped = BackStackEntryMapper.SerialNameAndProperties.map(photo)

    assertThat(mapped?.name).isEqualTo("Photo")
    assertThat(mapped?.arguments).containsExactly("photoId", "ph1", "size", UNENCODABLE, "page", 2)
  }

  @Test
  fun `a contextual property Nav3 saves via a module is a placeholder beside the name and other properties`() {
    val cart = Cart(cartId = "c1", total = Money(1999), page = 2)
    val configuration = SavedStateConfiguration {
      serializersModule = SerializersModule {
        polymorphic(NavKey::class) { subclass(Cart::class) }
        contextual(Money::class, MoneySerializer)
      }
    }
    assertThat(saveAndRestore(cart, configuration)).containsExactly(cart)

    val mapped = BackStackEntryMapper.SerialNameAndProperties.map(cart)

    assertThat(mapped?.name).isEqualTo("Cart")
    assertThat(mapped?.arguments).containsExactly("cartId", "c1", "total", UNENCODABLE, "page", 2)
  }

  @Test
  fun `an open polymorphic property Nav3 saves via a module is a placeholder beside the name and other properties`() {
    val results = Results(category = "boots", origin = SearchOrigin("shoes"), page = 2)
    val configuration = SavedStateConfiguration {
      serializersModule = SerializersModule {
        polymorphic(NavKey::class) { subclass(Results::class) }
        polymorphic(Origin::class) { subclass(SearchOrigin::class) }
      }
    }
    assertThat(saveAndRestore(results, configuration)).containsExactly(results)

    val mapped = BackStackEntryMapper.SerialNameAndProperties.map(results)

    assertThat(mapped?.name).isEqualTo("Results")
    assertThat(mapped?.arguments)
      .containsExactly("category", "boots", "origin", UNENCODABLE, "page", 2)
  }

  /**
   * Outside the guarantee, as Nav3 can't save this destination without a module registering the
   * property's subclasses, but documents that Sentry records it the same way as when Nav3 can.
   */
  @Test
  fun `an open polymorphic property Nav3 can't save is still a placeholder beside the name and other properties`() {
    val results = Results(category = "boots", origin = SearchOrigin("shoes"), page = 2)
    assertFailsWith<SerializationException> { saveAndRestoreByDefault(results) }

    val mapped = BackStackEntryMapper.SerialNameAndProperties.map(results)

    assertThat(mapped?.name).isEqualTo("Results")
    assertThat(mapped?.arguments)
      .containsExactly("category", "boots", "origin", UNENCODABLE, "page", 2)
  }

  /** Saves and restores [destination] as `rememberNavBackStack(vararg elements)` does. */
  private fun saveAndRestoreByDefault(destination: NavKey): List<NavKey> {
    val serializer = NavBackStackSerializer(elementSerializer = NavKeySerializer<NavKey>())
    val savedState = encodeToSavedState(serializer, NavBackStack(destination))
    return decodeFromSavedState(serializer, savedState).toList()
  }

  /**
   * Saves and restores [destination] as `rememberNavBackStack(configuration, vararg elements)`
   * does.
   */
  private fun saveAndRestore(
    destination: NavKey,
    configuration: SavedStateConfiguration,
  ): List<NavKey> {
    val serializer =
      NavBackStackSerializer(elementSerializer = PolymorphicSerializer(NavKey::class))
    val savedState = encodeToSavedState(serializer, NavBackStack(destination), configuration)
    return decodeFromSavedState(serializer, savedState, configuration).toList()
  }

  private companion object {
    const val UNENCODABLE = "[Unencodable]"
  }
}
