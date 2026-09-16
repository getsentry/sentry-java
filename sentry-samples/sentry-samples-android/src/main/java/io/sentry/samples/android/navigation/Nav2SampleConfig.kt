package io.sentry.samples.android.navigation

import android.content.Context
import android.content.Intent
import io.sentry.Sentry

internal data class Nav2SampleConfig(
  val enableNavigationTransactions: Boolean = true,
  val enableNavigationBreadcrumbs: Boolean = true,
  val enableScreenTracking: Boolean = true,
  val enableActivityUiLoadTransaction: Boolean = false,
  val enableUserInteractionTransactions: Boolean = false,
  val enableUserInteractionBreadcrumbs: Boolean = false,
)

internal val Nav2SampleConfig.hasOnlyActivityUiLoadTransactions: Boolean
  get() =
    enableActivityUiLoadTransaction &&
      !enableNavigationTransactions &&
      !enableUserInteractionTransactions

internal data class Nav2SampleConfigSnapshot(
  val enableScreenTracking: Boolean,
  val enableUserInteractionTransactions: Boolean,
  val enableUserInteractionBreadcrumbs: Boolean,
)

internal fun Nav2SampleConfig.applyToCurrentOptions() {
  applyNav2SampleOptions(
    enableScreenTracking = enableScreenTracking,
    enableUserInteractionTransactions = enableUserInteractionTransactions,
    enableUserInteractionBreadcrumbs = enableUserInteractionBreadcrumbs,
  )
}

internal fun Nav2SampleConfigSnapshot.applyToCurrentOptions() {
  applyNav2SampleOptions(
    enableScreenTracking = enableScreenTracking,
    enableUserInteractionTransactions = enableUserInteractionTransactions,
    enableUserInteractionBreadcrumbs = enableUserInteractionBreadcrumbs,
  )
}

private fun applyNav2SampleOptions(
  enableScreenTracking: Boolean,
  enableUserInteractionTransactions: Boolean,
  enableUserInteractionBreadcrumbs: Boolean,
) {
  val options = Sentry.getCurrentScopes().options
  options.setEnableScreenTracking(enableScreenTracking)
  options.setEnableUserInteractionTracing(enableUserInteractionTransactions)
  options.setEnableUserInteractionBreadcrumbs(enableUserInteractionBreadcrumbs)
}

internal fun currentNav2SampleConfigSnapshot(): Nav2SampleConfigSnapshot {
  val options = Sentry.getCurrentScopes().options
  return Nav2SampleConfigSnapshot(
    enableScreenTracking = options.isEnableScreenTracking,
    enableUserInteractionTransactions = options.isEnableUserInteractionTracing,
    enableUserInteractionBreadcrumbs = options.isEnableUserInteractionBreadcrumbs,
  )
}

internal fun Intent.previousNav2SampleConfigSnapshot(
  fallback: Nav2SampleConfigSnapshot
): Nav2SampleConfigSnapshot =
  Nav2SampleConfigSnapshot(
    enableScreenTracking =
      getBooleanExtra(EXTRA_PREVIOUS_ENABLE_SCREEN_TRACKING, fallback.enableScreenTracking),
    enableUserInteractionTransactions =
      getBooleanExtra(
        EXTRA_PREVIOUS_ENABLE_USER_INTERACTION_TRANSACTIONS,
        fallback.enableUserInteractionTransactions,
      ),
    enableUserInteractionBreadcrumbs =
      getBooleanExtra(
        EXTRA_PREVIOUS_ENABLE_USER_INTERACTION_BREADCRUMBS,
        fallback.enableUserInteractionBreadcrumbs,
      ),
  )

internal fun Intent.nav2SampleConfig(): Nav2SampleConfig =
  Nav2SampleConfig(
    enableNavigationTransactions = getBooleanExtra(EXTRA_ENABLE_NAVIGATION_TRANSACTIONS, true),
    enableNavigationBreadcrumbs = getBooleanExtra(EXTRA_ENABLE_NAVIGATION_BREADCRUMBS, true),
    enableScreenTracking = getBooleanExtra(EXTRA_ENABLE_SCREEN_TRACKING, true),
    enableActivityUiLoadTransaction =
      getBooleanExtra(EXTRA_ENABLE_ACTIVITY_UI_LOAD_TRANSACTION, false),
    enableUserInteractionTransactions =
      getBooleanExtra(EXTRA_ENABLE_USER_INTERACTION_TRANSACTIONS, false),
    enableUserInteractionBreadcrumbs =
      getBooleanExtra(EXTRA_ENABLE_USER_INTERACTION_BREADCRUMBS, false),
  )

internal fun Context.nav2LaunchIntent(
  configuration: Nav2SampleConfig,
  previousOptions: Nav2SampleConfigSnapshot,
): Intent =
  Intent(this, Nav2Activity::class.java)
    .putExtra(EXTRA_ENABLE_NAVIGATION_TRANSACTIONS, configuration.enableNavigationTransactions)
    .putExtra(EXTRA_ENABLE_NAVIGATION_BREADCRUMBS, configuration.enableNavigationBreadcrumbs)
    .putExtra(EXTRA_ENABLE_SCREEN_TRACKING, configuration.enableScreenTracking)
    .putExtra(
      EXTRA_ENABLE_ACTIVITY_UI_LOAD_TRANSACTION,
      configuration.enableActivityUiLoadTransaction,
    )
    .putExtra(
      EXTRA_ENABLE_USER_INTERACTION_TRANSACTIONS,
      configuration.enableUserInteractionTransactions,
    )
    .putExtra(
      EXTRA_ENABLE_USER_INTERACTION_BREADCRUMBS,
      configuration.enableUserInteractionBreadcrumbs,
    )
    .putExtra(EXTRA_PREVIOUS_ENABLE_SCREEN_TRACKING, previousOptions.enableScreenTracking)
    .putExtra(
      EXTRA_PREVIOUS_ENABLE_USER_INTERACTION_TRANSACTIONS,
      previousOptions.enableUserInteractionTransactions,
    )
    .putExtra(
      EXTRA_PREVIOUS_ENABLE_USER_INTERACTION_BREADCRUMBS,
      previousOptions.enableUserInteractionBreadcrumbs,
    )

private const val EXTRA_ENABLE_NAVIGATION_TRANSACTIONS =
  "io.sentry.samples.android.navigation.enable_navigation_transactions"
private const val EXTRA_ENABLE_NAVIGATION_BREADCRUMBS =
  "io.sentry.samples.android.navigation.enable_navigation_breadcrumbs"
private const val EXTRA_ENABLE_SCREEN_TRACKING =
  "io.sentry.samples.android.navigation.enable_screen_tracking"
private const val EXTRA_ENABLE_ACTIVITY_UI_LOAD_TRANSACTION =
  "io.sentry.samples.android.navigation.enable_activity_ui_load_transaction"
private const val EXTRA_ENABLE_USER_INTERACTION_TRANSACTIONS =
  "io.sentry.samples.android.navigation.enable_user_interaction_transactions"
private const val EXTRA_ENABLE_USER_INTERACTION_BREADCRUMBS =
  "io.sentry.samples.android.navigation.enable_user_interaction_breadcrumbs"
private const val EXTRA_PREVIOUS_ENABLE_SCREEN_TRACKING =
  "io.sentry.samples.android.navigation.previous_enable_screen_tracking"
private const val EXTRA_PREVIOUS_ENABLE_USER_INTERACTION_TRANSACTIONS =
  "io.sentry.samples.android.navigation.previous_enable_user_interaction_transactions"
private const val EXTRA_PREVIOUS_ENABLE_USER_INTERACTION_BREADCRUMBS =
  "io.sentry.samples.android.navigation.previous_enable_user_interaction_breadcrumbs"
