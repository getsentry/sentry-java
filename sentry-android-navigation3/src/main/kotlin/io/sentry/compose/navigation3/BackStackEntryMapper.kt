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
 * Use [BackStackEntryMapper.serialName] to name `@Serializable` entries after their serial names,
 * [BackStackEntryMapper.serialNameWithArguments] to also record their properties as arguments, or
 * implement this interface to choose your own names and arguments. A custom mapper can delegate the
 * entries it doesn't handle to [BackStackEntryMapper.serialName].
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
 * with different symbols across builds. For `@Serializable` entries, prefer serial names, which
 * survive minification (see [BackStackEntryMapper.serialName]).
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
 * Containers may be nested, but they must bottom out in supported scalar types.
 *
 * **Arguments fall back to `toString()` or nothing**
 *
 * All non-supported argument types are stringified via `toString()`.
 *
 * No arguments will be recorded for a back stack entry if [map] throws or returns `null`, or if its
 * arguments are cyclic, too deeply nested, or too large. If the SDK drops an entry's arguments, it
 * adds a `dropped_by_sentry` field and corresponding reason in the Sentry UI.
 *
 * **Using kotlinx.serialization**
 *
 * If your back stack contains `@Serializable` entry types, [BackStackEntryMapper.serialName] names
 * them after their `descriptor.serialName`. To add arguments for some entries, write a custom
 * mapper that handles those entries and delegates the rest. For performance reasons, don't
 * serialize back stack keys in their entirety as `arguments` if they might be large, deeply nested,
 * or contain PII or other sensitive information.
 *
 * For instance:
 * ```kotlin
 * @Serializable
 * @SerialName("Home")
 * data class Home(val userName: String) : NavKey
 *
 * @Serializable
 * @SerialName("ProductDetail")
 * data class ProductDetail(val userName: String, val productId: String) : NavKey
 *
 * ...
 *
 * val backStackItemMapper = BackStackEntryMapper<NavKey> { entry ->
 *   when (entry) {
 *     is ProductDetail -> SentryBackStackEntry(
 *       name = ProductDetail.serializer().descriptor.serialName,
 *       // Select a subset of diagnostic arguments when serialization is unsafe
 *       // or non-performant.
 *       arguments = mapOf("product_id" to entry.productId)
 *     )
 *     // Home and any other entries are named after their serial names.
 *     else -> BackStackEntryMapper.serialName().map(entry)
 *   }
 * }
 * ```
 */
@ApiStatus.Experimental
public fun interface BackStackEntryMapper<in T : Any> {

  public fun map(backStackEntry: T): SentryBackStackEntry?

  public companion object {

    /**
     * Returns a mapper that names each back stack entry after its kotlinx.serialization serial
     * name, without arguments.
     *
     * The name is the entry class's `descriptor.serialName`, passed through unchanged: the class's
     * fully qualified name (e.g. `com.example.Route.Detail`), or its `@SerialName` value if it has
     * one. Serial names are compile-time constants, so they survive R8 minification. Use
     * `@SerialName` to choose shorter names.
     *
     * Entries are named "/unknown" when:
     * - kotlinx.serialization is not on the app's runtime classpath;
     * - the entry's class isn't annotated with `@Serializable`, including plain strings, enums, and
     *   other built-in types; or
     * - kotlinx.serialization can't look up the class's serializer without type arguments (e.g.
     *   generic classes).
     *
     * Classes that share a custom serializer (`@Serializable(with = ...)`) share its serial name.
     *
     * kotlinx.serialization is an optional dependency of this library. Apps that use
     * `@Serializable` already include it.
     */
    @ApiStatus.Experimental
    public fun serialName(): BackStackEntryMapper<Any> = SerialNameBackStackEntryMapper.instance

    /**
     * Returns a mapper that names entries like [serialName] and also records each entry's
     * properties as arguments, encoded with kotlinx.serialization.
     *
     * **Privacy**
     *
     * This records the full content of your back stack keys, which may include user IDs, emails,
     * search queries, or other personal data. Only use it if your keys are safe to send to Sentry.
     * Values whose property names match Sentry's built-in sensitive denylist (e.g. `token`,
     * `password`, `session`, `key`; matched case-insensitively as substrings) are replaced with
     * `"[Filtered]"` at every level of nesting. To send only some properties, use a custom mapper.
     *
     * **Encoding**
     * - Classes and objects become maps from property name to value. Objects without properties
     *   record no arguments.
     * - Lists and sets become lists; maps become maps with string keys.
     * - Enums are recorded by serial name; value classes by their underlying value.
     * - Polymorphic properties become `{"type": <serial name>, "value": <value>}`.
     * - A key whose serializer doesn't encode to a structure (e.g. a custom serializer that encodes
     *   to a string) is recorded as `{"value": <value>}`.
     * - Properties marked `@Transient` are omitted.
     *
     * Properties with contextual or open polymorphic serializers can't be encoded, because no
     * serializers module is available. Entries with such properties are named, but recorded without
     * arguments.
     *
     * Arguments are subject to the same size and depth limits as those returned by custom mappers.
     * Entries that can't be named are recorded as "/unknown" without arguments, as with
     * [serialName].
     */
    @ApiStatus.Experimental
    public fun serialNameWithArguments(): BackStackEntryMapper<Any> =
      SerialNameBackStackEntryMapper.withArgumentsInstance
  }
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
