package io.sentry.compose.navigation3

import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.annotations.ApiStatus

/** Info about a given back stack entry, suitable for display in Sentry. */
@ApiStatus.Experimental
public class SentryBackStackEntry(
  /**
   * A host-app defined name for a given back stack entry.
   *
   * Sentry interprets the name as a navigation destination. For instance, the following
   * `SentryBackStackEntry` instances:
   * ```kotlin
   * SentryBackStackEntry(name = "Home")
   * SentryBackStackEntry(name = "ProductDetail", arguments = mapOf("product_id" to 1234))
   * ```
   *
   * will produce a breadcrumb like this:
   * ```json
   * {
   *   "from": "/Home",
   *   "to": "/ProductDetail",
   *   "to_arguments": {
   *     "product_id": 1234
   *   }
   * }
   * ```
   *
   * Note that the Sentry SDK normalizes `name` to include a "/" prefix.
   */
  public val name: String,
  /**
   * Arguments from a given back stack entry, as selected by the host app.
   *
   * Useful for capturing any properties in the back stack key that have diagnostic value.
   *
   * Sentry treats these as metadata to be displayed alongside [name] in appropriate contexts. (See
   * the `name` KDoc for an example.)
   */
  public val arguments: Map<String, Any?>? = null,
) {

  override fun equals(other: Any?): Boolean =
    this === other ||
      (other is SentryBackStackEntry && name == other.name && arguments == other.arguments)

  override fun hashCode(): Int = 31 * name.hashCode() + (arguments?.hashCode() ?: 0)

  /** Omits arguments because they may contain sensitive host-app data. */
  override fun toString(): String = "SentryBackStackEntry(name=$name)"
}

/**
 * Maps a back stack entry to a displayable name and optional diagnostic arguments.
 *
 * To be implemented by the host app.
 *
 * **Privacy / PII**
 *
 * Values returned from [map] are ***not*** scrubbed by the Sentry SDK before being sent to Sentry.
 * Only return names and arguments that are known to be safe or have been pre-scrubbed.
 *
 * **Performance**
 *
 * This mapper is invoked synchronously from [SentryNavEffect] on the same apply thread that runs
 * the effect. Avoid non-performant mappings.
 *
 * **Choosing appropriate names**
 *
 * Return stable, low-cardinality names that don't depend on object identity, argument values, or
 * runtime class-name preservation. E.g., `Home`, `DetailScreen`, etc.
 *
 * In particular, avoid `::class.simpleName`, as R8 obfuscates class names and may associate them
 * with different symbols across builds.
 *
 * **Names fall back to "/unknown"**
 *
 * If [map] throws, returns `null`, or returns a blank [name][SentryBackStackEntry.name], Sentry
 * records the destination as "/unknown". Doing so signals that name extraction needs to be fixed
 * while avoiding misleading gaps in navigation data.
 *
 * For instance, if a user navigates from `/home -> /detail -> /settings`, but the mapper for
 * `/detail` throws, the back stack record will be `/home -> /unknown -> /settings` rather than
 * `/home -> /settings`.
 *
 * **Choosing appropriate arguments**
 *
 * Argument values may be any of the following scalar types:
 *
 * - [String]
 * - [CharSequence]
 * - [Char]
 * - [Boolean]
 * - any [Number]
 * - enums (via [Enum.name])
 * - `null`
 *
 * Or any of the following container types:
 *
 * - [Array]s
 * - primitive arrays
 * - [Map]s
 * - [Collection]s
 *
 * Containers may be nested, but they must bottom out in supported scalar types. Cyclic or deeply
 * nested containers will be skipped.
 *
 * **Arguments fall back to `toString()` or nothing**
 *
 * All non-supported argument types are stringified via `toString()`. If [map] throws or returns
 * `null`, no arguments are recorded for that back stack entry.
 *
 * **Using kotlinx.serialization**
 *
 * If your back stack contains `@Serializable` entry types, consider giving each entry an explicit
 * `@SerialName` and using its `descriptor.serialName`. For performance reasons, don't serialize
 * back stack keys in their entirety as `arguments` if they might be large, deeply nested, or
 * contain PII or other sensitive information.
 *
 * For instance:
 * ```kotlin
 * @Serializable
 * @SerialName("Home")
 * data class Home(userName: String) : NavKey
 *
 * @Serializable
 * @SerialName("ProductDetail")
 * data class ProductDetail(userName: String, productId: String, tab: Tab) : NavKey
 *
 * ...
 *
 * val backStackItemMapper = BackStackEntryMapper<NavKey> { entry ->
 *   when (entry) {
 *     is Home -> SentryBackStackEntry(Home.serializer().descriptor.serialName)
 *     is ProductDetail -> SentryBackStackEntry(
 *       name = ProductDetail.serializer().descriptor.serialName,
 *       // Select a subset of diagnostic arguments when serialization is unsafe
 *       // or non-performant.
 *       arguments = mapOf("product_id" to entry.productId, "tab" to entry.tab)
 *     )
 *     ...
 *   }
 * }
 * ```
 */
@ApiStatus.Experimental
public fun interface BackStackEntryMapper<T : Any> {
  public fun map(backStackEntry: T): SentryBackStackEntry?
}

/**
 * A Compose-compatible version of [BackStackEntryMapper] that generates [SentryBackStackEntry]s by
 * forwarding the request to the host app mapper in effect at the time [map] is called.
 *
 * Dynamically determining the current mapper lets us separate two concerns:
 *
 * 1. the lifetime of a consumer that tracks navigation state over time (e.g., [BackStackObserver]);
 *    and
 * 2. the lifetime of the mapper used to generate `SentryBackStackEntry`s.
 *
 * Without that separation, a long-lived consumer would have to choose between holding stale mapping
 * logic or recreating its own state whenever the mapper changed.
 */
internal class ForwardingBackStackEntryMapper<T : Any>(
  private val currentMapper: () -> BackStackEntryMapper<T>
) {
  fun map(backStackEntry: T): SentryBackStackEntry? = Snapshot.withoutReadObservation {
    currentMapper().map(backStackEntry)
  }
}
