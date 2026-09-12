package io.sentry.compose.navigation3

import androidx.compose.runtime.snapshots.Snapshot

/**
 * Holds host app-defined extractors, which convert a back stack entry of type [T] into a route name
 * and a map of zero or more route arguments. Extracted values are eventually grouped into
 * [RouteEntry]s for display.
 *
 * Extractor invocations are hidden from Compose snapshot observation so they don't impact
 * invalidation of the recompose scope that reads them.
 */
internal class RouteResolvers<T : Any>(
  val nameExtractor: ((T) -> String)?,
  val argumentsExtractor: ((T) -> Map<String, Any?>)?,
) {

  fun getName(backStackEntry: T): String? = Snapshot.withoutReadObservation {
    nameExtractor?.invoke(backStackEntry)
  }

  fun getArguments(backStackEntry: T): Map<String, Any?>? = Snapshot.withoutReadObservation {
    argumentsExtractor?.invoke(backStackEntry)
  }
}
