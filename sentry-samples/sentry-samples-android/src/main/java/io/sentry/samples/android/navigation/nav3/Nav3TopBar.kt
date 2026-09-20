package io.sentry.samples.android.navigation.nav3

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.sentry.compose.SentryModifier.sentryTag

@Composable
internal fun Nav3TopBar(
  backStack: List<Nav3Route>,
  selectedScenario: Nav3Scenario,
  maxCapturedBackStackEntries: Int,
  onTransactionHistoryClick: () -> Unit,
  onRouteWorkSettingsClick: () -> Unit,
  onScenarioSelected: (Nav3Scenario) -> Unit,
) {
  val currentRoute = backStack.lastOrNull() ?: Nav3Route.SingleStack
  val currentRouteText = currentRoute.displayRoute()
  val capturedBackStackEntries =
    backStack.takeLast(maxCapturedBackStackEntries).map { route -> "/${route.previewName}" }
  val capturedBackStack =
    capturedBackStackEntries
      .mapIndexed { index, route ->
        if (index == 0 && backStack.size > maxCapturedBackStackEntries) {
          "... $route"
        } else {
          route
        }
      }
      .joinToString(" -> ")
  val selectedTabColor = MaterialTheme.colorScheme.primary

  Surface(
    color = MaterialTheme.colorScheme.background,
    shadowElevation = 4.dp,
    modifier = Modifier.testTag(nav3TestTag("top_bar")),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 18.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          "Navigation 3",
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.weight(1f).testTag(nav3TestTag("title")),
        )
        IconButton(
          onClick = onTransactionHistoryClick,
          modifier =
            Modifier.sentryTag(nav3InteractionTag("Recent Transactions"))
              .testTag(nav3TestTag("top_bar_recent_transactions")),
        ) {
          Icon(
            imageVector = Icons.Filled.AccountTree,
            contentDescription = "Recent transactions",
          )
        }
        IconButton(
          onClick = onRouteWorkSettingsClick,
          modifier =
            Modifier.sentryTag(nav3InteractionTag("Route Work Settings"))
              .testTag(nav3TestTag("top_bar_route_work_settings")),
        ) {
          Icon(imageVector = Icons.Filled.Settings, contentDescription = "Route work settings")
        }
      }
      Column(
        modifier =
          Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
            .testTag(nav3TestTag("route_summary")),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          text = "Current route: $currentRouteText",
          style = MaterialTheme.typography.bodySmall,
          modifier =
            Modifier.horizontalScroll(rememberScrollState())
              .testTag(nav3TestTag("current_route")),
          maxLines = 1,
        )
        Text(
          text = "Nav3 back stack: $capturedBackStack",
          style = MaterialTheme.typography.bodySmall,
          modifier =
            Modifier.horizontalScroll(rememberScrollState())
              .testTag(nav3TestTag("back_stack")),
          maxLines = 1,
        )
      }
      ScenarioBar(
        selectedScenario = selectedScenario,
        selectedTabColor = selectedTabColor,
        onScenarioSelected = onScenarioSelected,
      )
    }
  }
}

@Composable
private fun ScenarioBar(
  selectedScenario: Nav3Scenario,
  selectedTabColor: Color,
  onScenarioSelected: (Nav3Scenario) -> Unit,
) {
  val scenarios = Nav3Scenario.entries.filter { scenario -> scenario.showTab }

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(start = 24.dp, end = 24.dp)
        .testTag(nav3TestTag("scenario_bar"))
  ) {
    scenarios.forEach { scenario ->
      val selected = selectedScenario == scenario
      Column(
        modifier =
          Modifier.defaultMinSize(minWidth = 120.dp)
            .clickable { onScenarioSelected(scenario) }
            .testTag(nav3ScenarioTabTag(scenario)),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = scenario.label,
          modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
          color = if (selected) selectedTabColor else MaterialTheme.colorScheme.onBackground,
          fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .height(4.dp)
              .background(
                if (selected) selectedTabColor else Color.Transparent,
                RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
              )
        )
      }
    }
  }
}

internal fun nav3InteractionTag(label: String): String = "Nav3 $label"

internal fun nav3TestTag(name: String): String = "nav3_$name"

internal fun nav3ScenarioTabTag(scenario: Nav3Scenario): String =
  nav3TestTag("tab_${scenario.name.lowercase()}")
