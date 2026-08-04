package io.sentry.compose.navigation3

import org.jetbrains.annotations.ApiStatus

/** Configuration info for a [SentryNav3Effect]. */
@ApiStatus.Experimental
public class SentryNav3Options {
  /**
   * Whether user-visible navigation changes should produce Sentry breadcrumbs. If `true`, a change
   * in the primary route generates a breadcrumb like `from=/Home` and `to=/Profile`.
   */
  public var enableNavigationBreadcrumbs: Boolean = true

  /**
   * Whether a change in the primary navigation route should start a Sentry navigation transaction.
   * If `true`, a primary-route change from `/Home` to `/Profile` starts a `/Profile` transaction
   * and finishes the current `/Home` transaction.
   */
  public var enableNavigationTransactions: Boolean = true

  /**
   * Whether Sentry should attach captured back stack information to event context so it can be
   * included with crashes, errors, and other captured events. If `true`, a stack like `/Home ->
   * /Profile` is recorded alongside the event, ordered with the current/top entry first.
   *
   * Future Navigation 3 APIs may attach richer navigation stack state for multiple retained stacks
   * or visible entries.
   */
  public var captureBackStack: Boolean = true

  /**
   * Maximum number of entries Sentry records per captured back stack (starting with the most
   * recent).
   */
  public var maxCapturedBackStackEntries: Int = 30
}
