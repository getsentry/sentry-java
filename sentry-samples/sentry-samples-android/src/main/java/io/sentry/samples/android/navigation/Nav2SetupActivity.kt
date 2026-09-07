package io.sentry.samples.android.navigation

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Activity for configuring the developer's experience in the [Nav2Activity], in particular which
 * transaction types should be active and which nav data the SDK should emit.
 */
class Nav2SetupActivity : AppCompatActivity() {

  private var configuration by mutableStateOf(Nav2SampleConfig())

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configuration = savedInstanceState?.nav2SampleConfiguration() ?: configuration
    setContent {
      MaterialTheme {
        Nav2SetupScreen(
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

@Composable
private fun Nav2SetupScreen(
  configuration: Nav2SampleConfig,
  onConfigurationChanged: (Nav2SampleConfig) -> Unit,
  onLaunch: () -> Unit,
) {
  Column(
    modifier =
      Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .safeDrawingPadding()
        .verticalScroll(rememberScrollState())
        .padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text(
      text = "Navigation 2 Setup",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
      text =
        "Choose which auto-instrumentation features should be active before the Nav2 sample launches.",
      style = MaterialTheme.typography.bodyMedium,
    )
    Nav2SetupSection(title = "Navigation") {
      Nav2SetupCheckboxRow(
        label = "Navigation transactions",
        checked = configuration.enableNavigationTransactions,
      ) {
        onConfigurationChanged(configuration.copy(enableNavigationTransactions = it))
      }
      Nav2SetupCheckboxRow(
        label = "Navigation breadcrumbs",
        checked = configuration.enableNavigationBreadcrumbs,
      ) {
        onConfigurationChanged(configuration.copy(enableNavigationBreadcrumbs = it))
      }
      Nav2SetupCheckboxRow(
        label = "Screen tracking",
        checked = configuration.enableScreenTracking,
      ) {
        onConfigurationChanged(configuration.copy(enableScreenTracking = it))
      }
    }
    Nav2SetupSection(title = "Other auto-transactions") {
      Nav2SetupCheckboxRow(
        label = "Activity ui.load transaction",
        checked = configuration.enableActivityUiLoadTransaction,
        helpText = ACTIVITY_UI_LOAD_HELP_TEXT,
      ) {
        onConfigurationChanged(configuration.copy(enableActivityUiLoadTransaction = it))
      }
      Nav2SetupCheckboxRow(
        label = "User interaction transactions",
        checked = configuration.enableUserInteractionTransactions,
      ) {
        onConfigurationChanged(configuration.copy(enableUserInteractionTransactions = it))
      }
    }
    Nav2SetupSection(title = "Other breadcrumbs") {
      Nav2SetupCheckboxRow(
        label = "User interaction breadcrumbs",
        checked = configuration.enableUserInteractionBreadcrumbs,
      ) {
        onConfigurationChanged(configuration.copy(enableUserInteractionBreadcrumbs = it))
      }
    }
    Button(onClick = onLaunch, modifier = Modifier.fillMaxWidth()) {
      Text(
        "Launch Nav2 Sample",
        fontSize = 18.sp,
      )
    }
  }
}

@Composable
private fun Nav2SetupSection(title: String, content: @Composable () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      text = title,
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        content()
      }
    }
  }
}

@Composable
private fun Nav2SetupCheckboxRow(
  label: String,
  checked: Boolean,
  helpText: String? = null,
  onCheckedChange: (Boolean) -> Unit,
) {
  val sentryPink = Color(0xFFC85B9C)
  val rowShape: Shape = RoundedCornerShape(12.dp)
  val rowBackground by
    animateColorAsState(
      targetValue = if (checked) sentryPink.copy(alpha = 0.14f) else Color.Transparent,
      animationSpec = tween(durationMillis = 220),
      label = "nav2-setup-toggle-background",
    )
  val switchColors =
    SwitchDefaults.colors(
      checkedTrackColor = sentryPink,
      checkedBorderColor = sentryPink,
      checkedThumbColor = Color.White,
    )
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clip(rowShape)
        .background(rowBackground, rowShape)
        .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
        .defaultMinSize(minHeight = 52.dp)
        .padding(horizontal = 12.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Switch(
      checked = checked,
      onCheckedChange = null,
      colors = switchColors,
      modifier = Modifier.scale(0.8f),
    )
    Row(
      modifier = Modifier.weight(1f),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(text = label, style = MaterialTheme.typography.titleMedium)
      if (helpText != null) {
        Nav2SetupHelpTooltip(helpText)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Nav2SetupHelpTooltip(text: String) {
  val tooltipState = rememberTooltipState(isPersistent = true)
  val scope = rememberCoroutineScope()

  LaunchedEffect(tooltipState.isVisible) {
    if (tooltipState.isVisible) {
      delay(4000)
      tooltipState.dismiss()
    }
  }

  TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
    tooltip = { PlainTooltip { Text(text) } },
    state = tooltipState,
  ) {
    Icon(
      imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
      contentDescription = "Activity ui.load transaction help",
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier =
        Modifier.padding(start = 6.dp).size(20.dp).clickable {
          scope.launch { tooltipState.show() }
        },
    )
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

private const val ACTIVITY_UI_LOAD_HELP_TEXT =
  "The sample simulates disabling ui.load transactions, as actual activity lifecycle " +
    "tracing is fixed when the SDK initializes."
