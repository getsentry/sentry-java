package io.sentry.compose.navigation3

import android.os.Bundle
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
 * Open polymorphic properties are represented if their runtime classes are `@Serializable`.
 *
 * Destinations are saved as Nav3 1.1's `rememberNavBackStack` overloads save them: with
 * `encodeToSavedState`, a [NavBackStackSerializer], and either [NavKeySerializer] with the default
 * configuration, or a polymorphic [NavKey] serializer with an app-provided configuration.
 *
 * [BackStackEntryMapper.SerialNameAndProperties] is a lazily created singleton shared with every
 * other test in the JVM, and its caches, including which types it already logged, persist across
 * tests. So these tests assert only mapped entries, not logs; a test that asserts logs must create
 * its own mapper.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class Nav3SavedStateParityTest {

  @Serializable
  @SerialName("Product")
  data class Product(val productId: String, val tags: List<String>, val page: Int) : NavKey

  @Serializable
  @SerialName("Category")
  data class Category(val categoryId: String, val title: CharSequence, val page: Int) : NavKey

  @Serializable
  @SerialName("Listing")
  data class Listing(val listingId: String, val page: Int = 1) : NavKey

  @Serializable data class Size2D(val width: Int, val height: Int)

  @Serializable
  @SerialName("Gallery")
  data class Gallery(val galleryId: String, val countsBySize: Map<Size2D, Int>, val page: Int) :
    NavKey

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
  fun `a CharSequence property Nav3 saves by default is its text beside the name and other properties`() {
    val category = Category(categoryId = "c1", title = "Shoes", page = 2)
    assertThat(saveAndRestoreByDefault(category)).containsExactly(category)

    val mapped = BackStackEntryMapper.SerialNameAndProperties.map(category)

    assertThat(mapped?.name).isEqualTo("Category")
    assertThat(mapped?.arguments).containsExactly("categoryId", "c1", "title", "Shoes", "page", 2)
  }

  /**
   * Documents that Sentry deliberately records more than Nav3 saves here: Nav3's saved state omits
   * properties that have their default values, but they're recorded like any other property.
   */
  @Test
  fun `a property with its default value that Nav3 omits from saved state is still recorded`() {
    val listing = Listing(listingId = "l1")
    assertThat(saveAndRestoreByDefault(listing)).containsExactly(listing)
    val savedKeys = keysSavedByDefault(listing)
    assertThat(savedKeys).contains("listingId")
    assertThat(savedKeys).doesNotContain("page")

    val mapped = BackStackEntryMapper.SerialNameAndProperties.map(listing)

    assertThat(mapped?.name).isEqualTo("Listing")
    assertThat(mapped?.arguments).containsExactly("listingId", "l1", "page", 1)
  }

  @Test
  fun `a map with class keys Nav3 saves by default is key and value entries beside the name and other properties`() {
    val gallery = Gallery(galleryId = "g1", countsBySize = mapOf(Size2D(640, 480) to 3), page = 2)
    assertThat(saveAndRestoreByDefault(gallery)).containsExactly(gallery)

    val mapped = BackStackEntryMapper.SerialNameAndProperties.map(gallery)

    assertThat(mapped?.name).isEqualTo("Gallery")
    assertThat(mapped?.arguments)
      .containsExactly(
        "galleryId",
        "g1",
        "countsBySize",
        listOf(mapOf("key" to mapOf("width" to 640, "height" to 480), "value" to 3)),
        "page",
        2,
      )
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
  fun `an open polymorphic property Nav3 saves with a module is a type and value beside the other properties`() {
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
      .containsExactly("category", "boots", "origin", SEARCH_ORIGIN_ARGUMENTS, "page", 2)
  }

  /**
   * Outside the guarantee, as Nav3 can't save this destination without a module registering the
   * property's subclasses. Documents that Sentry deliberately records more than Nav3 can save here:
   * it finds the subclass's serializer by reflection, so it records the property the same way as
   * when Nav3 can.
   */
  @Test
  fun `an open polymorphic property Nav3 can't save is still a type and value beside the other properties`() {
    val results = Results(category = "boots", origin = SearchOrigin("shoes"), page = 2)
    assertFailsWith<SerializationException> { saveAndRestoreByDefault(results) }

    val mapped = BackStackEntryMapper.SerialNameAndProperties.map(results)

    assertThat(mapped?.name).isEqualTo("Results")
    assertThat(mapped?.arguments)
      .containsExactly("category", "boots", "origin", SEARCH_ORIGIN_ARGUMENTS, "page", 2)
  }

  /** Saves and restores [destination] as `rememberNavBackStack(vararg elements)` does. */
  private fun saveAndRestoreByDefault(destination: NavKey): List<NavKey> {
    val serializer = NavBackStackSerializer(elementSerializer = NavKeySerializer<NavKey>())
    val savedState = encodeToSavedState(serializer, NavBackStack(destination))
    return decodeFromSavedState(serializer, savedState).toList()
  }

  /**
   * Returns the keys, including nested ones, of the saved state that `rememberNavBackStack(vararg
   * elements)` saves for [destination].
   */
  private fun keysSavedByDefault(destination: NavKey): Set<String> {
    val serializer = NavBackStackSerializer(elementSerializer = NavKeySerializer<NavKey>())
    return keysOf(encodeToSavedState(serializer, NavBackStack(destination)))
  }

  @Suppress("DEPRECATION") // Bundle.get, to read values whatever their types.
  private fun keysOf(value: Any?): Set<String> =
    when (value) {
      is Bundle -> value.keySet().flatMapTo(mutableSetOf()) { keysOf(value.get(it)) + it }
      is Iterable<*> -> value.flatMapTo(mutableSetOf()) { keysOf(it) }
      is Array<*> -> value.flatMapTo(mutableSetOf()) { keysOf(it) }
      else -> emptySet()
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

    val SEARCH_ORIGIN_ARGUMENTS =
      mapOf("type" to "SearchOrigin", "value" to mapOf("query" to "shoes"))
  }
}
