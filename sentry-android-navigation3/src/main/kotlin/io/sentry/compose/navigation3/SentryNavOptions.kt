package io.sentry.compose.navigation3

import androidx.compose.runtime.Immutable
import org.jetbrains.annotations.ApiStatus

/**
 * Keep the default low: every captured entry may require route-name extraction, argument
 * extraction, recursive argument sanitization, and structural comparison during composition.
 */
private const val DEFAULT_MAX_CAPTURED_BACK_STACK_ENTRIES = 10

/** Configuration info for a [SentryNavEffect]. */
@ApiStatus.Experimental
@Immutable
public class SentryNavOptions(
  /**
   * Whether user-visible navigation changes should produce Sentry breadcrumbs. If `true`, a change
   * in the primary route generates a breadcrumb like `from=/Home` and `to=/Profile`.
   */
  public val enableNavigationBreadcrumbs: Boolean = true,

  /**
   * Whether a change in the primary navigation route should start a Sentry navigation transaction.
   * If `true`, a primary-route change from `/Home` to `/Profile` starts a `/Profile` transaction
   * and finishes the current `/Home` transaction.
   */
  public val enableNavigationTransactions: Boolean = true,

  /**
   * Whether Sentry should attach captured back stack information to event context so it can be
   * included with crashes, errors, and other captured events. If `true`, a stack like `/Home ->
   * /Profile` is recorded alongside the event, ordered with the current/top entry first.
   *
   * When enabled, Sentry resolves and sanitizes up to [maxCapturedBackStackEntries] routes whenever
   * the back stack changes. Argument sanitization shares one per-update value budget across the
   * captured stack, so older entries may omit arguments once the current update exceeds that
   * budget. Keep extractors lightweight and reduce the max captured count for unusually deep stacks
   * or expensive argument structures.
   *
   * Future Navigation 3 APIs may attach richer navigation stack state for multiple retained stacks
   * or visible entries.
   */
  public val captureBackStack: Boolean = true,

  /**
   * Maximum number of entries Sentry records per captured back stack (starting with the most
   * recent).
   */
  public val maxCapturedBackStackEntries: Int = DEFAULT_MAX_CAPTURED_BACK_STACK_ENTRIES,
) {
  init {
    require(maxCapturedBackStackEntries > 0) {
      "maxCapturedBackStackEntries must be positive, was $maxCapturedBackStackEntries"
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
