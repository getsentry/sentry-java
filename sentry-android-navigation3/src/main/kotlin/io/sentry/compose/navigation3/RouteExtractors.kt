package io.sentry.compose.navigation3

import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.annotations.ApiStatus

/**
 * Extracts a human-readable route name from a back stack entry.
 *
 * **Privacy / PII**
 *
 * Values returned from [extract] are ***not*** scrubbed by the Sentry SDK before being sent to
 * Sentry. Only return names that are known to be safe or have been pre-scrubbed.
 *
 * **Choosing appropriate route names**
 *
 * Implementations should return stable, low-cardinality names that don't depend on object identity,
 * argument values, or runtime class-name preservation. E.g., `Home`, `DetailScreen`, etc.
 *
 * In particular, avoid `::class.simpleName` in release builds, as R8 obfuscates class names and may
 * map them to different symbols across builds.
 *
 * Extractors are invoked synchronously from [SentryNavEffect] on the same apply thread that runs
 * the effect. Avoid non-performant extraction logic.
 *
 * **Falls back to "/unknown"**
 *
 * If [extract] throws or returns a blank route name, Sentry records the destination as "/unknown".
 * Doing so signals that name extraction needs to be fixed while avoiding misleading gaps in
 * navigation data.
 *
 * For instance, if a user navigates from `/home -> /detail -> /settings`, but the name extractor
 * for `/detail` throws, the back stack record will be `/home -> /unknown -> /settings` rather than
 * `/home -> /settings`.
 *
 * **Using kotlinx.serialization**
 *
 * If your back stack contains `@Serializable` route types, consider mapping each route type to a
 * stable serializer name. For instance:
 * ```kotlin
 * val nameExtractor = RouteNameExtractor<Any> { route ->
 *   when (route) {
 *     is HomeRoute -> HomeRoute.serializer().descriptor.serialName
 *     is ProfileRoute -> ProfileRoute.serializer().descriptor.serialName
 *     is SettingsRoute -> SettingsRoute.serializer().descriptor.serialName
 *   }
 * }
 * ```
 *
 * Doing so gives each route type a stable, non-obfuscated name while leaving per-route arguments to
 * [RouteArgumentsExtractor].
 */
@ApiStatus.Experimental
internal fun interface RouteNameExtractor<T : Any> {
  fun extract(backStackEntry: T): String
}

/**
 * Extracts diagnostic route arguments from a back stack entry as map of argument name -> argument
 * values.
 *
 * **Privacy / PII**
 *
 * Values returned from [extract] are ***not*** scrubbed by the Sentry SDK before being sent to
 * Sentry. Only return arguments that are known to be safe or have been pre-scrubbed.
 *
 * **Choosing appropriate route arguments**
 *
 * Return only a small subset of route data useful for diagnostics. Data should be stable enough to
 * inspect in Sentry.
 *
 * Extractors are invoked synchronously from [SentryNavEffect] on the same apply thread that runs
 * the effect. For performance reasons, implementations should avoid large structures. Cyclic or
 * deeply nested containers will be skipped. (See `RouteTranslator` for more details.)
 *
 * **Accepted value types**
 *
 * Values may be any of the following scalar types:
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
 * Container values may be nested, and they must bottom out in supported scalar types.
 *
 * **Falls back to `toString()` or nothing**
 *
 * All non-supported types are stringified via `toString()`. If [extract] throws, no arguments are
 * recorded for the destination.
 *
 * **Using kotlinx.serialization**
 *
 * If your back stack contains `@Serializable` route types, avoid returning the entire route object
 * when it may be large, nested, or privacy-sensitive. Prefer a small set of diagnostic arguments
 * instead. For instance:
 * ```kotlin
 * val argumentsExtractor = RouteArgumentsExtractor<Any> { route ->
 *   when (route) {
 *     is HomeRoute -> emptyMap()
 *     is ProfileRoute -> mapOf("userId" to route.userId, "tab" to route.tab)
 *     is SettingsRoute -> mapOf("section" to route.section)
 *   }
 * }
 * ```
 */
@ApiStatus.Experimental
internal fun interface RouteArgumentsExtractor<T : Any> {
  fun extract(backStackEntry: T): Map<String, Any?>
}

/**
 * Holds host app-defined extractors, which convert a back stack entry of type [T] into a route name
 * and a map of zero or more route arguments. Extracted values are eventually grouped into [Route]s
 * for display.
 *
 * Extractor invocations are hidden from Compose snapshot observation so they don't impact
 * invalidation of the recompose scope that reads them.
 */
internal class RouteExtractors<T : Any>(
  val nameExtractor: RouteNameExtractor<T>,
  val argumentsExtractor: RouteArgumentsExtractor<T>?,
) {

  fun getName(backStackEntry: T): String = Snapshot.withoutReadObservation {
    nameExtractor.extract(backStackEntry)
  }

  fun getArguments(backStackEntry: T): Map<String, Any?>? = Snapshot.withoutReadObservation {
    argumentsExtractor?.extract(backStackEntry)
  }
}
