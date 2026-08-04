package io.sentry.samples.android.navigation

import android.content.Context
import android.content.Intent
import io.sentry.Sentry
import io.sentry.samples.android.navigation.nav2.Nav2Activity
import io.sentry.samples.android.navigation.nav3.Nav3Activity

internal data class NavigationSampleConfig(
  val enableNavigationTransactions: Boolean = true,
  val enableNavigationBreadcrumbs: Boolean = true,
  val enableScreenTracking: Boolean = true,
  val enableActivityUiLoadTransaction: Boolean = false,
  val enableUserInteractionTransactions: Boolean = false,
  val enableUserInteractionBreadcrumbs: Boolean = false,
  val captureBackStack: Boolean = true,
  val maxCapturedBackStackEntries: Int = 10,
)

internal typealias Nav2SampleConfig = NavigationSampleConfig

internal val NavigationSampleConfig.hasOnlyActivityUiLoadTransactions: Boolean
  get() =
    enableActivityUiLoadTransaction &&
      !enableNavigationTransactions &&
      !enableUserInteractionTransactions

internal data class NavigationSampleConfigSnapshot(
  val enableScreenTracking: Boolean,
  val enableUserInteractionTransactions: Boolean,
  val enableUserInteractionBreadcrumbs: Boolean,
)

internal typealias Nav2SampleConfigSnapshot = NavigationSampleConfigSnapshot

internal fun NavigationSampleConfig.applyToCurrentOptions() {
  applyNavigationSampleOptions(
    enableScreenTracking = enableScreenTracking,
    enableUserInteractionTransactions = enableUserInteractionTransactions,
    enableUserInteractionBreadcrumbs = enableUserInteractionBreadcrumbs,
  )
}

internal fun NavigationSampleConfigSnapshot.applyToCurrentOptions() {
  applyNavigationSampleOptions(
    enableScreenTracking = enableScreenTracking,
    enableUserInteractionTransactions = enableUserInteractionTransactions,
    enableUserInteractionBreadcrumbs = enableUserInteractionBreadcrumbs,
  )
}

private fun applyNavigationSampleOptions(
  enableScreenTracking: Boolean,
  enableUserInteractionTransactions: Boolean,
  enableUserInteractionBreadcrumbs: Boolean,
) {
  val options = Sentry.getCurrentScopes().options
  options.setEnableScreenTracking(enableScreenTracking)
  options.setEnableUserInteractionTracing(enableUserInteractionTransactions)
  options.setEnableUserInteractionBreadcrumbs(enableUserInteractionBreadcrumbs)
}

internal fun currentNavigationSampleConfigSnapshot(): NavigationSampleConfigSnapshot {
  val options = Sentry.getCurrentScopes().options
  return NavigationSampleConfigSnapshot(
    enableScreenTracking = options.isEnableScreenTracking,
    enableUserInteractionTransactions = options.isEnableUserInteractionTracing,
    enableUserInteractionBreadcrumbs = options.isEnableUserInteractionBreadcrumbs,
  )
}

internal fun currentNav2SampleConfigSnapshot(): Nav2SampleConfigSnapshot =
  currentNavigationSampleConfigSnapshot()

internal fun Intent.previousNav2SampleConfigSnapshot(
  fallback: Nav2SampleConfigSnapshot
): Nav2SampleConfigSnapshot =
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

internal fun Intent.nav2SampleConfig(): Nav2SampleConfig =
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

internal fun Intent.previousNav3SampleConfigSnapshot(
  fallback: NavigationSampleConfigSnapshot
): NavigationSampleConfigSnapshot =
  NavigationSampleConfigSnapshot(
    enableScreenTracking =
      getBooleanExtra(EXTRA_NAV3_PREVIOUS_ENABLE_SCREEN_TRACKING, fallback.enableScreenTracking),
    enableUserInteractionTransactions =
      getBooleanExtra(
        EXTRA_NAV3_PREVIOUS_ENABLE_USER_INTERACTION_TRANSACTIONS,
        fallback.enableUserInteractionTransactions,
      ),
    enableUserInteractionBreadcrumbs =
      getBooleanExtra(
        EXTRA_NAV3_PREVIOUS_ENABLE_USER_INTERACTION_BREADCRUMBS,
        fallback.enableUserInteractionBreadcrumbs,
      ),
  )

internal fun Intent.nav3SampleConfig(): NavigationSampleConfig =
  NavigationSampleConfig(
    enableNavigationTransactions = getBooleanExtra(EXTRA_NAV3_ENABLE_NAVIGATION_TRANSACTIONS, true),
    enableNavigationBreadcrumbs = getBooleanExtra(EXTRA_NAV3_ENABLE_NAVIGATION_BREADCRUMBS, true),
    enableScreenTracking = getBooleanExtra(EXTRA_NAV3_ENABLE_SCREEN_TRACKING, true),
    enableActivityUiLoadTransaction =
      getBooleanExtra(EXTRA_NAV3_ENABLE_ACTIVITY_UI_LOAD_TRANSACTION, false),
    enableUserInteractionTransactions =
      getBooleanExtra(EXTRA_NAV3_ENABLE_USER_INTERACTION_TRANSACTIONS, false),
    enableUserInteractionBreadcrumbs =
      getBooleanExtra(EXTRA_NAV3_ENABLE_USER_INTERACTION_BREADCRUMBS, false),
    captureBackStack = getBooleanExtra(EXTRA_NAV3_CAPTURE_BACK_STACK, true),
    maxCapturedBackStackEntries =
      getIntExtra(EXTRA_NAV3_MAX_CAPTURED_BACK_STACK_ENTRIES, 10).coerceAtLeast(1),
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

internal fun Context.nav3LaunchIntent(
  configuration: NavigationSampleConfig,
  previousOptions: NavigationSampleConfigSnapshot,
): Intent =
  Intent(this, Nav3Activity::class.java)
    .putExtra(EXTRA_NAV3_ENABLE_NAVIGATION_TRANSACTIONS, configuration.enableNavigationTransactions)
    .putExtra(EXTRA_NAV3_ENABLE_NAVIGATION_BREADCRUMBS, configuration.enableNavigationBreadcrumbs)
    .putExtra(EXTRA_NAV3_ENABLE_SCREEN_TRACKING, configuration.enableScreenTracking)
    .putExtra(
      EXTRA_NAV3_ENABLE_ACTIVITY_UI_LOAD_TRANSACTION,
      configuration.enableActivityUiLoadTransaction,
    )
    .putExtra(
      EXTRA_NAV3_ENABLE_USER_INTERACTION_TRANSACTIONS,
      configuration.enableUserInteractionTransactions,
    )
    .putExtra(
      EXTRA_NAV3_ENABLE_USER_INTERACTION_BREADCRUMBS,
      configuration.enableUserInteractionBreadcrumbs,
    )
    .putExtra(EXTRA_NAV3_CAPTURE_BACK_STACK, configuration.captureBackStack)
    .putExtra(
      EXTRA_NAV3_MAX_CAPTURED_BACK_STACK_ENTRIES,
      configuration.maxCapturedBackStackEntries,
    )
    .putExtra(EXTRA_NAV3_PREVIOUS_ENABLE_SCREEN_TRACKING, previousOptions.enableScreenTracking)
    .putExtra(
      EXTRA_NAV3_PREVIOUS_ENABLE_USER_INTERACTION_TRANSACTIONS,
      previousOptions.enableUserInteractionTransactions,
    )
    .putExtra(
      EXTRA_NAV3_PREVIOUS_ENABLE_USER_INTERACTION_BREADCRUMBS,
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

private const val EXTRA_NAV3_ENABLE_NAVIGATION_TRANSACTIONS =
  "io.sentry.samples.android.navigation.nav3.enable_navigation_transactions"
private const val EXTRA_NAV3_ENABLE_NAVIGATION_BREADCRUMBS =
  "io.sentry.samples.android.navigation.nav3.enable_navigation_breadcrumbs"
private const val EXTRA_NAV3_ENABLE_SCREEN_TRACKING =
  "io.sentry.samples.android.navigation.nav3.enable_screen_tracking"
private const val EXTRA_NAV3_ENABLE_ACTIVITY_UI_LOAD_TRANSACTION =
  "io.sentry.samples.android.navigation.nav3.enable_activity_ui_load_transaction"
private const val EXTRA_NAV3_ENABLE_USER_INTERACTION_TRANSACTIONS =
  "io.sentry.samples.android.navigation.nav3.enable_user_interaction_transactions"
private const val EXTRA_NAV3_ENABLE_USER_INTERACTION_BREADCRUMBS =
  "io.sentry.samples.android.navigation.nav3.enable_user_interaction_breadcrumbs"
private const val EXTRA_NAV3_CAPTURE_BACK_STACK =
  "io.sentry.samples.android.navigation.nav3.capture_back_stack"
private const val EXTRA_NAV3_MAX_CAPTURED_BACK_STACK_ENTRIES =
  "io.sentry.samples.android.navigation.nav3.max_captured_back_stack_entries"
private const val EXTRA_NAV3_PREVIOUS_ENABLE_SCREEN_TRACKING =
  "io.sentry.samples.android.navigation.nav3.previous_enable_screen_tracking"
private const val EXTRA_NAV3_PREVIOUS_ENABLE_USER_INTERACTION_TRANSACTIONS =
  "io.sentry.samples.android.navigation.nav3.previous_enable_user_interaction_transactions"
private const val EXTRA_NAV3_PREVIOUS_ENABLE_USER_INTERACTION_BREADCRUMBS =
  "io.sentry.samples.android.navigation.nav3.previous_enable_user_interaction_breadcrumbs"
