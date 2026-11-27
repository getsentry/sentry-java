package io.sentry.samples.android.navigation.nav2

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.sentry.samples.android.navigation.common.NavigationSampleConfig
import io.sentry.samples.android.navigation.common.NavigationSetupScreen
import io.sentry.samples.android.navigation.common.applyToCurrentOptions

/** Activity for configuring the developer's experience in the [Nav2Activity]. */
class Nav2SetupActivity : AppCompatActivity() {

  private var configuration by mutableStateOf(NavigationSampleConfig())

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configuration = savedInstanceState?.nav2SampleConfiguration() ?: configuration
    setContent {
      Nav2SampleTheme {
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
