package io.sentry.samples.android.navigation.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.sentry.samples.android.R
import io.sentry.samples.android.navigation.NavigationSampleConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun NavigationSetupScreen(
  navName: String,
  navVersion: String,
  configuration: NavigationSampleConfig,
  showBackStackControls: Boolean = false,
  onConfigurationChanged: (NavigationSampleConfig) -> Unit,
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
      text = "Navigation $navVersion Setup",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
      text =
        "Choose which auto-instrumentation features should be active before the $navName sample launches.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onBackground,
    )
    NavigationSetupSection(title = "Navigation") {
      NavigationSetupCheckboxRow(
        label = "Navigation transactions",
        checked = configuration.enableNavigationTransactions,
      ) {
        onConfigurationChanged(configuration.copy(enableNavigationTransactions = it))
      }
      NavigationSetupCheckboxRow(
        label = "Navigation breadcrumbs",
        checked = configuration.enableNavigationBreadcrumbs,
      ) {
        onConfigurationChanged(configuration.copy(enableNavigationBreadcrumbs = it))
      }
      NavigationSetupCheckboxRow(
        label = "Screen tracking",
        checked = configuration.enableScreenTracking,
      ) {
        onConfigurationChanged(configuration.copy(enableScreenTracking = it))
      }
      if (showBackStackControls) {
        NavigationSetupCheckboxRow(
          label = "Capture back stack",
          checked = configuration.captureBackStack,
        ) {
          onConfigurationChanged(configuration.copy(captureBackStack = it))
        }
        NavigationSetupCounterRow(
          label = "Max captured back stack entries",
          value = configuration.maxCapturedBackStackEntries,
          enabled = configuration.captureBackStack,
        ) {
          onConfigurationChanged(configuration.copy(maxCapturedBackStackEntries = it))
        }
      }
    }
    NavigationSetupSection(title = "Other auto-transactions") {
      NavigationSetupCheckboxRow(
        label = "Activity ui.load transaction",
        checked = configuration.enableActivityUiLoadTransaction,
        helpText = ACTIVITY_UI_LOAD_HELP_TEXT,
      ) {
        onConfigurationChanged(configuration.copy(enableActivityUiLoadTransaction = it))
      }
      NavigationSetupCheckboxRow(
        label = "User interaction transactions",
        checked = configuration.enableUserInteractionTransactions,
      ) {
        onConfigurationChanged(configuration.copy(enableUserInteractionTransactions = it))
      }
    }
    NavigationSetupSection(title = "Other breadcrumbs") {
      NavigationSetupCheckboxRow(
        label = "User interaction breadcrumbs",
        checked = configuration.enableUserInteractionBreadcrumbs,
      ) {
        onConfigurationChanged(configuration.copy(enableUserInteractionBreadcrumbs = it))
      }
    }
    Button(onClick = onLaunch, modifier = Modifier.fillMaxWidth()) {
      Text("Launch $navName Sample", fontSize = 18.sp)
    }
  }
}

@Composable
private fun NavigationSetupSection(title: String, content: @Composable () -> Unit) {
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
private fun NavigationSetupCheckboxRow(
  label: String,
  checked: Boolean,
  helpText: String? = null,
  onCheckedChange: (Boolean) -> Unit,
) {
  val sentryPink = Color(0xFFC85B9C)
  val rowShape: Shape = RoundedCornerShape(12.dp)
  val rowBackground by
    animateColorAsState(
      targetValue =
        if (checked) sentryPink.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
      animationSpec = spring(stiffness = 550f, dampingRatio = 0.9f),
      label = "navigation-setup-toggle-background",
    )
  val rowBorderColor by
    animateColorAsState(
      targetValue =
        if (checked) sentryPink.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
      animationSpec = tween(durationMillis = 180),
      label = "navigation-setup-toggle-border",
    )
  val switchColors =
    SwitchDefaults.colors(
      checkedTrackColor = sentryPink,
      checkedBorderColor = sentryPink,
      checkedThumbColor = Color.White,
    )
  Surface(
    shape = rowShape,
    color = rowBackground,
    border = BorderStroke(1.dp, rowBorderColor),
    tonalElevation = if (checked) 1.dp else 0.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
          .defaultMinSize(minHeight = 56.dp)
          .padding(horizontal = 14.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Switch(
        checked = checked,
        onCheckedChange = null,
        colors = switchColors,
      )
      Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        if (helpText != null) {
          NavigationSetupHelpTooltip(helpText)
        }
      }
    }
  }
}

@Composable
private fun NavigationSetupCounterRow(
  label: String,
  value: Int,
  enabled: Boolean,
  onValueChange: (Int) -> Unit,
) {
  val sentryPurple = Color(0xFF6C55B2)
  val containerColor by
    animateColorAsState(
      targetValue =
        if (enabled) MaterialTheme.colorScheme.surface
        else MaterialTheme.colorScheme.surfaceVariant,
      animationSpec = tween(durationMillis = 180),
      label = "navigation-setup-counter-background",
    )
  val borderColor by
    animateColorAsState(
      targetValue =
        if (enabled) sentryPurple.copy(alpha = 0.28f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
      animationSpec = tween(durationMillis = 180),
      label = "navigation-setup-counter-border",
    )
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = containerColor,
    border = BorderStroke(1.dp, borderColor),
    tonalElevation = if (enabled) 1.dp else 0.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .defaultMinSize(minHeight = 56.dp)
          .padding(horizontal = 14.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.weight(1f),
      )
      NavigationSetupCounterButton(
        label = "-",
        enabled = enabled && value > 1,
        onClick = { onValueChange((value - 1).coerceAtLeast(1)) },
        color = sentryPurple,
      )
      Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        AnimatedContent(
          targetState = value,
          transitionSpec = {
            (slideInVertically { fullHeight -> fullHeight / 3 } + fadeIn()) togetherWith
              (slideOutVertically { fullHeight -> -fullHeight / 3 } + fadeOut()) using
              SizeTransform(clip = false)
          },
          label = "navigation-setup-counter-value",
        ) { animatedValue ->
          Text(
            text = animatedValue.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
          )
        }
      }
      NavigationSetupCounterButton(
        label = "+",
        enabled = enabled,
        onClick = { onValueChange(value + 1) },
        color = sentryPurple,
      )
    }
  }
}

@Composable
private fun NavigationSetupCounterButton(
  label: String,
  enabled: Boolean,
  color: Color,
  onClick: () -> Unit,
) {
  Button(
    onClick = onClick,
    enabled = enabled,
    shape = CircleShape,
    contentPadding = ButtonDefaults.ContentPadding,
    colors =
      ButtonDefaults.buttonColors(
        containerColor = color,
        contentColor = Color.White,
        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
      ),
    elevation =
      ButtonDefaults.buttonElevation(
        defaultElevation = 0.dp,
        pressedElevation = 2.dp,
        focusedElevation = 1.dp,
        hoveredElevation = 1.dp,
        disabledElevation = 0.dp,
      ),
    modifier = Modifier.size(48.dp),
  ) {
    Text(label, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavigationSetupHelpTooltip(text: String) {
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

private const val ACTIVITY_UI_LOAD_HELP_TEXT =
  "The sample simulates disabling ui.load transactions, as actual activity lifecycle " +
    "tracing is fixed when the SDK initializes."

@Composable
internal fun NavigationSampleTheme(content: @Composable () -> Unit) {
  val context = LocalContext.current
  val primaryColor = Color(ContextCompat.getColor(context, R.color.colorPrimary))
  val accentColor = Color(ContextCompat.getColor(context, R.color.colorAccent))
  val darkBackground = Color(0xFF333333)
  val darkSurface = Color(0xFF333333)
  val darkSurfaceVariant = Color(0xFF454545)
  val darkSurfaceContainer = Color(0xFF66666A)
  val darkOnBackground = Color(0xFFF2ECF7)
  val darkOnSurface = Color(0xFFF2ECF7)
  val darkOnSurfaceVariant = Color(0xFFD0CAD6)
  val lightBackground = Color(0xFFF6F1F8)
  val lightSurface = Color(0xFFF6F1F8)
  val lightSurfaceVariant = Color(0xFFE4DEEA)
  val lightOnBackground = Color(0xFF241F29)
  val lightOnSurface = Color(0xFF241F29)
  val lightOnSurfaceVariant = Color(0xFF5F5868)
  val colorScheme =
    if (isSystemInDarkTheme()) {
      darkColorScheme(
        primary = primaryColor,
        secondary = accentColor,
        tertiary = primaryColor,
        background = darkBackground,
        surface = darkSurface,
        surfaceVariant = darkSurfaceVariant,
        surfaceContainer = darkSurfaceContainer,
        onBackground = darkOnBackground,
        onSurface = darkOnSurface,
        onSurfaceVariant = darkOnSurfaceVariant,
      )
    } else {
      lightColorScheme(
        primary = primaryColor,
        secondary = accentColor,
        tertiary = primaryColor,
        background = lightBackground,
        surface = lightSurface,
        surfaceVariant = lightSurfaceVariant,
        onBackground = lightOnBackground,
        onSurface = lightOnSurface,
        onSurfaceVariant = lightOnSurfaceVariant,
      )
    }

  MaterialTheme(colorScheme = colorScheme, content = content)
}
