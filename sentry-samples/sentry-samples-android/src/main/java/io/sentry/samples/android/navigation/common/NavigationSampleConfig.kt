package io.sentry.samples.android.navigation.common

import io.sentry.Sentry

/** Configuration shared by nav sample apps. */
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
