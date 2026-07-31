package io.sentry.samples.android.navigation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import io.sentry.compose.navigation3.SentryNav3NavigationEffect

class NavigationActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) { NavigationSampleShell() }
      }
    }
  }
}

@Composable
private fun NavigationSampleShell() {
  val singleStack = rememberSingleBackStack()

  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text("Navigation 3 Sample", style = MaterialTheme.typography.headlineSmall)
    Text(
      text = "Use this activity to exercise Sentry Navigation 3 spans.",
      style = MaterialTheme.typography.bodyMedium,
    )
    SingleStackSample(backStack = singleStack)
  }
}

@Composable
private fun rememberSingleBackStack(): SnapshotStateList<SampleRoute> =
  remember { mutableStateListOf(SampleRoute.Home) }

@Composable
private fun SingleStackSample(backStack: SnapshotStateList<SampleRoute>) {
  SentryNav3NavigationEffect(
    backStack = backStack,
    nameExtractor = ::routeName,
    argumentsExtractor = ::routeArguments,
  )

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    tonalElevation = 2.dp,
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("Single-stack flow", style = MaterialTheme.typography.titleMedium)
      Text(
        "Push and pop demo routes. Detail routes attach only safe sample arguments.",
        style = MaterialTheme.typography.bodyMedium,
      )
      BackStackSummary(backStack)
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { backStack.add(SampleRoute.Detail("demo-${backStack.size}")) }) {
          Text("Push detail")
        }
        OutlinedButton(
          enabled = backStack.size > 1,
          onClick = { backStack.removeAt(backStack.lastIndex) },
        ) {
          Text("Pop")
        }
      }
      Spacer(modifier = Modifier.height(8.dp))
      NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
        entryProvider = entryProvider {
          entry<SampleRoute.Home> {
            RouteContent(title = "Home", description = "Root route for the single-stack flow.")
          }
          entry<SampleRoute.Detail> { route ->
            RouteContent(
              title = "Detail ${route.itemId}",
              description = "Sends safe sample argument item_id=${route.itemId}.",
            )
          }
        },
      )
    }
  }
}

@Composable
private fun BackStackSummary(backStack: List<SampleRoute>) {
  Text("Current route: ${routeName(backStack.last())}", style = MaterialTheme.typography.bodyMedium)
  Text(
    "Backstack: ${backStack.joinToString(" > ") { routeName(it) }}",
    style = MaterialTheme.typography.bodySmall,
  )
}

@Composable
private fun RouteContent(title: String, description: String) {
  Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      horizontalAlignment = Alignment.Start,
    ) {
      Text(title, style = MaterialTheme.typography.titleSmall)
      Text(description, style = MaterialTheme.typography.bodyMedium)
    }
  }
}

private sealed interface SampleRoute {
  data object Home : SampleRoute

  data class Detail(val itemId: String) : SampleRoute
}

private fun routeName(route: SampleRoute): String =
  when (route) {
    SampleRoute.Home -> "/nav3/home"
    is SampleRoute.Detail -> "/nav3/detail"
  }

private fun routeArguments(route: SampleRoute): Map<String, Any?> =
  when (route) {
    SampleRoute.Home -> emptyMap()
    is SampleRoute.Detail -> mapOf("item_id" to route.itemId)
  }
