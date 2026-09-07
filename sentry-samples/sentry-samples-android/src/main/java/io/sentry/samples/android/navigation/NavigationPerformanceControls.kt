package io.sentry.samples.android.navigation

import android.os.Trace
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** State holder backing the [Nav2Scenario.PERFORMANCE] tab. */
internal class NavigationPerformanceState {

  var stackDepth by mutableIntStateOf(30)
  var autoRecompose by mutableStateOf(false)
  var autoNavigate by mutableStateOf(false)

  var generation = 0
    private set

  var recompositionRequests by mutableIntStateOf(0)
    private set

  var navigationMutations by mutableIntStateOf(0)
    private set

  var destinationChanges by mutableIntStateOf(0)
    private set

  fun resetCounters() {
    recompositionRequests = 0
    navigationMutations = 0
    destinationChanges = 0
  }

  fun stopAutomaticWork() {
    autoRecompose = false
    autoNavigate = false
  }

  fun nextGeneration(): Int {
    generation++
    return generation
  }

  fun markRecompositionRequest() {
    recompositionRequests++
  }

  fun markNavigationMutation() {
    navigationMutations++
  }

  fun markDestinationChange() {
    destinationChanges++
  }
}

/** "Performance" tab content. */
@Composable
internal fun NavigationPerformancePanel(
  title: String,
  description: String,
  currentRoute: String,
  backStack: String,
  state: NavigationPerformanceState,
  onBuildStack: () -> Unit,
  onReplaceTop: () -> Unit,
) {
  LaunchedEffect(state.autoRecompose) {
    while (state.autoRecompose) {
      delay(250)
      if (!state.autoRecompose) {
        break
      }
      traceNavigationPerformanceSection("Nav2Stress.autoRecompose") {
        state.markRecompositionRequest()
      }
    }
  }

  LaunchedEffect(state.autoNavigate) {
    while (state.autoNavigate) {
      delay(500)
      if (!state.autoNavigate) {
        break
      }
      traceNavigationPerformanceSection("Nav2Stress.autoNavigate") { onReplaceTop() }
    }
  }

  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text(description, style = MaterialTheme.typography.bodyMedium)

    PerfCard(title = "Current State") {
      PerfInfoRow("Current route", currentRoute)
      PerfInfoRow("Tracked stack", backStack)
    }

    PerfCard(title = "Stress Controls") {
      PerfStepper(
        label = "Stack depth",
        value = state.stackDepth,
        onDecrement = { state.stackDepth = (state.stackDepth - 1).coerceAtLeast(1) },
        onIncrement = { state.stackDepth = (state.stackDepth + 1).coerceAtMost(100) },
      )

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onBuildStack, modifier = Modifier.weight(1f)) { Text("Build Stack") }
        Button(onClick = onReplaceTop, modifier = Modifier.weight(1f)) { Text("Replace Top") }
      }

      Button(
        onClick = { state.markRecompositionRequest() },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("Force Unrelated Recomposition")
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PerfToggleButton(
          selected = state.autoRecompose,
          label = if (state.autoRecompose) "Stop Recompose" else "Auto Recompose",
          onClick = { state.autoRecompose = !state.autoRecompose },
          modifier = Modifier.weight(1f),
        )
        PerfToggleButton(
          selected = state.autoNavigate,
          label = if (state.autoNavigate) "Stop Navigate" else "Auto Navigate",
          onClick = { state.autoNavigate = !state.autoNavigate },
          modifier = Modifier.weight(1f),
        )
      }
    }

    PerfCard(title = "Counters") {
      PerfInfoRow("Recomposition requests", state.recompositionRequests.toString())
      PerfInfoRow("Navigation mutations", state.navigationMutations.toString())
      PerfInfoRow("Destination changes", state.destinationChanges.toString())
      Button(onClick = { state.resetCounters() }, modifier = Modifier.fillMaxWidth()) {
        Text("Reset Counters")
      }
    }
  }
}

@Composable
private fun PerfCard(title: String, content: @Composable ColumnScope.() -> Unit) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      content()
    }
  }
}

@Composable
private fun PerfInfoRow(label: String, value: String) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
        .padding(12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    Spacer(Modifier.size(12.dp))
    Text(value, modifier = Modifier.weight(1f))
  }
}

@Composable
private fun PerfStepper(
  label: String,
  value: Int,
  onDecrement: () -> Unit,
  onIncrement: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, fontWeight = FontWeight.Bold)
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedButton(
        modifier = Modifier.size(44.dp),
        contentPadding = PaddingValues(0.dp),
        onClick = onDecrement,
      ) {
        Text("-", style = MaterialTheme.typography.titleLarge)
      }
      Text(
        value.toString(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      OutlinedButton(
        modifier = Modifier.size(44.dp),
        contentPadding = PaddingValues(0.dp),
        onClick = onIncrement,
      ) {
        Text("+", style = MaterialTheme.typography.titleLarge)
      }
    }
  }
}

@Composable
private fun PerfToggleButton(
  selected: Boolean,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (selected) {
    Button(onClick = onClick, modifier = modifier) { Text(label) }
  } else {
    OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
  }
}

internal fun traceNavigationPerformanceSection(sectionName: String, block: () -> Unit) {
  Trace.beginSection(sectionName)
  try {
    block()
  } finally {
    Trace.endSection()
  }
}

internal fun navigationPerformanceBackStackPreview(entries: List<String>): String {
  if (entries.size <= 8) {
    return entries.joinToString(" -> ")
  }

  return "${entries.size} entries: " +
    entries.take(3).joinToString(" -> ") +
    " -> ... -> " +
    entries.takeLast(3).joinToString(" -> ")
}
