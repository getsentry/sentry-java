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
 * All non-supported types are stringified via `toString()`.
 *
 * **Privacy / PII**
 *
 * Values returned from [extract] are ***not*** scrubbed by the Sentry SDK before being sent to
 * Sentry. Only return arguments that are known to be safe or have been pre-scrubbed.
 *
 * **Performance**
 *
 * For the sake of performance, implementations should return only the arguments needed for
 * diagnostics and should avoid large structures. Cyclic or deeply nested containers are skipped.
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
