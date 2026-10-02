package io.sentry.samples.android.navigation.nav3

import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.sentry.samples.android.navigation.common.NavigationSampleConfig
import io.sentry.samples.android.navigation.common.NavigationSampleConfigSnapshot

internal fun Intent.previousNav3SampleConfigSnapshot(
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

internal fun Intent.nav3SampleConfig(): NavigationSampleConfig =
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
    captureBackStack = getBooleanExtra(EXTRA_CAPTURE_BACK_STACK, true),
    maxCapturedBackStackEntries =
      getIntExtra(EXTRA_MAX_CAPTURED_BACK_STACK_ENTRIES, 10).coerceAtLeast(1),
  )

internal fun Context.nav3LaunchIntent(
  configuration: NavigationSampleConfig,
  previousOptions: NavigationSampleConfigSnapshot,
): Intent =
  Intent(this, Nav3Activity::class.java)
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
    .putExtra(EXTRA_CAPTURE_BACK_STACK, configuration.captureBackStack)
    .putExtra(EXTRA_MAX_CAPTURED_BACK_STACK_ENTRIES, configuration.maxCapturedBackStackEntries)
    .putExtra(EXTRA_PREVIOUS_ENABLE_SCREEN_TRACKING, previousOptions.enableScreenTracking)
    .putExtra(
      EXTRA_PREVIOUS_ENABLE_USER_INTERACTION_TRANSACTIONS,
      previousOptions.enableUserInteractionTransactions,
    )
    .putExtra(
      EXTRA_PREVIOUS_ENABLE_USER_INTERACTION_BREADCRUMBS,
      previousOptions.enableUserInteractionBreadcrumbs,
    )

internal fun Bundle.putNav3SampleConfiguration(configuration: NavigationSampleConfig) {
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
  putBoolean(STATE_CAPTURE_BACK_STACK, configuration.captureBackStack)
  putInt(STATE_MAX_CAPTURED_BACK_STACK_ENTRIES, configuration.maxCapturedBackStackEntries)
}

internal fun Bundle.nav3SampleConfiguration(): NavigationSampleConfig =
  NavigationSampleConfig(
    enableNavigationTransactions = getBoolean(STATE_ENABLE_NAVIGATION_TRANSACTIONS, true),
    enableNavigationBreadcrumbs = getBoolean(STATE_ENABLE_NAVIGATION_BREADCRUMBS, true),
    enableScreenTracking = getBoolean(STATE_ENABLE_SCREEN_TRACKING, true),
    enableActivityUiLoadTransaction = getBoolean(STATE_ENABLE_ACTIVITY_UI_LOAD_TRANSACTION, false),
    enableUserInteractionTransactions =
      getBoolean(STATE_ENABLE_USER_INTERACTION_TRANSACTIONS, false),
    enableUserInteractionBreadcrumbs = getBoolean(STATE_ENABLE_USER_INTERACTION_BREADCRUMBS, false),
    captureBackStack = getBoolean(STATE_CAPTURE_BACK_STACK, true),
    maxCapturedBackStackEntries =
      getInt(STATE_MAX_CAPTURED_BACK_STACK_ENTRIES, 10).coerceAtLeast(1),
  )

private const val EXTRA_PREFIX = "io.sentry.samples.android.navigation.nav3"
private const val EXTRA_ENABLE_NAVIGATION_TRANSACTIONS =
  "$EXTRA_PREFIX.enable_navigation_transactions"
private const val EXTRA_ENABLE_NAVIGATION_BREADCRUMBS =
  "$EXTRA_PREFIX.enable_navigation_breadcrumbs"
private const val EXTRA_ENABLE_SCREEN_TRACKING = "$EXTRA_PREFIX.enable_screen_tracking"
private const val EXTRA_ENABLE_ACTIVITY_UI_LOAD_TRANSACTION =
  "$EXTRA_PREFIX.enable_activity_ui_load_transaction"
private const val EXTRA_ENABLE_USER_INTERACTION_TRANSACTIONS =
  "$EXTRA_PREFIX.enable_user_interaction_transactions"
private const val EXTRA_ENABLE_USER_INTERACTION_BREADCRUMBS =
  "$EXTRA_PREFIX.enable_user_interaction_breadcrumbs"
private const val EXTRA_CAPTURE_BACK_STACK = "$EXTRA_PREFIX.capture_back_stack"
private const val EXTRA_MAX_CAPTURED_BACK_STACK_ENTRIES =
  "$EXTRA_PREFIX.max_captured_back_stack_entries"
private const val EXTRA_PREVIOUS_ENABLE_SCREEN_TRACKING =
  "$EXTRA_PREFIX.previous_enable_screen_tracking"
private const val EXTRA_PREVIOUS_ENABLE_USER_INTERACTION_TRANSACTIONS =
  "$EXTRA_PREFIX.previous_enable_user_interaction_transactions"
private const val EXTRA_PREVIOUS_ENABLE_USER_INTERACTION_BREADCRUMBS =
  "$EXTRA_PREFIX.previous_enable_user_interaction_breadcrumbs"

private const val STATE_ENABLE_NAVIGATION_TRANSACTIONS = "enable_navigation_transactions"
private const val STATE_ENABLE_NAVIGATION_BREADCRUMBS = "enable_navigation_breadcrumbs"
private const val STATE_ENABLE_SCREEN_TRACKING = "enable_screen_tracking"
private const val STATE_ENABLE_ACTIVITY_UI_LOAD_TRANSACTION = "enable_activity_ui_load_transaction"
private const val STATE_ENABLE_USER_INTERACTION_TRANSACTIONS =
  "enable_user_interaction_transactions"
private const val STATE_ENABLE_USER_INTERACTION_BREADCRUMBS = "enable_user_interaction_breadcrumbs"
private const val STATE_CAPTURE_BACK_STACK = "capture_back_stack"
private const val STATE_MAX_CAPTURED_BACK_STACK_ENTRIES = "max_captured_back_stack_entries"
