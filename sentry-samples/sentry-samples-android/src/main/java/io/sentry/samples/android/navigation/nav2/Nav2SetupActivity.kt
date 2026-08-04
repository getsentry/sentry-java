package io.sentry.samples.android.navigation.nav2

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.sentry.samples.android.navigation.Nav2SampleConfig
import io.sentry.samples.android.navigation.NavigationSampleConfig
import io.sentry.samples.android.navigation.applyToCurrentOptions
import io.sentry.samples.android.navigation.common.NavigationSampleTheme
import io.sentry.samples.android.navigation.common.NavigationSetupScreen
import io.sentry.samples.android.navigation.currentNav2SampleConfigSnapshot
import io.sentry.samples.android.navigation.nav2LaunchIntent

/**
 * Activity for configuring the developer's experience in the [Nav2Activity], in particular which
 * transaction types should be active and which nav data the SDK should emit.
 */
class Nav2SetupActivity : AppCompatActivity() {

  private var configuration by mutableStateOf(NavigationSampleConfig())

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configuration = savedInstanceState?.nav2SampleConfiguration() ?: configuration
    setContent {
      NavigationSampleTheme {
        NavigationSetupScreen(
          navName = "Nav2",
          navVersion = "2",
          configuration = configuration,
          onConfigurationChanged = { updatedConfiguration ->
            configuration = updatedConfiguration
          },
          onLaunch = {
            val previousOptions = currentNav2SampleConfigSnapshot()
            configuration.applyToCurrentOptions()
            startActivity(nav2LaunchIntent(configuration, previousOptions))
          },
        )
      }
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putNav2SampleConfiguration(configuration)
  }
}

private fun Bundle.putNav2SampleConfiguration(configuration: Nav2SampleConfig) {
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
}

private fun Bundle.nav2SampleConfiguration(): Nav2SampleConfig =
  Nav2SampleConfig(
    enableNavigationTransactions = getBoolean("enable_navigation_transactions", true),
    enableNavigationBreadcrumbs = getBoolean("enable_navigation_breadcrumbs", true),
    enableScreenTracking = getBoolean("enable_screen_tracking", true),
    enableActivityUiLoadTransaction = getBoolean("enable_activity_ui_load_transaction", false),
    enableUserInteractionTransactions = getBoolean("enable_user_interaction_transactions", false),
    enableUserInteractionBreadcrumbs = getBoolean("enable_user_interaction_breadcrumbs", false),
  )
