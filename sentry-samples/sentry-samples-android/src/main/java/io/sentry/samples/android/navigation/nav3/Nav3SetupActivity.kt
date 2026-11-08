package io.sentry.samples.android.navigation.nav3

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.sentry.samples.android.navigation.common.NavigationSampleConfig
import io.sentry.samples.android.navigation.common.NavigationSetupCheckboxRow
import io.sentry.samples.android.navigation.common.NavigationSetupScreen
import io.sentry.samples.android.navigation.common.NavigationSetupSection
import io.sentry.samples.android.navigation.common.applyToCurrentOptions
import io.sentry.samples.android.navigation.common.currentNavigationSampleConfigSnapshot

/**
 * Activity for configuring the developer's experience in the [Nav3Activity], matching the Nav2
 * setup flow while exposing Nav3-only back stack capture options and how the sample records
 * navigation (see [Nav3SentryNavEffectMode]).
 */
class Nav3SetupActivity : AppCompatActivity() {

  private var configuration by mutableStateOf(NavigationSampleConfig())

  private var effectMode by mutableStateOf(Nav3SentryNavEffectMode.MANUAL)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configuration = savedInstanceState?.nav3SampleConfiguration() ?: configuration
    effectMode = savedInstanceState?.nav3SentryNavEffectMode() ?: effectMode
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
            NavigationTransactionHistory.startSession()
            startActivity(
              nav3LaunchIntent(configuration, previousOptions)
                .putNav3SentryNavEffectMode(effectMode)
            )
          },
          additionalSections = {
            SentryNavEffectModeSection(
              selectedMode = effectMode,
              onModeSelected = { effectMode = it },
            )
          },
        )
      }
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putNav3SampleConfiguration(configuration)
    outState.putNav3SentryNavEffectMode(effectMode)
  }
}

@Composable
private fun SentryNavEffectModeSection(
  selectedMode: Nav3SentryNavEffectMode,
  onModeSelected: (Nav3SentryNavEffectMode) -> Unit,
) {
  NavigationSetupSection(title = "Recording") {
    Nav3SentryNavEffectMode.entries.forEach { mode ->
      NavigationSetupCheckboxRow(label = mode.label, checked = mode == selectedMode) { checked ->
        if (checked) {
          onModeSelected(mode)
        }
      }
    }
    Text(text = selectedMode.description, style = MaterialTheme.typography.bodySmall)
    Text(
      text = autoInstrumentationNote(selectedMode),
      style = MaterialTheme.typography.bodySmall,
      color =
        if (
          selectedMode.needsAutoInstrumentation && !Nav3AutoInstrumentationStatus.isInstrumented
        ) {
          MaterialTheme.colorScheme.error
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
  }
}

private fun autoInstrumentationNote(mode: Nav3SentryNavEffectMode): String =
  when {
    Nav3AutoInstrumentationStatus.isInstrumented ->
      "This build is auto-instrumented (Navigation3Instrumentation)."
    Nav3AutoInstrumentationStatus.isRequestedByBuild ->
      "This build requested auto-instrumentation, but the plugin didn't apply it. Check the " +
        "SDK version (see navigation/README.md)."
    mode.needsAutoInstrumentation ->
      "This build isn't auto-instrumented, so this mode won't behave as described. Build with " +
        "-PuseSagp -PsagpNavigation3 (see navigation/README.md)."
    else -> "This build isn't auto-instrumented."
  }
