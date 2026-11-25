package io.sentry.compose.navigation3

import io.sentry.compose.navigation3.internal.backstack.BuiltInBackStackEntryMappers
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
 * If you use kotlinx.serialization, see [SerialName] and [SerialNameAndProperties] for convenient,
 * out-of-the-box mappers. Otherwise, provide your own implementation. (Note: Custom implementations
 * can always fall back to `SerialName` or `SerialNameAndProperties` for any entries that don't have
 * to be special-cased.)
 *
 * **Privacy / PII**
 *
 * Built-in mappers that capture entry properties apply limited sensitive-data filtering before
 * recording arguments; see [SerialNameAndProperties] for the filtering rules. Custom mappers are
 * responsible for filtering their own arguments; Sentry does not apply its built-in sensitive-data
 * filtering to arguments they supply themselves.
 *
 * Only return names and arguments that are known to be safe or have been pre-scrubbed. All
 * arguments remain subject to size and structural limits.
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
 * while avoiding misleading gaps in nav state.
 *
 * For instance, if a user navigates from `/home -> /detail -> /settings`, but the mapper for
 * `/detail` throws, the back stack record will be `/home -> /unknown -> /settings` rather than
 * `/home -> /settings`.
 *
 * Each "/unknown" entry records why it has no name, as `unnamed_reason` in the back stack record,
 * breadcrumbs, and navigation transactions (e.g., `"entry_unnamed_reason": "unmapped"`):
 * `mapping_failed` if [map] threw, `blank_name` for a blank name, and `unmapped` if [map] returned
 * `null`. The built-in mappers record more specific reasons when they return `null` (see
 * [SerialName]), and so does a custom mapper that returns the `null` a built-in mapper returned.
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
 *   `"-Infinity"`, which JSON can represent)
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
 * All non-supported argument types are stringified via `toString()`, as are map keys.
 *
 * No arguments will be recorded for a back stack entry if [map] throws or returns `null`, or if its
 * arguments are cyclic, too deeply nested, or too large, or contain a map with two keys that are
 * stringified the same way (e.g., `1` and `"1"`). If the SDK drops an entry's arguments, it adds a
 * `dropped_by_sentry` field and corresponding reason (e.g., `duplicate_key`) in the Sentry UI.
 */
@ApiStatus.Experimental
public fun interface BackStackEntryMapper<in T : Any> {

  public fun map(backStackEntry: T): SentryBackStackEntry?

  public companion object {

    /**
     * A convenience [BackStackEntryMapper] for apps that use kotlinx.serialization and only need to
     * track the serial name of each of their back stack entries (and not each entry's properties).
     *
     * For instance, given:
     * ```
     * @Serializable
     * @SerialName("ProductDetail")
     * data class ProductDetail(
     *   val userId: String,
     *   val productId: String
     * ) : NavKey
     * ```
     *
     * when navigating to `ProductDetail(userId = "123abc", productId = "zyx987")`, this mapper
     * would return `"ProductDetail"` as the destination's name, but would ***not*** return the
     * destination's properties (`userId` and `productId`).
     *
     * To return both entry names and properties, see [SerialNameAndProperties].
     *
     * **Name == SerialDescriptor.serialName**
     *
     * This mapper names each back stack entry after the entry's
     * [`SerialDescriptor.serialName`][kotlinx.serialization.descriptors.SerialDescriptor.serialName],
     * i.e.:
     *
     * - the entry's `@SerialName` if it has one; or else
     * - the entry's fully qualified class name (e.g., "com.example.Home").
     *
     * **Unknown names**
     *
     * This mapper returns `null` for an entry, which Sentry displays as `"/unknown"`, with the
     * `unnamed_reason` in parentheses, if:
     *
     * - the entry class isn't annotated with `@Serializable` itself, even if kotlinx.serialization
     *   can serialize it without the annotation (e.g., `String`, `List`, an unannotated enum, or a
     *   class marked with a custom `@MetaSerializable` annotation) (`not_serializable`);
     *
     * - the entry class is generic (e.g., `SettingsScreen<T>`), as its serializer needs type
     *   arguments that can't be recovered from the entry at runtime (`generic_type`);
     *
     * - looking up the entry's serializer finds none, e.g., because R8 removed what the lookup
     *   needs (`serializer_not_found`), or throws, e.g., because a custom serializer throws
     *   (`serializer_lookup_threw`); or
     *
     * - the app's kotlinx.serialization version is incompatible with this SDK, in which case every
     *   entry is unnamed (`kotlinx_serialization_version`).
     *
     * `serializer_not_found` often only happens in minified release builds. In particular, if the
     * app is minified by R8 and uses a kotlinx.serialization version below 1.10.0, an entry class
     * with a named companion object (e.g., `companion object Factory`) may be named in unminified
     * debug builds but not in minified release builds, depending on the kotlinx.serialization
     * version and R8 mode. To fix this, upgrade kotlinx.serialization to 1.10.0 or later, or add
     * the keep rules from Troubleshooting (linked below).
     *
     * The first entry of each class that can't be named logs a warning explaining why, visible when
     * the SDK's `debug` option is enabled. For fixes, see
     * [Troubleshooting](https://docs.sentry.io/platforms/android/integrations/navigation/#troubleshooting-kotlinxserialization-mappers)
     * in the Sentry Android Navigation docs.
     *
     * **Sharing names**
     *
     * Entries share a Sentry name whenever their serializers share a serial name. In particular:
     *
     * - All constants of an enum are named after the enum. A `@SerialName` on a constant only
     *   changes how [SerialNameAndProperties] records the constant as an argument.
     *
     * - Classes that use the same custom serializer (`@Serializable(with = ...)`) are named after
     *   that serializer's descriptor, which ignores a `@SerialName` on the class.
     *
     * To tell such entries apart, define a custom [BackStackEntryMapper], e.g., one that names enum
     * constants after [Enum.name].
     *
     * **One-time setup**
     *
     * Reading this mapper (e.g., to pass it to [SentryNavEffect]) does no work. Its first [map]
     * call does a one-time setup, which checks the app's kotlinx.serialization version, so that
     * call takes longer than later ones. [SentryNavEffect] makes that call when it first records
     * the back stack, after composition. The same applies to [SerialNameAndProperties].
     */
    public val SerialName: BackStackEntryMapper<Any>
      get() = BuiltInBackStackEntryMappers.serialName

    /**
     * Like [SerialName], but also returns the properties of each back stack entry as Sentry
     * arguments.
     *
     * For instance, given:
     * ```
     * @Serializable
     * @SerialName("ProductDetail")
     * data class ProductDetail(
     *   val userId: String,
     *   val productId: String
     * ) : NavKey
     * ```
     *
     * when navigating to `ProductDetail(userId = "123abc", productId = "zyx987")`, this mapper
     * would return `"ProductDetail"` as the destination's name, along with the following arguments:
     * ```json
     * {
     *   "userId": "123abc",
     *   "productId": "zyx987"
     * }
     * ```
     *
     * Returns `null` for any entries that can't be named (see [SerialName]). Sentry displays these
     * entries as `"/unknown"` with no arguments.
     *
     * The argument limits described in [BackStackEntryMapper] apply.
     *
     * **Privacy / PII**
     *
     * This mapper returns arguments for every property of each back stack entry passed to it, which
     * may include private data or PII. Almost all properties are sent as-is (see below for limited
     * exceptions in the case of sensitive secrets). Neither `sendDefaultPii` nor the
     * `dataCollection` options change what this mapper returns.
     *
     * Modify your use of this mapper if any entry in your back stack contains info you don't want
     * sent to Sentry. For instance, i) use this mapper for all entries but filter out any
     * properties you want to omit, or ii) use a custom [BackStackEntryMapper] that special-cases
     * entries with sensitive info or PII and falls back to this mapper (or [SerialName]) for
     * entries without it. See
     * [Choosing Which Arguments to Send](https://docs.sentry.io/platforms/android/integrations/navigation/#choosing-which-arguments-to-send)
     * for examples.
     *
     * **Limited filtering for sensitive secrets**
     *
     * Values are replaced with `"[Filtered]"` when their property's targeted name (i.e., their
     * `@SerialName` if they have one, or else the property's name) contains a term from
     * [Sentry's sensitive denylist](https://develop.sentry.dev/sdk/foundations/client/data-collection/#sensitive-denylist)
     * (ignoring case). This filters secrets like `authToken`, but leaves PII like `userEmail`
     * unchanged. Map keys and value classes' underlying property names are checked too, and a map
     * whose keys are value classes with sensitive underlying property names is recorded as
     * `"[Filtered]"` as a whole.
     *
     * Matching follows the same rule Sentry SDKs use for HTTP headers, cookies, and query
     * parameters, meaning it may filter harmless properties like `insideCart` (`sid`) or `keyword`
     * (`key`).
     *
     * **Encoding and encoding failures**
     *
     * Properties are encoded, which runs the entry's serializers, when its arguments are first
     * read, including by the arguments map's `equals`, `hashCode`, or `toString`. Properties with
     * default values are recorded too, although Nav3's saved state omits them. `CharSequence`
     * properties are recorded as strings.
     *
     * Sentry encodes the properties again when it records an entry this mapper returned, so if an
     * entry's properties can change, or its serializers return different values each time, the
     * recorded values can differ from ones read earlier. If a custom mapper checks argument values
     * to decide what to send, it should return a copy (`SentryBackStackEntry(entry.name,
     * entry.arguments?.toMap())`), so Sentry records the values it checked.
     *
     * Maps are recorded as maps with their keys converted to text. A map whose keys are classes,
     * objects, collections, or polymorphic values is recorded as `"[Unencodable]"`, as its keys
     * can't be recorded as text.
     *
     * A value of a sealed type is recorded as `{"type": <serial name>, "value": <properties>}`.
     *
     * Some values can't be encoded, e.g., contextual properties and values of open polymorphic
     * types (e.g., a non-sealed interface or a `Parcelable` property), since their serializers
     * require a `SerializersModule` or saved state encoder this mapper doesn't have. Each such
     * property value, list element, or map value is recorded as `"[Unencodable]"`, and the entry's
     * other arguments are kept.
     *
     * Other failures preserve the entry's name and replace its arguments with a drop reason, e.g.,
     * `{"dropped_by_sentry": "encoding_failed"}`. Encoding errors are logged once per entry type
     * when `debug` is enabled. If the app's kotlinx.serialization version can't encode properties
     * at all, every entry with properties records `{"dropped_by_sentry":
     * "kotlinx_serialization_version"}` instead.
     */
    public val SerialNameAndProperties: BackStackEntryMapper<Any>
      get() = BuiltInBackStackEntryMappers.serialNameAndProperties
  }
}
