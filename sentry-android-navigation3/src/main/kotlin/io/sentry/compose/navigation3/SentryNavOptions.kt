package io.sentry.compose.navigation3

import androidx.compose.runtime.Immutable
import org.jetbrains.annotations.ApiStatus

// Keep the default low: every captured entry may require route-name extraction, argument
// extraction, and recursive argument sanitization when navigation changes are observed.
private const val DEFAULT_MAX_CAPTURED_BACK_STACK_ENTRIES = 10

/**
 * Configuration info for a [SentryNavEffect].
 */
@ApiStatus.Experimental
@Immutable
public class SentryNavOptions(
  public val enableNavigationBreadcrumbs: Boolean = true,
  public val enableNavigationTransactions: Boolean = true,
  public val captureBackStack: Boolean = true,
  public val maxCapturedBackStackEntries: Int = DEFAULT_MAX_CAPTURED_BACK_STACK_ENTRIES,
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
