package io.sentry.compose.navigation3.internal.backstack

import io.sentry.compose.navigation3.internal.contract.UnknownEntryReason

/** Runs [map] through [UnknownEntryReasonReporter.collect], returning its result and reason. */
internal fun <R> collectWithReason(map: () -> R): Pair<R, UnknownEntryReason?> {
  var reason: UnknownEntryReason? = null
  val result = UnknownEntryReasonReporter.collect(map) { reason = it }
  return result to reason
}
