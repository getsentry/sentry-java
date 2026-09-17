package io.sentry.compose.navigation3

import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.annotations.ApiStatus

/**
 * Extracts a human-readable route name from a back stack entry.
 *
 * **Falls back to "/unknown"**
 *
 * If [extract] throws or returns a blank route name, Sentry records the destination as "/unknown".
 * Doing so signals that name extraction needs to be fixed while avoiding misleading gaps in
 * navigation data.
 *
 * For instance, if a user navigates from `/home -> /detail -> /settings`, but the name extractor
 * for `/detail` throws, the back stack record will be `/home -> /unknown -> /settings` rather than
 * `/home -> /settings`, as the latter would be confusing.
 *
 * Note that we can't reliably fall back to the simple class name because R8 obfuscates it in
 * release builds and may obfuscate the same name differently between builds.
 *
 * **Privacy / PII**
 *
 * Values returned from [extract] are ***not*** scrubbed by the Sentry SDK before being sent to
 * Sentry. Only return names that are known to be safe or have been pre-scrubbed.
 */
@ApiStatus.Experimental
internal fun interface RouteNameExtractor<T : Any> {
  fun extract(backStackEntry: T): String
}

/**
 * Extracts diagnostic route arguments from a back stack entry as map of argument name -> argument
 * values.
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
 * **Privacy / PII**
 *
 * Values returned from [extract] are ***not*** scrubbed by the Sentry SDK before being sent to
 * Sentry. Only return arguments that are known to be safe or have been pre-scrubbed.
 *
 * **Performance**
 *
 * For the sake of performance, implementations should return only the arguments needed for
 * diagnostics and should avoid large structures. Cyclic or deeply nested containers will be
 * skipped.
 */
@ApiStatus.Experimental
internal fun interface RouteArgumentsExtractor<T : Any> {
  fun extract(backStackEntry: T): Map<String, Any?>
}

/**
 * Holds host app-defined extractors, which convert a back stack entry of type [T] into a route name
 * and a map of zero or more route arguments. Extracted values are eventually grouped into
 * [RouteEntry]s for display.
 *
 * Extractor invocations are hidden from Compose snapshot observation so they don't impact
 * invalidation of the recompose scope that reads them.
 */
internal class RouteResolvers<T : Any>(
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
