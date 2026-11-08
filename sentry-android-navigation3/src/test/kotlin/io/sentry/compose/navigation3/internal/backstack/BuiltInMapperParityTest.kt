package io.sentry.compose.navigation3.internal.backstack

import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.compose.navigation3.BackStackEntryMapper
import io.sentry.compose.navigation3.SentryBackStackEntry
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_CHARACTERS
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_DEPTH
import io.sentry.compose.navigation3.internal.backstack.ArgumentConstraints.MAX_ARGUMENT_VALUES
import io.sentry.compose.navigation3.internal.backstack.BackStackConverter.RetentionPolicy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Converts the same back stack once with [BackStackEntryMapper.SerialNameAndProperties] and once
 * with a custom mapper that returns the equivalent plain map for each entry, and checks that both
 * record the same arguments and drop reasons.
 *
 * Also converts back stacks with a custom mapper that wraps the built-in one and filters its
 * arguments, as `BackStackEntryMapper.SerialNameAndProperties` documents, and checks that it
 * records the same drop reasons as the built-in mapper. The exception is an entry over an
 * update-wide limit, which drops only its own arguments through the wrapper. That the wrapper
 * records the arguments it doesn't remove is covered by `SentryNavEffectTest`.
 *
 * Each back stack is ordered top first, as [BackStackObserver] converts it.
 */
class BuiltInMapperParityTest {

  @Serializable @SerialName("Cart") private data class CartRoute(val cartId: String)

  @Serializable private data class Address(val city: String, val zip: Int)

  private enum class Delivery {
    STANDARD,
    EXPRESS,
  }

  @Serializable
  @SerialName("Order")
  private data class OrderRoute(
    val orderId: Long,
    val gift: Boolean,
    val note: String?,
    val delivery: Delivery,
    val address: Address,
    val tags: List<String>,
    val quantities: Map<String, Int>,
  )

  @Serializable @SerialName("Catalog") private data class CatalogRoute(val prices: Map<String, Int>)

  @Serializable @SerialName("Search") private data class SearchRoute(val query: String)

  @Serializable @SerialName("Nested") private data class NestedRoute(val child: NestedRoute?)

  @Serializable
  @SerialName("FilterCounts")
  private data class FilterCountsRoute(val counts: Map<String?, Int>)

  @Serializable(with = UnencodableSerializer::class) private class UnencodableRoute

  /** Throws when encoding an [UnencodableRoute], as a broken host app serializer might. */
  private object UnencodableSerializer : KSerializer<UnencodableRoute> {
    override val descriptor: SerialDescriptor =
      buildClassSerialDescriptor("Unencodable") { element<String>("email") }

    override fun serialize(encoder: Encoder, value: UnencodableRoute) {
      error("Can't encode")
    }

    override fun deserialize(decoder: Decoder): UnencodableRoute =
      throw UnsupportedOperationException()
  }

  /** Wraps the built-in mapper and removes the `email` argument, as its KDoc documents. */
  private val emailFilteringMapper =
    BackStackEntryMapper<Any> { entry ->
      BackStackEntryMapper.SerialNameAndProperties.map(entry)?.let { mapped ->
        SentryBackStackEntry(mapped.name, mapped.arguments?.filterKeys { it != "email" })
      }
    }

  private val cart = CartRoute(cartId = "c-1")
  private val plainCart = SentryBackStackEntry("Cart", mapOf("cartId" to "c-1"))

  private val logger = mock<ILogger>()

  @Test
  fun `both mappers record the same arguments for entries within the argument limits`() {
    val order =
      OrderRoute(
        orderId = 42L,
        gift = true,
        note = null,
        delivery = Delivery.EXPRESS,
        address = Address(city = "Vienna", zip = 1010),
        tags = listOf("new", "sale"),
        quantities = mapOf("apple" to 2, "pear" to 3),
      )
    val plainOrder =
      SentryBackStackEntry(
        "Order",
        mapOf(
          "orderId" to 42L,
          "gift" to true,
          "note" to null,
          "delivery" to Delivery.EXPRESS,
          "address" to mapOf("city" to "Vienna", "zip" to 1010),
          "tags" to listOf("new", "sale"),
          "quantities" to mapOf("apple" to 2, "pear" to 3),
        ),
      )
    val backStack = listOf(order, cart)

    val builtIn = convertWithBuiltInMapper(backStack)
    val custom = convertWithPlainMapper(backStack, order to plainOrder, cart to plainCart)

    assertThat(custom).isEqualTo(builtIn)
    assertThat(builtIn)
      .containsExactly(
        NormalizedSentryBackStackEntry(
          "/Order",
          mapOf(
            "orderId" to 42L,
            "gift" to true,
            "note" to null,
            "delivery" to "EXPRESS",
            "address" to mapOf("city" to "Vienna", "zip" to 1010),
            "tags" to listOf("new", "sale"),
            "quantities" to mapOf("apple" to 2, "pear" to 3),
          ),
        ),
        NormalizedSentryBackStackEntry("/Cart", mapOf("cartId" to "c-1")),
      )
      .inOrder()
  }

  @Test
  fun `both mappers drop the arguments of an entry over the argument count limit, and of older entries`() {
    // The prices map and its MAX_ARGUMENT_VALUES + 1 entries are MAX_ARGUMENT_VALUES + 2 values,
    // over the limit of MAX_ARGUMENT_VALUES per update.
    val prices = (0..MAX_ARGUMENT_VALUES).associate { "item$it" to it }
    val catalog = CatalogRoute(prices = prices)
    val plainCatalog = SentryBackStackEntry("Catalog", mapOf("prices" to prices))
    val backStack = listOf(catalog, cart)

    val builtIn = convertWithBuiltInMapper(backStack)
    val custom = convertWithPlainMapper(backStack, catalog to plainCatalog, cart to plainCart)

    assertThat(custom).isEqualTo(builtIn)
    assertThat(builtIn)
      .containsExactly(
        NormalizedSentryBackStackEntry(
          "/Catalog",
          argumentDropReason = ArgumentDropReason.MAX_COUNT,
        ),
        NormalizedSentryBackStackEntry("/Cart", argumentDropReason = ArgumentDropReason.MAX_COUNT),
      )
      .inOrder()
  }

  @Test
  fun `both mappers drop the arguments of an entry over the character limit, and of older entries`() {
    // The query alone is one character over the limit of MAX_ARGUMENT_CHARACTERS per update.
    val query = "q".repeat(MAX_ARGUMENT_CHARACTERS + 1)
    val search = SearchRoute(query = query)
    val plainSearch = SentryBackStackEntry("Search", mapOf("query" to query))
    val backStack = listOf(search, cart)

    val builtIn = convertWithBuiltInMapper(backStack)
    val custom = convertWithPlainMapper(backStack, search to plainSearch, cart to plainCart)

    assertThat(custom).isEqualTo(builtIn)
    assertThat(builtIn)
      .containsExactly(
        NormalizedSentryBackStackEntry(
          "/Search",
          argumentDropReason = ArgumentDropReason.CHARACTER_LIMIT,
        ),
        NormalizedSentryBackStackEntry(
          "/Cart",
          argumentDropReason = ArgumentDropReason.CHARACTER_LIMIT,
        ),
      )
      .inOrder()
  }

  @Test
  fun `both mappers drop the arguments of an entry nested past the depth limit, but not of older entries`() {
    // The innermost child is nested one level past the depth limit of MAX_ARGUMENT_DEPTH.
    val nested = nestedRoute(levels = MAX_ARGUMENT_DEPTH + 1)
    val plainNested =
      SentryBackStackEntry("Nested", plainNestedArguments(levels = MAX_ARGUMENT_DEPTH + 1))
    val backStack = listOf(nested, cart)

    val builtIn = convertWithBuiltInMapper(backStack)
    val custom = convertWithPlainMapper(backStack, nested to plainNested, cart to plainCart)

    assertThat(custom).isEqualTo(builtIn)
    assertThat(builtIn)
      .containsExactly(
        NormalizedSentryBackStackEntry(
          "/Nested",
          argumentDropReason = ArgumentDropReason.INVALID_STRUCTURE,
        ),
        NormalizedSentryBackStackEntry("/Cart", mapOf("cartId" to "c-1")),
      )
      .inOrder()
  }

  @Test
  fun `both mappers drop the arguments of an entry with duplicate map keys, but not of older entries`() {
    val filterCounts = FilterCountsRoute(counts = mapOf(null to 1, "null" to 2))
    val plainFilterCounts =
      SentryBackStackEntry("FilterCounts", mapOf("counts" to mapOf(null to 1, "null" to 2)))
    val backStack = listOf(filterCounts, cart)

    val builtIn = convertWithBuiltInMapper(backStack)
    val custom =
      convertWithPlainMapper(backStack, filterCounts to plainFilterCounts, cart to plainCart)

    assertThat(custom).isEqualTo(builtIn)
    assertThat(builtIn)
      .containsExactly(
        NormalizedSentryBackStackEntry(
          "/FilterCounts",
          argumentDropReason = ArgumentDropReason.DUPLICATE_KEY,
        ),
        NormalizedSentryBackStackEntry("/Cart", mapOf("cartId" to "c-1")),
      )
      .inOrder()
  }

  // A custom mapper that wraps the built-in one

  @Test
  fun `a mapper that wraps the built-in one records the same encoding failure for an entry that can't be encoded`() {
    val backStack = listOf(UnencodableRoute(), cart)

    val builtIn = convertWithBuiltInMapper(backStack)
    val wrapped = convert(backStack, emailFilteringMapper)

    assertThat(wrapped.serialize()).isEqualTo(builtIn.serialize())
    assertThat(wrapped.serialize())
      .containsExactly(
        mapOf(
          "entry" to "/Unencodable",
          "entry_arguments" to mapOf("dropped_by_sentry" to "encoding_failed"),
        ),
        mapOf("entry" to "/Cart", "entry_arguments" to mapOf("cartId" to "c-1")),
      )
      .inOrder()
  }

  @Test
  fun `a mapper that wraps the built-in one records the same drop reason for an entry nested past the depth limit`() {
    val backStack = listOf(nestedRoute(levels = MAX_ARGUMENT_DEPTH + 1), cart)

    val builtIn = convertWithBuiltInMapper(backStack)
    val wrapped = convert(backStack, emailFilteringMapper)

    assertThat(wrapped.serialize()).isEqualTo(builtIn.serialize())
    assertThat(wrapped.serialize())
      .containsExactly(
        mapOf(
          "entry" to "/Nested",
          "entry_arguments" to mapOf("dropped_by_sentry" to "invalid_structure"),
        ),
        mapOf("entry" to "/Cart", "entry_arguments" to mapOf("cartId" to "c-1")),
      )
      .inOrder()
  }

  @Test
  fun `a mapper that wraps the built-in one records the same drop reason for an entry with duplicate map keys`() {
    val backStack = listOf(FilterCountsRoute(counts = mapOf(null to 1, "null" to 2)), cart)

    val builtIn = convertWithBuiltInMapper(backStack)
    val wrapped = convert(backStack, emailFilteringMapper)

    assertThat(wrapped.serialize()).isEqualTo(builtIn.serialize())
    assertThat(wrapped.serialize())
      .containsExactly(
        mapOf(
          "entry" to "/FilterCounts",
          "entry_arguments" to mapOf("dropped_by_sentry" to "duplicate_key"),
        ),
        mapOf("entry" to "/Cart", "entry_arguments" to mapOf("cartId" to "c-1")),
      )
      .inOrder()
  }

  @Test
  fun `a mapper that wraps the built-in one drops only the arguments of an entry over the character limit`() {
    val search = SearchRoute(query = "q".repeat(MAX_ARGUMENT_CHARACTERS + 1))
    val backStack = listOf(search, cart)

    val builtIn = convertWithBuiltInMapper(backStack)
    val wrapped = convert(backStack, emailFilteringMapper)

    assertThat(builtIn.last().argumentDropReason).isEqualTo(ArgumentDropReason.CHARACTER_LIMIT)
    assertThat(wrapped.serialize())
      .containsExactly(
        mapOf(
          "entry" to "/Search",
          "entry_arguments" to mapOf("dropped_by_sentry" to "max_character_limit_exceeded"),
        ),
        mapOf("entry" to "/Cart", "entry_arguments" to mapOf("cartId" to "c-1")),
      )
      .inOrder()
  }

  @Test
  fun `a mapper that wraps the built-in one drops only the arguments of an entry over the argument count limit`() {
    val catalog = CatalogRoute(prices = (0..MAX_ARGUMENT_VALUES).associate { "item$it" to it })
    val backStack = listOf(catalog, cart)

    val builtIn = convertWithBuiltInMapper(backStack)
    val wrapped = convert(backStack, emailFilteringMapper)

    assertThat(builtIn.last().argumentDropReason).isEqualTo(ArgumentDropReason.MAX_COUNT)
    assertThat(wrapped.serialize())
      .containsExactly(
        mapOf(
          "entry" to "/Catalog",
          "entry_arguments" to mapOf("dropped_by_sentry" to "max_argument_count_exceeded"),
        ),
        mapOf("entry" to "/Cart", "entry_arguments" to mapOf("cartId" to "c-1")),
      )
      .inOrder()
  }

  private fun convertWithBuiltInMapper(backStack: List<Any>): List<NormalizedSentryBackStackEntry> =
    convert(backStack, BackStackEntryMapper.SerialNameAndProperties)

  /** Converts [backStack] with a custom mapper that returns each entry's paired plain entry. */
  private fun convertWithPlainMapper(
    backStack: List<Any>,
    vararg plainEntries: Pair<Any, SentryBackStackEntry>,
  ): List<NormalizedSentryBackStackEntry> {
    val plainEntriesByEntry = plainEntries.toMap()
    return convert(backStack) { entry -> plainEntriesByEntry.getValue(entry) }
  }

  private fun convert(
    backStack: List<Any>,
    entryMapper: BackStackEntryMapper<Any>,
  ): List<NormalizedSentryBackStackEntry> =
    BackStackConverter(ForwardingBackStackEntryMapper { entryMapper }, logger)
      .convert(backStack, RetentionPolicy.KEEP_FIRST)

  /** A [NestedRoute] wrapping [levels] more of them, the innermost with no child. */
  private fun nestedRoute(levels: Int): NestedRoute {
    var route = NestedRoute(child = null)
    repeat(levels) { route = NestedRoute(child = route) }
    return route
  }

  /** The plain map equivalent of [nestedRoute]'s arguments. */
  private fun plainNestedArguments(levels: Int): Map<String, Any?> {
    var arguments = mapOf<String, Any?>("child" to null)
    repeat(levels) { arguments = mapOf("child" to arguments) }
    return arguments
  }
}
