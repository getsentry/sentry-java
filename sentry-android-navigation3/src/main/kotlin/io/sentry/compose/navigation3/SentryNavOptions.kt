package io.sentry.compose.navigation3

import androidx.compose.runtime.Immutable

// Keep the default low: every captured entry may require route-name extraction, argument
// extraction, and recursive argument sanitization when navigation changes are observed.
private const val DEFAULT_MAX_CAPTURED_BACK_STACK_ENTRIES = 10

/** Configuration info for a [SentryNavEffect]. */
@Immutable
internal class SentryNavOptions(
  /**
   * Whether navigation should produce Sentry breadcrumbs. If `true`, a new nav destination
   * generates a breadcrumb like `from=/Home` and `to=/Profile`.
   */
  val enableNavigationBreadcrumbs: Boolean = true,

  /**
   * Whether navigation should start a Sentry transaction. If `true`, navigating from `/Home` to
   * `/Profile` starts a `/Profile` transaction and finishes the current `/Home` transaction.
   */
  val enableNavigationTransactions: Boolean = true,

  /**
   * Whether Sentry should record back stack information for inclusion with crashes, errors, and
   * other captured events. If `true`, a stack like `/Home -> /Profile` is recorded alongside the
   * event, ordered with the current/top entry first.
   */
  val captureBackStack: Boolean = true,

  /**
   * Maximum number of entries Sentry should record per captured back stack (starting with the most
   * recent). Set to `0` to capture no back stack entries.
   *
   * Note: Sentry resolves and sanitizes up to [maxCapturedBackStackEntries] names + argument maps
   * whenever your back stack changes. Keep name and argument extractors lightweight, and reduce the
   * max captured count if extractor work is unusually expensive.
   */
  val maxCapturedBackStackEntries: Int = DEFAULT_MAX_CAPTURED_BACK_STACK_ENTRIES,
) {

  init {
    require(maxCapturedBackStackEntries >= 0) {
      "maxCapturedBackStackEntries must be non-negative, was $maxCapturedBackStackEntries"
    }
  }

  override fun equals(other: Any?): Boolean =
    this === other ||
      (other is SentryNavOptions &&
        enableNavigationBreadcrumbs == other.enableNavigationBreadcrumbs &&
        enableNavigationTransactions == other.enableNavigationTransactions &&
        captureBackStack == other.captureBackStack &&
        maxCapturedBackStackEntries == other.maxCapturedBackStackEntries)

  override fun hashCode(): Int {
    var result = enableNavigationBreadcrumbs.hashCode()
    result = 31 * result + enableNavigationTransactions.hashCode()
    result = 31 * result + captureBackStack.hashCode()
    result = 31 * result + maxCapturedBackStackEntries
    return result
  }
}
