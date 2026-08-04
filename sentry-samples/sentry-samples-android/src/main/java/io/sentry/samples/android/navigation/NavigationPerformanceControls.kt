package io.sentry.samples.android.navigation

import android.os.Build
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
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.delay

internal class NavigationPerformanceState(private val measureRenderLatency: Boolean = false) {
  var stackDepth by mutableIntStateOf(30)
  var recompositionTick by mutableIntStateOf(0)
  var displayRevision by mutableIntStateOf(0)
  var autoRecompose by mutableStateOf(false)
  var autoNavigate by mutableStateOf(false)
  var extractorMode by mutableStateOf(NavigationPerformanceExtractorMode.NORMAL)
  var argumentMode by mutableStateOf(NavigationPerformanceArgumentMode.FLAT)
  var integrationMode by mutableStateOf(NavigationPerformanceIntegrationMode.FULL_STACK)
    private set

  var benchmarkStatus by mutableStateOf("Ready")
    private set

  var benchmarkRunning by mutableStateOf(false)
    private set

  var comparisonResult by mutableStateOf<String?>(null)
    private set

  var generation = 0
    private set

  var recompositionRequests = 0
    private set

  var navigationMutations = 0
    private set

  var destinationChanges = 0
    private set

  var nameExtractorCalls = 0
    private set

  var argumentsExtractorCalls = 0
    private set

  var nameExtractorNanos = 0L
    private set

  var argumentsExtractorNanos = 0L
    private set

  var sentryNavEffectAttempts = 0
    private set

  var sentryNavEffectProcessedCalls = 0
    private set

  var capturedEntriesResolved = 0
    private set

  private val sentryNavEffectDurations = NavigationPerformanceDurations()
  private val extractorDurations = NavigationPerformanceDurations()
  private val mutationToCompositionDurations = NavigationPerformanceDurations()
  private val mutationToFirstDrawDurations = NavigationPerformanceDurations()
  private var pendingOperation: PendingNavigationPerformanceOperation? = null
  private var pendingAbPhase: PendingNavigationPerformanceTrace? = null
  private var nextTraceCookie = 0
  private var abComparisonCount = 0
  private var collectMeasurements = true
  private var discardNextSentryNavEffectMeasurement = false
  private var suppressNextDestinationChange = false

  fun resetCounters() {
    finishPendingOperation()
    recompositionRequests = 0
    navigationMutations = 0
    destinationChanges = 0
    nameExtractorCalls = 0
    argumentsExtractorCalls = 0
    nameExtractorNanos = 0L
    argumentsExtractorNanos = 0L
    sentryNavEffectAttempts = 0
    sentryNavEffectProcessedCalls = 0
    capturedEntriesResolved = 0
    sentryNavEffectDurations.clear()
    extractorDurations.clear()
    mutationToCompositionDurations.clear()
    mutationToFirstDrawDurations.clear()
    comparisonResult = null
    displayRevision++
  }

  fun nextGeneration(): Int {
    generation++
    return generation
  }

  fun markRecompositionRequest() {
    beginNavigationOperation(measureFirstDraw = false)
    recompositionRequests++
    recompositionTick++
    if (!benchmarkRunning) {
      displayRevision++
    }
  }

  fun markNavigationMutation() {
    navigationMutations++
    if (!benchmarkRunning) {
      displayRevision++
    }
  }

  fun markDestinationChange() {
    if (suppressNextDestinationChange) {
      suppressNextDestinationChange = false
      return
    }
    destinationChanges++
    if (!benchmarkRunning) {
      displayRevision++
    }
  }

  fun markBenchmarkDestinationChange() {
    destinationChanges++
  }

  fun suppressNextDestinationChange() {
    suppressNextDestinationChange = true
  }

  fun updateIntegrationMode(mode: NavigationPerformanceIntegrationMode) {
    if (integrationMode != mode) {
      discardNextSentryNavEffectMeasurement = true
      integrationMode = mode
    }
  }

  fun beginNavigationOperation(measureFirstDraw: Boolean = true) {
    if (!measureRenderLatency) {
      return
    }

    finishPendingOperation()
    val cookie = ++nextTraceCookie
    pendingOperation =
      PendingNavigationPerformanceOperation(
        cookie = cookie,
        startedAtNanos = System.nanoTime(),
        collectMeasurement = collectMeasurements,
        measureFirstDraw = measureFirstDraw,
      )
    beginAsyncTraceSection(NAVIGATION_TO_COMPOSITION_SECTION, cookie)
    if (measureFirstDraw) {
      beginAsyncTraceSection(NAVIGATION_TO_FIRST_DRAW_SECTION, cookie)
    }
  }

  fun recordComposition() {
    val operation = pendingOperation ?: return
    if (operation.compositionRecorded) {
      return
    }

    operation.compositionRecorded = true
    endAsyncTraceSection(NAVIGATION_TO_COMPOSITION_SECTION, operation.cookie)
    if (operation.collectMeasurement) {
      mutationToCompositionDurations.add(System.nanoTime() - operation.startedAtNanos)
    }
    if (!operation.measureFirstDraw) {
      pendingOperation = null
    }
  }

  fun recordFirstDraw() {
    val operation = pendingOperation ?: return
    if (!operation.measureFirstDraw) {
      return
    }
    endAsyncTraceSection(NAVIGATION_TO_FIRST_DRAW_SECTION, operation.cookie)
    if (!operation.compositionRecorded) {
      endAsyncTraceSection(NAVIGATION_TO_COMPOSITION_SECTION, operation.cookie)
    }
    if (operation.collectMeasurement) {
      mutationToFirstDrawDurations.add(System.nanoTime() - operation.startedAtNanos)
    }
    pendingOperation = null
  }

  fun recordSentryNavEffect(
    durationNanos: Long,
    nameExtractorCallsBefore: Int,
    argumentsExtractorCallsBefore: Int,
    extractorNanosBefore: Long,
  ) {
    if (discardNextSentryNavEffectMeasurement) {
      discardNextSentryNavEffectMeasurement = false
      return
    }
    if (!collectMeasurements) {
      return
    }

    val nameExtractorCallCount = nameExtractorCalls - nameExtractorCallsBefore
    val argumentsExtractorCallCount = argumentsExtractorCalls - argumentsExtractorCallsBefore
    sentryNavEffectAttempts++
    if (nameExtractorCallCount + argumentsExtractorCallCount > 0) {
      sentryNavEffectProcessedCalls++
    }
    capturedEntriesResolved += nameExtractorCallCount
    sentryNavEffectDurations.add(durationNanos)
    extractorDurations.add(nameExtractorNanos + argumentsExtractorNanos - extractorNanosBefore)
  }

  fun startBenchmark(status: String) {
    benchmarkRunning = true
    benchmarkStatus = status
    comparisonResult = null
    collectMeasurements = false
  }

  fun updateBenchmarkStatus(status: String) {
    benchmarkStatus = status
  }

  fun startMeasuredIterations() {
    resetCounters()
    collectMeasurements = true
  }

  fun stopCollectingMeasurements() {
    collectMeasurements = false
  }

  fun finishWarmUp() {
    resetCounters()
    benchmarkRunning = true
    benchmarkStatus = "Warm-up complete"
  }

  fun finishBenchmark(result: String? = null) {
    finishAbPhase()
    collectMeasurements = true
    benchmarkRunning = false
    benchmarkStatus = "Complete"
    comparisonResult = result
    suppressNextDestinationChange = true
    displayRevision++
  }

  fun cancelBenchmark(status: String = "Ready") {
    finishAbPhase()
    collectMeasurements = true
    benchmarkRunning = false
    benchmarkStatus = status
    finishPendingOperation()
  }

  fun benchmarkSummary(label: String): String =
    "$label: effect ${sentryNavEffectDurations.compactSummary()}, " +
      "first draw ${mutationToFirstDrawDurations.compactSummary()}"

  fun nextAbComparisonRunsDisabledFirst(): Boolean = abComparisonCount++ % 2 == 0

  fun beginAbPhase(mode: NavigationPerformanceIntegrationMode) {
    finishAbPhase()
    val sectionName =
      when (mode) {
        NavigationPerformanceIntegrationMode.DISABLED -> AB_DISABLED_SECTION
        NavigationPerformanceIntegrationMode.FULL_STACK -> AB_ENABLED_SECTION
        else -> error("Unsupported A/B integration mode: $mode")
      }
    val cookie = ++nextTraceCookie
    pendingAbPhase = PendingNavigationPerformanceTrace(sectionName, cookie)
    beginAsyncTraceSection(sectionName, cookie)
  }

  fun finishAbPhase() {
    pendingAbPhase?.let { phase -> endAsyncTraceSection(phase.sectionName, phase.cookie) }
    pendingAbPhase = null
  }

  fun sentryNavEffectTraceSection(): String =
    if (collectMeasurements) SENTRY_NAV_EFFECT_SECTION else SENTRY_NAV_EFFECT_WARM_UP_SECTION

  fun sentryNavEffectDurationSummary(): String = sentryNavEffectDurations.summary()

  fun extractorDurationSummary(): String = extractorDurations.summary()

  fun nonExtractorDurationSummary(): String =
    sentryNavEffectDurations.minus(extractorDurations).summary()

  fun mutationToCompositionSummary(): String = mutationToCompositionDurations.summary()

  fun mutationToFirstDrawSummary(): String = mutationToFirstDrawDurations.summary()

  fun firstDrawsOver8Millis(): Int = mutationToFirstDrawDurations.countOver(8_300_000L)

  fun firstDrawsOver16Millis(): Int = mutationToFirstDrawDurations.countOver(16_700_000L)

  private fun finishPendingOperation() {
    val operation = pendingOperation ?: return
    if (!operation.compositionRecorded) {
      endAsyncTraceSection(NAVIGATION_TO_COMPOSITION_SECTION, operation.cookie)
    }
    if (operation.measureFirstDraw) {
      endAsyncTraceSection(NAVIGATION_TO_FIRST_DRAW_SECTION, operation.cookie)
    }
    pendingOperation = null
  }

  fun recordNameExtractor(sectionName: String, block: () -> String): String {
    val startedAt = System.nanoTime()
    Trace.beginSection(sectionName)
    try {
      return block()
    } finally {
      Trace.endSection()
      if (collectMeasurements && !discardNextSentryNavEffectMeasurement) {
        nameExtractorCalls++
        nameExtractorNanos += System.nanoTime() - startedAt
      }
    }
  }

  fun recordArgumentsExtractor(
    sectionName: String,
    block: () -> Map<String, Any?>,
  ): Map<String, Any?> {
    val startedAt = System.nanoTime()
    Trace.beginSection(sectionName)
    try {
      return block()
    } finally {
      Trace.endSection()
      if (collectMeasurements && !discardNextSentryNavEffectMeasurement) {
        argumentsExtractorCalls++
        argumentsExtractorNanos += System.nanoTime() - startedAt
      }
    }
  }
}

private data class PendingNavigationPerformanceOperation(
  val cookie: Int,
  val startedAtNanos: Long,
  val collectMeasurement: Boolean,
  val measureFirstDraw: Boolean,
  var compositionRecorded: Boolean = false,
)

private data class PendingNavigationPerformanceTrace(val sectionName: String, val cookie: Int)

internal class NavigationPerformanceDurations {
  private val values = mutableListOf<Long>()

  val size: Int
    get() = values.size

  fun add(durationNanos: Long) {
    values += durationNanos.coerceAtLeast(0L)
  }

  fun clear() {
    values.clear()
  }

  fun countOver(thresholdNanos: Long): Int = values.count { it > thresholdNanos }

  fun percentile(percentile: Int): Long {
    require(percentile in 0..100)
    if (values.isEmpty()) {
      return 0L
    }
    val sortedValues = values.sorted()
    val index = (ceil(percentile / 100.0 * sortedValues.size).toInt() - 1).coerceAtLeast(0)
    return sortedValues[index]
  }

  fun summary(): String =
    if (values.isEmpty()) {
      "No samples"
    } else {
      "n=$size, p50=${formatNanos(percentile(50))}, p90=${formatNanos(percentile(90))}, " +
        "max=${formatNanos(values.max())}"
    }

  fun compactSummary(): String =
    if (values.isEmpty()) "disabled" else "p50=${formatNanos(percentile(50))}"

  fun minus(other: NavigationPerformanceDurations): NavigationPerformanceDurations =
    NavigationPerformanceDurations().also { result ->
      values.forEachIndexed { index, value ->
        result.add(value - other.values.getOrElse(index) { 0L })
      }
    }
}

internal enum class NavigationPerformanceExtractorMode(val label: String) {
  NORMAL("Normal"),
  HEAVY("Heavy"),
}

internal enum class NavigationPerformanceArgumentMode(val label: String) {
  EMPTY("Empty"),
  FLAT("Flat"),
  NESTED("Nested"),
  LARGE("Large"),
}

internal enum class NavigationPerformanceIntegrationMode(
  val label: String,
  val captureBackStack: Boolean,
  val includeArguments: Boolean,
) {
  DISABLED("Disabled", captureBackStack = false, includeArguments = false),
  TOP_ONLY("Top only", captureBackStack = false, includeArguments = true),
  FULL_STACK("Full stack", captureBackStack = true, includeArguments = true),
  FULL_STACK_WITHOUT_ARGUMENTS(
    "No arguments",
    captureBackStack = true,
    includeArguments = false,
  ),
}

internal enum class NavigationPerformanceRun(val label: String) {
  UNRELATED_RECOMPOSITIONS("Run 20 recompositions"),
  TOP_REPLACEMENTS("Run 20 top replacements"),
  LOWER_ENTRY_MUTATIONS("Run 20 lower mutations"),
  AB_COMPARISON("Run A/B comparison"),
}

internal enum class NavigationPerformancePreset(
  val label: String,
  val stackDepth: Int,
  val maxCapturedBackStackEntries: Int,
  val integrationMode: NavigationPerformanceIntegrationMode,
  val extractorMode: NavigationPerformanceExtractorMode,
  val argumentMode: NavigationPerformanceArgumentMode,
) {
  LIGHT(
    "Light",
    1,
    1,
    NavigationPerformanceIntegrationMode.FULL_STACK,
    NavigationPerformanceExtractorMode.NORMAL,
    NavigationPerformanceArgumentMode.EMPTY,
  ),
  NORMAL(
    "Normal",
    20,
    20,
    NavigationPerformanceIntegrationMode.FULL_STACK,
    NavigationPerformanceExtractorMode.NORMAL,
    NavigationPerformanceArgumentMode.FLAT,
  ),
  CAPTURE_1_OF_100(
    "Capture 1 of 100",
    100,
    1,
    NavigationPerformanceIntegrationMode.FULL_STACK,
    NavigationPerformanceExtractorMode.NORMAL,
    NavigationPerformanceArgumentMode.FLAT,
  ),
  CAPTURE_20_OF_100(
    "Capture 20 of 100",
    100,
    20,
    NavigationPerformanceIntegrationMode.FULL_STACK,
    NavigationPerformanceExtractorMode.NORMAL,
    NavigationPerformanceArgumentMode.FLAT,
  ),
  CAPTURE_100_OF_100(
    "Capture 100 of 100",
    100,
    100,
    NavigationPerformanceIntegrationMode.FULL_STACK,
    NavigationPerformanceExtractorMode.NORMAL,
    NavigationPerformanceArgumentMode.FLAT,
  ),
  HEAVY(
    "Heavy",
    50,
    50,
    NavigationPerformanceIntegrationMode.FULL_STACK,
    NavigationPerformanceExtractorMode.NORMAL,
    NavigationPerformanceArgumentMode.NESTED,
  ),
  SUPER_HEAVY(
    "Super heavy",
    100,
    100,
    NavigationPerformanceIntegrationMode.FULL_STACK,
    NavigationPerformanceExtractorMode.HEAVY,
    NavigationPerformanceArgumentMode.LARGE,
  ),
  NO_ARGUMENTS(
    "No arguments",
    100,
    100,
    NavigationPerformanceIntegrationMode.FULL_STACK_WITHOUT_ARGUMENTS,
    NavigationPerformanceExtractorMode.HEAVY,
    NavigationPerformanceArgumentMode.LARGE,
  ),
  TOP_ONLY(
    "Top only",
    30,
    30,
    NavigationPerformanceIntegrationMode.TOP_ONLY,
    NavigationPerformanceExtractorMode.NORMAL,
    NavigationPerformanceArgumentMode.FLAT,
  ),
  DISABLED_CONTROL(
    "Disabled control",
    30,
    30,
    NavigationPerformanceIntegrationMode.DISABLED,
    NavigationPerformanceExtractorMode.NORMAL,
    NavigationPerformanceArgumentMode.EMPTY,
  ),
}

internal data class Nav3PerformanceControls(
  val actualStackEntries: Int,
  val maxCapturedBackStackEntries: Int,
  val onMaxCapturedBackStackEntriesChange: (Int) -> Unit,
  val onApplyPreset: (NavigationPerformancePreset) -> Unit,
  val onRunBenchmark: (NavigationPerformanceRun) -> Unit,
)

@Composable
internal fun NavigationPerformancePanel(
  title: String,
  description: String,
  currentRoute: String,
  backStack: String,
  state: NavigationPerformanceState,
  showExtractorControls: Boolean,
  onBuildStack: () -> Unit,
  onMutateLowerEntry: (() -> Unit)? = null,
  onReplaceTop: () -> Unit,
  nav3Controls: Nav3PerformanceControls? = null,
) {
  @Suppress("UNUSED_EXPRESSION") state.displayRevision

  LaunchedEffect(state.autoRecompose) {
    while (state.autoRecompose) {
      delay(250)
      traceNavigationPerformanceSection("NavigationPerf.autoRecompose") {
        state.markRecompositionRequest()
      }
    }
  }

  LaunchedEffect(state.autoNavigate) {
    while (state.autoNavigate) {
      delay(500)
      traceNavigationPerformanceSection("NavigationPerf.autoNavigate") { onReplaceTop() }
    }
  }

  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text(description, style = MaterialTheme.typography.bodyMedium)
    if (nav3Controls != null) {
      PerfInfoRow("Benchmark status", state.benchmarkStatus)
    }

    PerfCard(title = "Current State") {
      PerfInfoRow("Current route", currentRoute)
      PerfInfoRow("Tracked stack", backStack)
      nav3Controls?.let { controls ->
        PerfInfoRow("Actual stack entries", controls.actualStackEntries.toString())
        PerfInfoRow("Requested stack depth", state.stackDepth.toString())
        PerfInfoRow("Integration mode", state.integrationMode.label)
        PerfInfoRow(
          "Capture enabled",
          if (state.integrationMode.captureBackStack) "Yes" else "No",
        )
        PerfInfoRow("Capture limit", controls.maxCapturedBackStackEntries.toString())
        PerfInfoRow(
          "Effective captured entries",
          if (state.integrationMode.captureBackStack) {
            minOf(controls.actualStackEntries, controls.maxCapturedBackStackEntries).toString()
          } else {
            "0"
          },
        )
      }
    }

    nav3Controls?.let { controls ->
      PerfCard(title = "Scenarios") {
        NavigationPerformancePreset.entries.chunked(2).forEach { presets ->
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { preset ->
              OutlinedButton(
                enabled = !state.benchmarkRunning,
                onClick = { controls.onApplyPreset(preset) },
                modifier = Modifier.weight(1f),
              ) {
                Text(preset.label)
              }
            }
            if (presets.size == 1) {
              Spacer(Modifier.weight(1f))
            }
          }
        }
      }

      PerfCard(title = "Nav3 Integration") {
        PerfModeRow(
          label = "Mode",
          selectedLabel = state.integrationMode.label,
          enabled = !state.benchmarkRunning,
          options =
            NavigationPerformanceIntegrationMode.entries.map { mode ->
              mode.label to { state.updateIntegrationMode(mode) }
            },
        )
        PerfStepper(
          label = "Capture limit",
          value = controls.maxCapturedBackStackEntries,
          enabled = !state.benchmarkRunning,
          onDecrement = {
            controls.onMaxCapturedBackStackEntriesChange(
              (controls.maxCapturedBackStackEntries - 1).coerceAtLeast(1)
            )
          },
          onIncrement = {
            controls.onMaxCapturedBackStackEntriesChange(
              (controls.maxCapturedBackStackEntries + 1).coerceAtMost(100)
            )
          },
        )
      }
    }

    PerfCard(title = "Stress Controls") {
      PerfStepper(
        label = "Stack depth",
        value = state.stackDepth,
        enabled = !state.benchmarkRunning,
        onDecrement = { state.stackDepth = (state.stackDepth - 1).coerceAtLeast(1) },
        onIncrement = { state.stackDepth = (state.stackDepth + 1).coerceAtMost(100) },
      )

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
          enabled = !state.benchmarkRunning,
          onClick = onBuildStack,
          modifier = Modifier.weight(1f),
        ) {
          Text("Build Stack")
        }
        Button(
          enabled = !state.benchmarkRunning,
          onClick = onReplaceTop,
          modifier = Modifier.weight(1f),
        ) {
          Text("Replace Top")
        }
      }

      onMutateLowerEntry?.let { mutateLowerEntry ->
        Button(
          enabled = !state.benchmarkRunning,
          onClick = mutateLowerEntry,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Mutate Lower Entry")
        }
      }

      Button(
        enabled = !state.benchmarkRunning,
        onClick = { state.markRecompositionRequest() },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("Force Unrelated Recomposition")
      }

      if (nav3Controls == null) {
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
    }

    nav3Controls?.let { controls ->
      PerfCard(title = "Fixed Runs") {
        NavigationPerformanceRun.entries.forEach { run ->
          Button(
            enabled = !state.benchmarkRunning,
            onClick = { controls.onRunBenchmark(run) },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(run.label)
          }
        }
        state.comparisonResult?.let { result -> PerfInfoRow("A/B result", result) }
      }
    }

    if (showExtractorControls) {
      PerfCard(title = "Extractor Inputs") {
        PerfModeRow(
          label = "Extractor cost",
          selectedLabel = state.extractorMode.label,
          enabled = !state.benchmarkRunning,
          options =
            NavigationPerformanceExtractorMode.entries.map { mode ->
              mode.label to { state.extractorMode = mode }
            },
        )
        PerfModeRow(
          label = "Argument shape",
          selectedLabel = state.argumentMode.label,
          enabled = !state.benchmarkRunning,
          options =
            NavigationPerformanceArgumentMode.entries.map { mode ->
              mode.label to { state.argumentMode = mode }
            },
        )
      }
    }

    PerfCard(title = "Counters") {
      PerfInfoRow("Recomposition requests", state.recompositionRequests.toString())
      PerfInfoRow("Navigation mutations", state.navigationMutations.toString())
      PerfInfoRow("Destination changes", state.destinationChanges.toString())
      if (showExtractorControls) {
        if (state.benchmarkRunning) {
          Text("Metrics are published when the fixed run completes.")
        } else {
          PerfInfoRow("SentryNavEffect attempts", state.sentryNavEffectAttempts.toString())
          PerfInfoRow("Calls with extractor work", state.sentryNavEffectProcessedCalls.toString())
          PerfInfoRow("Captured entries resolved", state.capturedEntriesResolved.toString())
          PerfInfoRow("nameExtractor calls", state.nameExtractorCalls.toString())
          PerfInfoRow("argumentsExtractor calls", state.argumentsExtractorCalls.toString())
          PerfInfoRow("nameExtractor avg", state.nameExtractorAverageMicros())
          PerfInfoRow("argumentsExtractor avg", state.argumentsExtractorAverageMicros())
          PerfInfoRow("SentryNavEffect duration", state.sentryNavEffectDurationSummary())
          PerfInfoRow("Extractor duration", state.extractorDurationSummary())
          PerfInfoRow("Non-extractor estimate", state.nonExtractorDurationSummary())
          PerfInfoRow("Mutation to composition", state.mutationToCompositionSummary())
          PerfInfoRow("Mutation to first draw", state.mutationToFirstDrawSummary())
          PerfInfoRow(
            "First draws over 8.3 ms",
            state.firstDrawsOver8Millis().toString(),
          )
          PerfInfoRow(
            "First draws over 16.7 ms",
            state.firstDrawsOver16Millis().toString(),
          )
        }
      }
      Button(
        enabled = !state.benchmarkRunning,
        onClick = { state.resetCounters() },
        modifier = Modifier.fillMaxWidth(),
      ) {
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
  enabled: Boolean = true,
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
        enabled = enabled,
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
        enabled = enabled,
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
  enabled: Boolean = true,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (selected) {
    Button(enabled = enabled, onClick = onClick, modifier = modifier) { Text(label) }
  } else {
    OutlinedButton(enabled = enabled, onClick = onClick, modifier = modifier) { Text(label) }
  }
}

@Composable
private fun PerfModeRow(
  label: String,
  selectedLabel: String,
  enabled: Boolean = true,
  options: List<Pair<String, () -> Unit>>,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text("$label: $selectedLabel", fontWeight = FontWeight.Bold)
    options.chunked(2).forEach { optionRow ->
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        optionRow.forEach { (optionLabel, onClick) ->
          PerfToggleButton(
            selected = optionLabel == selectedLabel,
            label = optionLabel,
            enabled = enabled,
            onClick = onClick,
            modifier = Modifier.weight(1f),
          )
        }
        if (optionRow.size == 1) {
          Spacer(Modifier.weight(1f))
        }
      }
    }
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

internal fun navigationPerformanceArguments(
  mode: NavigationPerformanceArgumentMode,
  index: Int,
  generation: Int,
): Map<String, Any?> =
  when (mode) {
    NavigationPerformanceArgumentMode.EMPTY -> emptyMap()
    NavigationPerformanceArgumentMode.FLAT ->
      mapOf("index" to index, "generation" to generation, "label" to "route-$index")
    NavigationPerformanceArgumentMode.NESTED ->
      mapOf(
        "route" to
          mapOf(
            "index" to index,
            "generation" to generation,
            "source" to "performance",
          ),
        "tags" to listOf("nav", "stress", "route-$index"),
      )
    NavigationPerformanceArgumentMode.LARGE ->
      (0 until 20).associate { valueIndex ->
        "key_$valueIndex" to
          mapOf(
            "index" to index,
            "generation" to generation,
            "value" to "payload-$index-$generation-$valueIndex",
            "tags" to listOf("a", "b", "c", valueIndex.toString()),
          )
      }
  }

@Volatile private var navigationPerformanceExtractorBlackhole = 0

internal fun consumeNavigationPerformanceExtractorWork(
  mode: NavigationPerformanceExtractorMode,
  seed: Int,
) {
  if (mode == NavigationPerformanceExtractorMode.NORMAL) {
    return
  }

  var checksum = seed
  repeat(2_000) { index -> checksum = (checksum * 31) xor index }
  navigationPerformanceExtractorBlackhole = checksum
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

private fun NavigationPerformanceState.nameExtractorAverageMicros(): String =
  averageMicros(nameExtractorNanos, nameExtractorCalls)

private fun NavigationPerformanceState.argumentsExtractorAverageMicros(): String =
  averageMicros(argumentsExtractorNanos, argumentsExtractorCalls)

private fun averageMicros(totalNanos: Long, count: Int): String =
  if (count == 0) "0 us" else "${totalNanos / count / 1_000} us"

private fun formatNanos(durationNanos: Long): String =
  if (durationNanos < 1_000_000L) {
    "${durationNanos / 1_000} us"
  } else {
    String.format(Locale.US, "%.2f ms", durationNanos / 1_000_000.0)
  }

private fun beginAsyncTraceSection(sectionName: String, cookie: Int) {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    Trace.beginAsyncSection(sectionName, cookie)
  }
}

private fun endAsyncTraceSection(sectionName: String, cookie: Int) {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    Trace.endAsyncSection(sectionName, cookie)
  }
}

private const val NAVIGATION_TO_COMPOSITION_SECTION = "Nav3Stress.navigationToComposition"
private const val NAVIGATION_TO_FIRST_DRAW_SECTION = "Nav3Stress.navigationToFirstDraw"
private const val SENTRY_NAV_EFFECT_SECTION = "Nav3Stress.SentryNavEffect"
private const val SENTRY_NAV_EFFECT_WARM_UP_SECTION = "Nav3Stress.SentryNavEffect.warmup"
private const val AB_DISABLED_SECTION = "Nav3Stress.ab.disabled"
private const val AB_ENABLED_SECTION = "Nav3Stress.ab.enabled"
