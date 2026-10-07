package io.sentry.samples.android.navigation.nav2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.sentry.samples.android.navigation.common.NavigationSampleConfig
import io.sentry.samples.android.navigation.common.NavigationSampleConfigSnapshot
import io.sentry.samples.android.navigation.common.currentNavigationSampleConfigSnapshot

internal fun currentNav2SampleConfigSnapshot(): NavigationSampleConfigSnapshot =
  currentNavigationSampleConfigSnapshot()

internal fun Intent.previousNav2SampleConfigSnapshot(
  fallback: NavigationSampleConfigSnapshot
): NavigationSampleConfigSnapshot =
  NavigationSampleConfigSnapshot(
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

internal fun Intent.nav2SampleConfig(): NavigationSampleConfig =
  NavigationSampleConfig(
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
  configuration: NavigationSampleConfig,
  previousOptions: NavigationSampleConfigSnapshot,
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

internal fun Bundle.putNav2SampleConfiguration(configuration: NavigationSampleConfig) {
  putBoolean(STATE_ENABLE_NAVIGATION_TRANSACTIONS, configuration.enableNavigationTransactions)
  putBoolean(STATE_ENABLE_NAVIGATION_BREADCRUMBS, configuration.enableNavigationBreadcrumbs)
  putBoolean(STATE_ENABLE_SCREEN_TRACKING, configuration.enableScreenTracking)
  putBoolean(
    STATE_ENABLE_ACTIVITY_UI_LOAD_TRANSACTION,
    configuration.enableActivityUiLoadTransaction,
  )
  putBoolean(
    STATE_ENABLE_USER_INTERACTION_TRANSACTIONS,
    configuration.enableUserInteractionTransactions,
  )
  putBoolean(
    STATE_ENABLE_USER_INTERACTION_BREADCRUMBS,
    configuration.enableUserInteractionBreadcrumbs,
  )
}

internal fun Bundle.nav2SampleConfiguration(): NavigationSampleConfig =
  NavigationSampleConfig(
    enableNavigationTransactions = getBoolean(STATE_ENABLE_NAVIGATION_TRANSACTIONS, true),
    enableNavigationBreadcrumbs = getBoolean(STATE_ENABLE_NAVIGATION_BREADCRUMBS, true),
    enableScreenTracking = getBoolean(STATE_ENABLE_SCREEN_TRACKING, true),
    enableActivityUiLoadTransaction = getBoolean(STATE_ENABLE_ACTIVITY_UI_LOAD_TRANSACTION, false),
    enableUserInteractionTransactions =
      getBoolean(STATE_ENABLE_USER_INTERACTION_TRANSACTIONS, false),
    enableUserInteractionBreadcrumbs = getBoolean(STATE_ENABLE_USER_INTERACTION_BREADCRUMBS, false),
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
private const val STATE_ENABLE_NAVIGATION_TRANSACTIONS = "enable_navigation_transactions"
private const val STATE_ENABLE_NAVIGATION_BREADCRUMBS = "enable_navigation_breadcrumbs"
private const val STATE_ENABLE_SCREEN_TRACKING = "enable_screen_tracking"
private const val STATE_ENABLE_ACTIVITY_UI_LOAD_TRANSACTION = "enable_activity_ui_load_transaction"
private const val STATE_ENABLE_USER_INTERACTION_TRANSACTIONS =
  "enable_user_interaction_transactions"
private const val STATE_ENABLE_USER_INTERACTION_BREADCRUMBS = "enable_user_interaction_breadcrumbs"
