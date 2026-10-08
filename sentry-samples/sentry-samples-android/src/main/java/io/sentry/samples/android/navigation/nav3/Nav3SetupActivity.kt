package io.sentry.samples.android.navigation.nav3

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.sentry.samples.android.navigation.common.NavigationSampleConfig
import io.sentry.samples.android.navigation.common.NavigationSetupScreen
import io.sentry.samples.android.navigation.common.applyToCurrentOptions
import io.sentry.samples.android.navigation.common.currentNavigationSampleConfigSnapshot

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
      Nav3SampleTheme {
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
