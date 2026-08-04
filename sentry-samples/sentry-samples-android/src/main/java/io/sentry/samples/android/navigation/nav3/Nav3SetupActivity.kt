package io.sentry.samples.android.navigation.nav3

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.sentry.samples.android.navigation.NavigationSampleConfig
import io.sentry.samples.android.navigation.applyToCurrentOptions
import io.sentry.samples.android.navigation.common.NavigationSampleTheme
import io.sentry.samples.android.navigation.common.NavigationSetupScreen
import io.sentry.samples.android.navigation.currentNavigationSampleConfigSnapshot
import io.sentry.samples.android.navigation.nav3LaunchIntent

/**
 * Activity for configuring the developer's experience in the [Nav3Activity], matching the Nav2
 * setup flow while exposing Nav3-only back stack capture options.
 */
class Nav3SetupActivity : AppCompatActivity() {

  private var configuration by mutableStateOf(NavigationSampleConfig())

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configuration = savedInstanceState?.nav3SampleConfiguration() ?: configuration
    setContent {
      NavigationSampleTheme {
        NavigationSetupScreen(
          navName = "Nav3",
          navVersion = "3",
          configuration = configuration,
          showBackStackControls = true,
          onConfigurationChanged = { updatedConfiguration ->
            configuration = updatedConfiguration
          },
          onLaunch = {
            val previousOptions = currentNavigationSampleConfigSnapshot()
            configuration.applyToCurrentOptions()
            startActivity(nav3LaunchIntent(configuration, previousOptions))
          },
        )
      }
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putNav3SampleConfiguration(configuration)
  }
}

private fun Bundle.putNav3SampleConfiguration(configuration: NavigationSampleConfig) {
  putBoolean("enable_navigation_transactions", configuration.enableNavigationTransactions)
  putBoolean("enable_navigation_breadcrumbs", configuration.enableNavigationBreadcrumbs)
  putBoolean("enable_screen_tracking", configuration.enableScreenTracking)
  putBoolean("enable_activity_ui_load_transaction", configuration.enableActivityUiLoadTransaction)
  putBoolean(
    "enable_user_interaction_transactions",
    configuration.enableUserInteractionTransactions,
  )
  putBoolean(
    "enable_user_interaction_breadcrumbs",
    configuration.enableUserInteractionBreadcrumbs,
  )
  putBoolean("capture_back_stack", configuration.captureBackStack)
  putInt("max_captured_back_stack_entries", configuration.maxCapturedBackStackEntries)
}

private fun Bundle.nav3SampleConfiguration(): NavigationSampleConfig =
  NavigationSampleConfig(
    enableNavigationTransactions = getBoolean("enable_navigation_transactions", true),
    enableNavigationBreadcrumbs = getBoolean("enable_navigation_breadcrumbs", true),
    enableScreenTracking = getBoolean("enable_screen_tracking", true),
    enableActivityUiLoadTransaction = getBoolean("enable_activity_ui_load_transaction", false),
    enableUserInteractionTransactions =
      getBoolean(
        "enable_user_interaction_transactions",
        false,
      ),
    enableUserInteractionBreadcrumbs = getBoolean("enable_user_interaction_breadcrumbs", false),
    captureBackStack = getBoolean("capture_back_stack", true),
    maxCapturedBackStackEntries =
      getInt(
          "max_captured_back_stack_entries",
          10,
        )
        .coerceAtLeast(1),
  )
