package io.sentry.compose.navigation3

import androidx.compose.runtime.Immutable
import org.jetbrains.annotations.ApiStatus

// Keep the default low: every captured entry may require route-name extraction, argument
// extraction, and recursive argument sanitization when navigation changes are observed.
private const val DEFAULT_MAX_CAPTURED_BACK_STACK_ENTRIES = 10

/**
 * Configuration info for a [SentryNavEffect].
 *
 * Instances are immutable; create one with the [SentryNavOptions] DSL:
 * ```kotlin
 * val options = SentryNavOptions {
 *   captureBackStack = false
 *   maxCapturedBackStackEntries = 5
 * }
 * ```
 */
@ApiStatus.Experimental
@Immutable
internal class SentryNavOptions
private constructor(
  val enableNavigationBreadcrumbs: Boolean,
  val enableNavigationTransactions: Boolean,
  val captureBackStack: Boolean,
  val maxCapturedBackStackEntries: Int,
) {

  init {
    require(maxCapturedBackStackEntries >= 0) {
      "maxCapturedBackStackEntries must be non-negative, was $maxCapturedBackStackEntries"
    }
  }

  /**
   * Mutable builder for [SentryNavOptions]. Prefer the [SentryNavOptions] DSL to using this
   * directly.
   *
   * Lets us keep the resulting instance [Immutable] while preserving binary compatibility, should
   * new properties be added in the future.
   */
  class Builder {

    /**
     * Whether navigation should produce Sentry breadcrumbs. If `true`, a new nav destination
     * generates a breadcrumb like `from=/Home` and `to=/Profile`.
     */
    var enableNavigationBreadcrumbs: Boolean = true

    /**
     * Whether navigation should start a Sentry transaction. If `true`, navigating from `/Home` to
     * `/Profile` starts a `/Profile` transaction and finishes the current `/Home` transaction.
     */
    var enableNavigationTransactions: Boolean = true

    /**
     * Whether Sentry should record back stack information for inclusion with crashes, errors, and
     * other captured events. If `true`, a stack like `/Home -> /Profile` is recorded alongside the
     * event, ordered with the current/top entry first.
     */
    var captureBackStack: Boolean = true

    /**
     * Maximum number of entries Sentry should record per captured back stack (starting with the
     * most recent). Set to `0` to capture no back stack entries.
     *
     * Note: Sentry resolves and sanitizes up to [maxCapturedBackStackEntries] names + argument maps
     * whenever your back stack changes. Keep name and argument extractors lightweight, and reduce
     * the max captured count if extractor work is unusually expensive.
     */
    var maxCapturedBackStackEntries: Int = DEFAULT_MAX_CAPTURED_BACK_STACK_ENTRIES

    fun build(): SentryNavOptions =
      SentryNavOptions(
        enableNavigationBreadcrumbs = enableNavigationBreadcrumbs,
        enableNavigationTransactions = enableNavigationTransactions,
        captureBackStack = captureBackStack,
        maxCapturedBackStackEntries = maxCapturedBackStackEntries,
      )
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

/** Creates [SentryNavOptions]. Optionally configure it via [configure]. */
@ApiStatus.Experimental
internal fun SentryNavOptions(
  configure: SentryNavOptions.Builder.() -> Unit = {}
): SentryNavOptions = SentryNavOptions.Builder().apply(configure).build()
