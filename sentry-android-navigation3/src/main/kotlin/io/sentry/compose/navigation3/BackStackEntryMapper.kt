package io.sentry.compose.navigation3

import androidx.compose.runtime.snapshots.Snapshot
import io.sentry.compose.navigation3.serialization.SerialNameBackStackEntryMapper
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
 * Use [BackStackEntryMapper.SerialNames] to name `@Serializable` entries after their serial names,
 * [BackStackEntryMapper.SerialNamesAndProperties] to also record their properties as arguments, or
 * implement this interface to choose your own names and arguments. A custom mapper can delegate the
 * entries it doesn't handle to [BackStackEntryMapper.SerialNames].
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
 * survive minification (see [BackStackEntryMapper.SerialNames]).
 *
 * **Names fall back to "/unknown"**
 *
 * If [map] throws, returns `null`, or returns a blank [name][SentryBackStackEntry.name], Sentry
 * records the destination as "/unknown". Doing so signals that name extraction needs to be fixed
 * while avoiding misleading gaps in nav state.
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
 * If your back stack contains `@Serializable` entry types, [BackStackEntryMapper.SerialNames] names
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
 *     else -> BackStackEntryMapper.SerialNames.map(entry)
 *   }
 * }
 * ```
 */
@ApiStatus.Experimental
public fun interface BackStackEntryMapper<in T : Any> {

  public fun map(backStackEntry: T): SentryBackStackEntry?

  public companion object {

    /**
     * A convenience mapper for apps that use kotlinx.serialization.
     *
     * Names each back stack entry after the entry's `descriptor.serialName`, viz.:
     *
     * - the entry's `@SerialName` if it has one; or else
     * - the entry's fully qualified class name (e.g., "com.example.Home").
     *
     * The mapper returns `null` for an entry if any of the following is true (note that Sentry
     * displays `null` as "/unknown"):
     *
     * - kotlinx.serialization is not on the app's runtime classpath. (kotlinx.serialization is an
     *   optional dependency of this library; if your app doesn't use it, define your own
     *   [BackStackEntryMapper].)
     *
     * - The entry class isn't annotated with `@Serializable`. (This applies even to types
     *   kotlinx.serialization can serialize without the annotation, such as `String`, `List`, or
     *   unannotated enums.)
     *
     * - The entry class is generic (e.g., `SettingsScreen<T>`), even if it's `@Serializable`.
     *   (Generic serializers need type arguments, which can't be recovered from the entry at
     *   runtime.)
     *
     * The first entry of each class that can't be named logs a warning, visible when the SDK's
     * `debug` option is enabled.
     *
     * Entries share a name whenever they share a serial name, e.g., constants of the same enum, or
     * classes that use the same custom serializer (`@Serializable(with = ...)`). To tell them apart
     * give them distinct `SerialName`s or define a custom [BackStackEntryMapper].
     */
    @get:ApiStatus.Experimental
    public val SerialNames: BackStackEntryMapper<Any>
      get() = SerialNameBackStackEntryMapper.instance

    /**
     * Like [SerialNames], but also records the properties of each back stack entry as Sentry
     * arguments.
     *
     * **Privacy / PII**
     *
     * This mapper records the full content of your back stack keys, which may include user IDs,
     * emails, search queries, or other personal data. Only use it if ***every*** key in your back
     * stack is safe to send to Sentry; otherwise, [send only names][SerialNames], or use a custom
     * [BackStackEntryMapper] (see example below).
     *
     * Values of properties whose names contain a term from
     * [Sentry's sensitive denylist](https://develop.sentry.dev/sdk/foundations/client/data-collection/#sensitive-denylist)
     * (e.g., `auth`, `token`, `password`, `key`) are replaced with `"[Filtered]"` at every level of
     * nesting. Matching is case-insensitive and by substring, so names like `author` are also
     * filtered. Sentry's denylist is intentionally narrow, so treat it as a safety net rather than
     * a fully-fledged scrubber.
     *
     * For instance, given:
     * ```kotlin
     * @Serializable
     * enum class Source {
     *   @SerialName("search") SEARCH,
     *   @SerialName("deep_link") DEEP_LINK,
     * }
     *
     * @Serializable
     * @SerialName("ProductDetail")
     * data class ProductDetail(
     *   val productId: String,
     *   val source: Source,
     *   val userEmail: String,
     *   val authToken: String,
     * ) : NavKey
     * ```
     *
     * Navigating to `ProductDetail("sku-1234", Source.DEEP_LINK, "jane@example.com", "abc123")`
     * records the following arguments:
     * ```json
     * {
     *   "productId": "sku-1234",
     *   "source": "deep_link",
     *   "userEmail": "jane@example.com",
     *   "authToken": "[Filtered]"
     * }
     * ```
     *
     * Note that `userEmail` is sent, because it doesn't match the denylist. To send only select
     * properties, use a custom mapper:
     * ```kotlin
     * val mapper = BackStackEntryMapper<NavKey> { entry ->
     *   when (entry) {
     *     is ProductDetail -> SentryBackStackEntry(
     *       name = ProductDetail.serializer().descriptor.serialName,
     *       arguments = mapOf(
     *         "productId" to entry.productId, "source" to entry.source
     *       ),
     *     )
     *     // You can still use the convenience mapper for any entries you don't
     *     // need to special-case.
     *     else -> BackStackEntryMapper.SerialNamesAndProperties.map(entry)
     *   }
     * }
     * ```
     *
     * **Encoding**
     *
     * - Classes and objects become maps from property name to value. Objects without properties
     *   record no arguments.
     *
     * - Lists and sets become lists; maps become maps with string keys.
     *
     * - Enums are recorded by serial name; value classes by their underlying value.
     *
     * - Polymorphic properties become `{"type": <serial name>, "value": <value>}`.
     *
     * - A key whose serializer doesn't encode to a structure (e.g. a custom serializer that encodes
     *   to a string) is recorded as `{"value": <value>}`.
     *
     * - Properties marked `@Transient` are omitted.
     *
     * Properties with contextual or open polymorphic serializers can't be encoded, because no
     * serializers module is available. Entries with such properties are named, but recorded without
     * arguments, and the first entry of each such class logs a warning, visible when the SDK's
     * `debug` option is enabled.
     *
     * Arguments are subject to the same size and depth limits as those returned by custom mappers.
     * Entries that can't be named are recorded as "/unknown" without arguments, as with
     * [SerialNames].
     */
    @get:ApiStatus.Experimental
    public val SerialNamesAndProperties: BackStackEntryMapper<Any>
      get() = SerialNameBackStackEntryMapper.withArgumentsInstance
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
