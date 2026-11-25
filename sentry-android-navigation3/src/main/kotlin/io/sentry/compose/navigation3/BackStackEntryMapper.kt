package io.sentry.compose.navigation3

import io.sentry.compose.navigation3.internal.backstack.BuiltInBackStackEntryMappers
import io.sentry.compose.navigation3.internal.contract.ArgumentDropReason
import io.sentry.compose.navigation3.internal.contract.UnknownEntryReason
import org.jetbrains.annotations.ApiStatus

/**
 * Maps a back stack entry to a displayable name and optional diagnostic arguments.
 *
 * See [SerialName] for an out-of-the-box mapper, or else provide your own. (Custom implementations
 * can optionally fall back to `SerialName` for any entries that don't have to be special-cased.)
 *
 * **Privacy / PII**
 *
 * Host apps are responsible for filtering their own arguments, including removing any sensitive or
 * personal information. Only return names and arguments that are known to be safe or have been
 * pre-scrubbed.
 *
 * **Performance**
 *
 * Mappers are invoked synchronously from [SentryNavEffect] on the same apply thread that runs the
 * effect. Avoid non-performant mappings.
 *
 * **Choosing appropriate names**
 *
 * Return stable, low-cardinality names (e.g., `Home`, `DetailScreen`, etc.) that don't depend on
 * object identity, argument values, or runtime class-name preservation.
 *
 * In particular, avoid `::class.simpleName`, as R8 obfuscates class names and may associate them
 * with different symbols across builds.
 *
 * **Names fall back to "/unknown"**
 *
 * If [map] throws, returns `null`, or returns a blank [name][SentryBackStackEntry.name], Sentry
 * records the destination as "/unknown". Doing so signals that name extraction needs to be fixed
 * while avoiding misleading gaps in nav state.
 *
 * For instance, if a user navigates from /home -> /detail -> /settings, but the mapper for /detail
 * throws, the back stack record will be /home -> /unknown -> /settings rather than /home ->
 * /settings.
 *
 * Each "/unknown" entry records why it has no name via an [UnknownEntryReason].
 *
 * **Choosing appropriate arguments**
 *
 * Argument values may be any of the following scalar types:
 *
 * - [String]
 * - [CharSequence]
 * - [Char]
 * - [Boolean]
 * - any [Number] (`NaN` and infinite values are sent as the strings `"NaN"`, `"Infinity"`, and
 *   `"-Infinity"`)
 * - enums (via [Enum.name])
 * - `null`
 *
 * Or any of the following container types:
 *
 * - primitive arrays
 * - [Array]s
 * - [Collection]s
 * - [Map]s
 *
 * Containers may be nested, but they must bottom out in supported scalar types.
 *
 * **Unsupported arguments types are stringified or dropped**
 *
 * All non-supported argument types are stringified via `toString()`.
 *
 * No arguments are recorded for a back stack entry if [map] throws, returns `null`, or has
 * arguments that are:
 *
 * - cyclic
 * - too deeply nested,
 * - too large, or
 * - contain a map with two keys stringified the same way (e.g., `1` and `"1"`).
 *
 * If the SDK drops an entry's arguments, it adds an [ArgumentDropReason].
 */
@ApiStatus.Experimental
public fun interface BackStackEntryMapper<in T : Any> {

  public fun map(backStackEntry: T): SentryBackStackEntry?

  public companion object {

    /**
     * A convenience [BackStackEntryMapper] for apps that use kotlinx.serialization and only need to
     * track the [serial names][kotlinx.serialization.descriptors.SerialDescriptor.serialName] of
     * their back stack entries.
     *
     * For instance, given:
     * ```
     * @Serializable
     * @SerialName("ProductDetailScreen")
     * data class ProductDetail(val productId: String) : NavKey
     * ```
     *
     * this mapper returns `"ProductDetailScreen"` for `ProductDetail(productId = "zyx987")` but
     * omits the destination's properties (`productId`).
     *
     * **Name == SerialDescriptor.serialName**
     *
     * Each back stack entry is named after its
     * [`SerialDescriptor.serialName`][kotlinx.serialization.descriptors.SerialDescriptor.serialName],
     * i.e.:
     *
     * - the entry's `@SerialName` if it has one; or else
     * - the entry's fully qualified class name (e.g., "com.example.Home").
     *
     * **Sharing names**
     *
     * Entries will share a Sentry name if their serializers have the same serial name. In
     * particular:
     *
     * - All constants of an enum share the enum class's serial name, regardless of any
     *   `@SerialName` on the constants.
     *
     * - Classes that share a custom serializer (`@Serializable(with = ...)`) share its descriptor's
     *   serial name, regardless of any `@SerialName` on the classes.
     *
     * To tell such entries apart, define a custom [BackStackEntryMapper], e.g., one that names enum
     * constants after [Enum.name].
     *
     * **Unknown names**
     *
     * This mapper returns `null` for back stack entries that lack a `@Serializable` annotation, are
     * generic, or whose serializers can't be found or used.
     *
     * Sentry displays `null` entries as "/unknown" with an [UnknownEntryReason]. With SDK debugging
     * enabled, the first unnamed entry of each class also logs a warning.
     *
     * See the
     * [Troubleshooting Guide](https://docs.sentry.io/platforms/android/integrations/navigation/#troubleshooting-kotlinxserialization-mappers)
     * for more details.
     */
    public val SerialName: BackStackEntryMapper<Any>
      get() = BuiltInBackStackEntryMappers.serialName
  }
}

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
