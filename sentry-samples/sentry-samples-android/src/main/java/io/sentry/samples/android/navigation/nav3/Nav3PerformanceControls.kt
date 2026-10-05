package io.sentry.samples.android.navigation.nav3

import android.os.Build
import android.os.Trace
import android.util.Log
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.delay

internal class Nav3PerformanceState(
  private val measureRenderLatency: Boolean = false,
  private val diagnosticsSurfaceName: String? = null,
) {
  var stackDepth by mutableIntStateOf(30)
  var recompositionTick by mutableIntStateOf(0)
  var displayRevision by mutableIntStateOf(0)
  var autoRecompose by mutableStateOf(false)
  var autoNavigate by mutableStateOf(false)
  var extractorMode by mutableStateOf(Nav3PerformanceExtractorMode.NORMAL)
  var argumentMode by mutableStateOf(Nav3PerformanceArgumentMode.FLAT)
  var integrationMode by mutableStateOf(Nav3PerformanceIntegrationMode.FULL_STACK)
    private set

  var performanceStatus by mutableStateOf("Ready")
    private set

  var performanceRunActive by mutableStateOf(false)
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

  private val mutationToCompositionDurations = Nav3PerformanceDurations()
  private val mutationToFirstDrawDurations = Nav3PerformanceDurations()
  private var pendingOperation: PendingNav3PerformanceOperation? = null
  private var pendingAbPhase: PendingNav3PerformanceTrace? = null
  private var pendingMeasuredPhase: PendingNav3PerformanceTrace? = null
  private var nextTraceCookie = 0
  private var abComparisonCount = 0
  private var collectMeasurements = true
  private var discardNextSentryNavEffectMeasurement = false
  private var suppressNextDestinationChange = false
  private var pendingProcessedNavigationWork = false

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
    mutationToCompositionDurations.clear()
    mutationToFirstDrawDurations.clear()
    comparisonResult = null
    displayRevision++
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
    beginNavigationOperation(measureFirstDraw = false)
    recompositionRequests++
    pendingProcessedNavigationWork = false
    recompositionTick++
    if (!performanceRunActive) {
      displayRevision++
    }
  }

  fun markNavigationMutation() {
    navigationMutations++
    pendingProcessedNavigationWork = true
    if (!performanceRunActive) {
      displayRevision++
    }
  }

  fun markDestinationChange() {
    if (suppressNextDestinationChange) {
      suppressNextDestinationChange = false
      return
    }
    destinationChanges++
    pendingProcessedNavigationWork = true
    if (!performanceRunActive) {
      displayRevision++
    }
  }

  fun markPerformanceDestinationChange() {
    destinationChanges++
    pendingProcessedNavigationWork = true
  }

  fun suppressNextDestinationChange() {
    suppressNextDestinationChange = true
  }

  fun updateIntegrationMode(mode: Nav3PerformanceIntegrationMode) {
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
      PendingNav3PerformanceOperation(
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
    processedCall: Boolean,
    resolvedEntryCount: Int,
  ) {
    if (discardNextSentryNavEffectMeasurement) {
      discardNextSentryNavEffectMeasurement = false
      return
    }
    if (!collectMeasurements) {
      return
    }

    sentryNavEffectAttempts++
    val shouldCountAsProcessed = processedCall && pendingProcessedNavigationWork
    if (shouldCountAsProcessed) {
      sentryNavEffectProcessedCalls++
      capturedEntriesResolved += resolvedEntryCount
    }
    pendingProcessedNavigationWork = false
  }

  fun startPerformanceRun(status: String) {
    finishMeasuredPhase()
    performanceRunActive = true
    performanceStatus = status
    comparisonResult = null
    collectMeasurements = false
  }

  fun updatePerformanceStatus(status: String) {
    performanceStatus = status
  }

  fun startMeasuredIterations() {
    finishMeasuredPhase()
    resetCounters()
    collectMeasurements = true
    val cookie = ++nextTraceCookie
    pendingMeasuredPhase = PendingNav3PerformanceTrace(MEASURED_SECTION, cookie)
    beginAsyncTraceSection(MEASURED_SECTION, cookie)
  }

  fun stopCollectingMeasurements() {
    finishMeasuredPhase()
    collectMeasurements = false
  }

  fun finishWarmUp() {
    resetCounters()
    performanceRunActive = true
    performanceStatus = "Warm-up complete"
  }

  fun finishPerformanceRun(result: String? = null) {
    finishMeasuredPhase()
    finishAbPhase()
    collectMeasurements = true
    performanceRunActive = false
    performanceStatus = "Complete"
    comparisonResult = result
    suppressNextDestinationChange = true
    pendingProcessedNavigationWork = false
    displayRevision++
    emitDiagnosticsSummary()
  }

  fun cancelPerformanceRun(status: String = "Ready") {
    finishMeasuredPhase()
    finishAbPhase()
    collectMeasurements = true
    performanceRunActive = false
    performanceStatus = status
    pendingProcessedNavigationWork = false
    finishPendingOperation()
  }

  fun performanceSummary(label: String): String =
    "$label: first draw ${mutationToFirstDrawDurations.compactSummary()}"

  fun diagnosticsSummary(currentRoute: String, backStack: String): String = buildString {
    appendLine("status=$performanceStatus")
    appendLine("route=$currentRoute")
    appendLine("tracked_stack=$backStack")
    appendLine("recomposition_requests=$recompositionRequests")
    appendLine("navigation_mutations=$navigationMutations")
    appendLine("destination_changes=$destinationChanges")
    appendLine("sentry_nav_effect_attempts=$sentryNavEffectAttempts")
    appendLine("processed_calls=$sentryNavEffectProcessedCalls")
    appendLine("captured_entries_resolved=$capturedEntriesResolved")
    appendLine("name_extractor_calls=$nameExtractorCalls")
    appendLine("arguments_extractor_calls=$argumentsExtractorCalls")
    appendLine("name_extractor_avg=${nameExtractorAverageMicros()}")
    appendLine("arguments_extractor_avg=${argumentsExtractorAverageMicros()}")
    appendLine("mutation_to_composition=${mutationToCompositionSummary()}")
    appendLine("mutation_to_first_draw=${mutationToFirstDrawSummary()}")
    appendLine("first_draws_over_8_3_ms=${firstDrawsOver8Millis()}")
    appendLine("first_draws_over_16_7_ms=${firstDrawsOver16Millis()}")
    comparisonResult?.let { append("ab_result=$it") }
  }

  private fun emitDiagnosticsSummary() {
    val surface = diagnosticsSurfaceName ?: return
    Log.i(NAV_PERF_TAG, "NAV_PERF_DIAGNOSTICS_START surface=$surface")
    diagnosticsSummary(currentRoute = "<runtime>", backStack = "<runtime>")
      .trimEnd()
      .lineSequence()
      .forEach { line -> Log.i(NAV_PERF_TAG, line) }
    Log.i(NAV_PERF_TAG, "NAV_PERF_DIAGNOSTICS_END surface=$surface")
  }

  fun publishDiagnostics(currentRoute: String, backStack: String) {
    val surface = diagnosticsSurfaceName ?: return
    Log.i(NAV_PERF_TAG, "NAV_PERF_DIAGNOSTICS_START surface=$surface")
    diagnosticsSummary(currentRoute = currentRoute, backStack = backStack)
      .trimEnd()
      .lineSequence()
      .forEach { line -> Log.i(NAV_PERF_TAG, line) }
    Log.i(NAV_PERF_TAG, "NAV_PERF_DIAGNOSTICS_END surface=$surface")
  }

  fun nextAbComparisonRunsDisabledFirst(): Boolean = abComparisonCount++ % 2 == 0

  fun beginAbPhase(mode: Nav3PerformanceIntegrationMode) {
    finishAbPhase()
    val sectionName =
      when (mode) {
        Nav3PerformanceIntegrationMode.DISABLED -> AB_DISABLED_SECTION
        Nav3PerformanceIntegrationMode.FULL_STACK -> AB_ENABLED_SECTION
        else -> error("Unsupported A/B integration mode: $mode")
      }
    val cookie = ++nextTraceCookie
    pendingAbPhase = PendingNav3PerformanceTrace(sectionName, cookie)
    beginAsyncTraceSection(sectionName, cookie)
  }

  fun finishAbPhase() {
    pendingAbPhase?.let { phase -> endAsyncTraceSection(phase.sectionName, phase.cookie) }
    pendingAbPhase = null
  }

  private fun finishMeasuredPhase() {
    pendingMeasuredPhase?.let { phase -> endAsyncTraceSection(phase.sectionName, phase.cookie) }
    pendingMeasuredPhase = null
  }

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

  fun recordNameExtraction(sectionName: String, block: () -> String): String {
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

  fun recordArgumentExtraction(
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

private data class PendingNav3PerformanceOperation(
  val cookie: Int,
  val startedAtNanos: Long,
  val collectMeasurement: Boolean,
  val measureFirstDraw: Boolean,
  var compositionRecorded: Boolean = false,
)

private data class PendingNav3PerformanceTrace(val sectionName: String, val cookie: Int)

internal class Nav3PerformanceDurations {
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

  fun minus(other: Nav3PerformanceDurations): Nav3PerformanceDurations =
    Nav3PerformanceDurations().also { result ->
      values.forEachIndexed { index, value ->
        result.add(value - other.values.getOrElse(index) { 0L })
      }
    }
}

internal enum class Nav3PerformanceExtractorMode(val label: String) {
  NORMAL("Normal"),
  HEAVY("Heavy"),
}

internal enum class Nav3PerformanceArgumentMode(val label: String) {
  EMPTY("Empty"),
  FLAT("Flat"),
  NESTED("Nested"),
  LARGE("Large"),
}

internal enum class Nav3PerformanceIntegrationMode(
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

internal enum class Nav3PerformanceRun(val label: String) {
  UNRELATED_RECOMPOSITIONS("Run 20 recompositions"),
  TOP_REPLACEMENTS("Run 20 top replacements"),
  LOWER_ENTRY_MUTATIONS("Run 20 lower mutations"),
  AB_COMPARISON("Run A/B comparison"),
}

internal enum class Nav3PerformancePreset(
  val label: String,
  val stackDepth: Int,
  val maxCapturedBackStackEntries: Int,
  val integrationMode: Nav3PerformanceIntegrationMode,
  val extractorMode: Nav3PerformanceExtractorMode,
  val argumentMode: Nav3PerformanceArgumentMode,
) {
  LIGHT(
    "Light",
    1,
    1,
    Nav3PerformanceIntegrationMode.FULL_STACK,
    Nav3PerformanceExtractorMode.NORMAL,
    Nav3PerformanceArgumentMode.EMPTY,
  ),
  NORMAL(
    "Normal",
    20,
    20,
    Nav3PerformanceIntegrationMode.FULL_STACK,
    Nav3PerformanceExtractorMode.NORMAL,
    Nav3PerformanceArgumentMode.FLAT,
  ),
  CAPTURE_1_OF_100(
    "Capture 1 of 100",
    100,
    1,
    Nav3PerformanceIntegrationMode.FULL_STACK,
    Nav3PerformanceExtractorMode.NORMAL,
    Nav3PerformanceArgumentMode.FLAT,
  ),
  CAPTURE_20_OF_100(
    "Capture 20 of 100",
    100,
    20,
    Nav3PerformanceIntegrationMode.FULL_STACK,
    Nav3PerformanceExtractorMode.NORMAL,
    Nav3PerformanceArgumentMode.FLAT,
  ),
  CAPTURE_100_OF_100(
    "Capture 100 of 100",
    100,
    100,
    Nav3PerformanceIntegrationMode.FULL_STACK,
    Nav3PerformanceExtractorMode.NORMAL,
    Nav3PerformanceArgumentMode.FLAT,
  ),
  HEAVY(
    "Heavy",
    50,
    50,
    Nav3PerformanceIntegrationMode.FULL_STACK,
    Nav3PerformanceExtractorMode.NORMAL,
    Nav3PerformanceArgumentMode.NESTED,
  ),
  SUPER_HEAVY(
    "Super heavy",
    100,
    100,
    Nav3PerformanceIntegrationMode.FULL_STACK,
    Nav3PerformanceExtractorMode.HEAVY,
    Nav3PerformanceArgumentMode.LARGE,
  ),
  NO_ARGUMENTS(
    "No arguments",
    100,
    100,
    Nav3PerformanceIntegrationMode.FULL_STACK_WITHOUT_ARGUMENTS,
    Nav3PerformanceExtractorMode.HEAVY,
    Nav3PerformanceArgumentMode.LARGE,
  ),
  TOP_ONLY(
    "Top only",
    30,
    30,
    Nav3PerformanceIntegrationMode.TOP_ONLY,
    Nav3PerformanceExtractorMode.NORMAL,
    Nav3PerformanceArgumentMode.FLAT,
  ),
  DISABLED_CONTROL(
    "Disabled control",
    30,
    30,
    Nav3PerformanceIntegrationMode.DISABLED,
    Nav3PerformanceExtractorMode.NORMAL,
    Nav3PerformanceArgumentMode.EMPTY,
  ),
}

internal data class Nav3PerformancePanelControls(
  val actualStackEntries: Int,
  val maxCapturedBackStackEntries: Int,
  val onMaxCapturedBackStackEntriesChange: (Int) -> Unit,
  val onApplyPreset: (Nav3PerformancePreset) -> Unit,
  val onRunPerformance: (Nav3PerformanceRun) -> Unit,
)

@Composable
internal fun Nav3PerformancePanel(
  title: String,
  description: String,
  currentRoute: String,
  backStack: String,
  state: Nav3PerformanceState,
  showExtractorControls: Boolean,
  onBuildStack: () -> Unit,
  onMutateLowerEntry: (() -> Unit)? = null,
  onReplaceTop: () -> Unit,
  nav3Controls: Nav3PerformancePanelControls? = null,
) {
  @Suppress("UNUSED_EXPRESSION") state.displayRevision

  val diagnosticsSummary =
    state.diagnosticsSummary(currentRoute = currentRoute, backStack = backStack)

  LaunchedEffect(state.autoRecompose) {
    while (state.autoRecompose) {
      delay(250)
      traceNav3PerformanceSection("Nav3Stress.autoRecompose") {
        state.markRecompositionRequest()
      }
    }
  }

  LaunchedEffect(state.autoNavigate) {
    while (state.autoNavigate) {
      delay(500)
      traceNav3PerformanceSection("Nav3Stress.autoNavigate") { onReplaceTop() }
    }
  }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(16.dp)
        .testTag(navPerformanceTag("root")),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      title,
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
      description,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onBackground,
    )
    CollapsiblePerfInfoRow(
      label = "Performance status",
      value = state.performanceStatus,
      collapsedValue = state.performanceStatus,
      tag = navPerformanceTag("performance_status"),
      defaultExpanded = state.performanceRunActive,
    )
    if (!state.performanceRunActive) {
      CollapsiblePerfInfoRow(
        label = "Diagnostics summary",
        value = diagnosticsSummary,
        collapsedValue = diagnosticsSummary.lineSequence().firstOrNull() ?: diagnosticsSummary,
        tag = navPerformanceTag("diagnostics_summary_top"),
      )
    }

    PerfCard(title = "Current State", tag = navPerformanceTag("current_state_card")) {
      PerfInfoRow("Current route", currentRoute, tag = navPerformanceTag("current_route"))
      PerfInfoRow("Tracked stack", backStack, tag = navPerformanceTag("tracked_stack"))
      nav3Controls?.let { controls ->
        PerfInfoRow(
          "Actual stack entries",
          controls.actualStackEntries.toString(),
          tag = navPerformanceTag("actual_stack_entries"),
        )
        PerfInfoRow(
          "Requested stack depth",
          state.stackDepth.toString(),
          tag = navPerformanceTag("requested_stack_depth"),
        )
        PerfInfoRow(
          "Integration mode",
          state.integrationMode.label,
          tag = navPerformanceTag("integration_mode"),
        )
        PerfInfoRow(
          "Capture enabled",
          if (state.integrationMode.captureBackStack) "Yes" else "No",
          tag = navPerformanceTag("capture_enabled"),
        )
        PerfInfoRow(
          "Capture limit",
          controls.maxCapturedBackStackEntries.toString(),
          tag = navPerformanceTag("capture_limit"),
        )
        PerfInfoRow(
          "Effective captured entries",
          if (state.integrationMode.captureBackStack) {
            minOf(controls.actualStackEntries, controls.maxCapturedBackStackEntries).toString()
          } else {
            "0"
          },
          tag = navPerformanceTag("effective_captured_entries"),
        )
      }
    }

    nav3Controls?.let { controls ->
      PerfCard(title = "Scenarios", tag = navPerformanceTag("scenarios_card")) {
        Nav3PerformancePreset.entries.chunked(2).forEach { presets ->
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { preset ->
              OutlinedButton(
                enabled = !state.performanceRunActive,
                onClick = { controls.onApplyPreset(preset) },
                modifier =
                  Modifier.weight(1f)
                    .testTag(navPerformanceTag("preset_${preset.name.lowercase()}")),
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

      PerfCard(title = "Nav3 Integration", tag = navPerformanceTag("integration_card")) {
        PerfModeRow(
          label = "Mode",
          selectedLabel = state.integrationMode.label,
          enabled = !state.performanceRunActive,
          options =
            Nav3PerformanceIntegrationMode.entries.map { mode ->
              mode.label to { state.updateIntegrationMode(mode) }
            },
        )
        PerfStepper(
          label = "Capture limit",
          value = controls.maxCapturedBackStackEntries,
          enabled = !state.performanceRunActive,
          onDecrement = {
            controls.onMaxCapturedBackStackEntriesChange(
              (controls.maxCapturedBackStackEntries - 1).coerceAtLeast(0)
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

    PerfCard(title = "Stress Controls", tag = navPerformanceTag("stress_controls_card")) {
      PerfStepper(
        label = "Stack depth",
        value = state.stackDepth,
        enabled = !state.performanceRunActive,
        onDecrement = { state.stackDepth = (state.stackDepth - 1).coerceAtLeast(1) },
        onIncrement = { state.stackDepth = (state.stackDepth + 1).coerceAtMost(100) },
      )

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
          enabled = !state.performanceRunActive,
          onClick = onBuildStack,
          modifier = Modifier.weight(1f),
        ) {
          Text("Build Stack")
        }
        Button(
          enabled = !state.performanceRunActive,
          onClick = onReplaceTop,
          modifier = Modifier.weight(1f),
        ) {
          Text("Replace Top")
        }
      }

      onMutateLowerEntry?.let { mutateLowerEntry ->
        Button(
          enabled = !state.performanceRunActive,
          onClick = mutateLowerEntry,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Mutate Lower Entry")
        }
      }

      Button(
        enabled = !state.performanceRunActive,
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
      PerfCard(title = "Fixed Runs", tag = navPerformanceTag("fixed_runs_card")) {
        Nav3PerformanceRun.entries.forEach { run ->
          Button(
            enabled = !state.performanceRunActive,
            onClick = { controls.onRunPerformance(run) },
            modifier =
              Modifier.fillMaxWidth().testTag(navPerformanceTag("run_${run.name.lowercase()}")),
          ) {
            Text(run.label)
          }
        }
        state.comparisonResult?.let { result ->
          PerfInfoRow("A/B result", result, tag = navPerformanceTag("ab_result"))
        }
      }
    }

    if (showExtractorControls) {
      PerfCard(title = "Extractor Inputs") {
        PerfModeRow(
          label = "Extractor cost",
          selectedLabel = state.extractorMode.label,
          enabled = !state.performanceRunActive,
          options =
            Nav3PerformanceExtractorMode.entries.map { mode ->
              mode.label to { state.extractorMode = mode }
            },
        )
        PerfModeRow(
          label = "Argument shape",
          selectedLabel = state.argumentMode.label,
          enabled = !state.performanceRunActive,
          options =
            Nav3PerformanceArgumentMode.entries.map { mode ->
              mode.label to { state.argumentMode = mode }
            },
        )
      }
    }

    PerfCard(title = "Counters", tag = navPerformanceTag("counters_card")) {
      PerfInfoRow(
        "Recomposition requests",
        state.recompositionRequests.toString(),
        tag = navPerformanceTag("recomposition_requests"),
      )
      PerfInfoRow(
        "Navigation mutations",
        state.navigationMutations.toString(),
        tag = navPerformanceTag("navigation_mutations"),
      )
      PerfInfoRow(
        "Destination changes",
        state.destinationChanges.toString(),
        tag = navPerformanceTag("destination_changes"),
      )
      if (showExtractorControls) {
        if (state.performanceRunActive) {
          Text("Metrics are published when the fixed run completes.")
        } else {
          PerfInfoRow(
            "SentryNavEffect attempts",
            state.sentryNavEffectAttempts.toString(),
            tag = navPerformanceTag("sentry_nav_effect_attempts"),
          )
          PerfInfoRow(
            "Calls with extractor work",
            state.sentryNavEffectProcessedCalls.toString(),
            tag = navPerformanceTag("calls_with_extractor_work"),
          )
          PerfInfoRow(
            "Captured entries resolved",
            state.capturedEntriesResolved.toString(),
            tag = navPerformanceTag("captured_entries_resolved"),
          )
          PerfInfoRow(
            "nameExtractor calls",
            state.nameExtractorCalls.toString(),
            tag = navPerformanceTag("name_extractor_calls"),
          )
          PerfInfoRow(
            "argumentsExtractor calls",
            state.argumentsExtractorCalls.toString(),
            tag = navPerformanceTag("arguments_extractor_calls"),
          )
          PerfInfoRow(
            "nameExtractor avg",
            state.nameExtractorAverageMicros(),
            tag = navPerformanceTag("name_extractor_avg"),
          )
          PerfInfoRow(
            "argumentsExtractor avg",
            state.argumentsExtractorAverageMicros(),
            tag = navPerformanceTag("arguments_extractor_avg"),
          )
          PerfInfoRow(
            "Mutation to composition",
            state.mutationToCompositionSummary(),
            tag = navPerformanceTag("mutation_to_composition"),
          )
          PerfInfoRow(
            "Mutation to first draw",
            state.mutationToFirstDrawSummary(),
            tag = navPerformanceTag("mutation_to_first_draw"),
          )
          PerfInfoRow(
            "First draws over 8.3 ms",
            state.firstDrawsOver8Millis().toString(),
            tag = navPerformanceTag("first_draws_over_8_3_ms"),
          )
          PerfInfoRow(
            "First draws over 16.7 ms",
            state.firstDrawsOver16Millis().toString(),
            tag = navPerformanceTag("first_draws_over_16_7_ms"),
          )
        }
      }
      Button(
        enabled = !state.performanceRunActive,
        onClick = { state.resetCounters() },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("Reset Counters")
      }
      Button(
        enabled = !state.performanceRunActive,
        onClick = { state.publishDiagnostics(currentRoute = currentRoute, backStack = backStack) },
        modifier = Modifier.fillMaxWidth().testTag(navPerformanceTag("publish_diagnostics")),
      ) {
        Text("Publish diagnostics")
      }
    }
  }
}

@Composable
private fun PerfCard(
  title: String,
  tag: String? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    modifier = Modifier.fillMaxWidth().then(if (tag != null) Modifier.testTag(tag) else Modifier),
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
private fun PerfInfoRow(label: String, value: String, tag: String? = null) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(8.dp),
    modifier = Modifier.fillMaxWidth().then(if (tag != null) Modifier.testTag(tag) else Modifier),
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        label,
        fontWeight = FontWeight.Bold,
        modifier =
          Modifier.weight(1f).then(if (tag != null) Modifier.testTag("${tag}_label") else Modifier),
      )
      Spacer(Modifier.size(12.dp))
      Text(
        value,
        modifier =
          Modifier.weight(1f).then(if (tag != null) Modifier.testTag("${tag}_value") else Modifier),
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollapsiblePerfInfoRow(
  label: String,
  value: String,
  collapsedValue: String,
  tag: String? = null,
  defaultExpanded: Boolean = false,
) {
  var expanded by remember(label, value) { mutableStateOf(defaultExpanded) }
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(8.dp),
    modifier = Modifier.fillMaxWidth().then(if (tag != null) Modifier.testTag(tag) else Modifier),
    onClick = { expanded = !expanded },
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          label,
          fontWeight = FontWeight.Bold,
          modifier =
            Modifier.weight(1f)
              .then(if (tag != null) Modifier.testTag("${tag}_label") else Modifier),
        )
        TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
          Text(if (expanded) "Collapse" else "Expand")
        }
      }
      Text(
        if (expanded) value else collapsedValue,
        style = MaterialTheme.typography.bodyMedium,
        modifier =
          Modifier.fillMaxWidth()
            .then(if (tag != null) Modifier.testTag("${tag}_value") else Modifier),
      )
    }
  }
}

private fun navPerformanceTag(name: String): String = "navigation_perf_$name"

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

internal fun traceNav3PerformanceSection(sectionName: String, block: () -> Unit) {
  Trace.beginSection(sectionName)
  try {
    block()
  } finally {
    Trace.endSection()
  }
}

internal fun nav3PerformanceArguments(
  mode: Nav3PerformanceArgumentMode,
  index: Int,
  generation: Int,
): Map<String, Any?> =
  when (mode) {
    Nav3PerformanceArgumentMode.EMPTY -> emptyMap()
    Nav3PerformanceArgumentMode.FLAT ->
      mapOf("index" to index, "generation" to generation, "label" to "route-$index")
    Nav3PerformanceArgumentMode.NESTED ->
      mapOf(
        "route" to
          mapOf(
            "index" to index,
            "generation" to generation,
            "source" to "performance",
          ),
        "tags" to listOf("nav", "stress", "route-$index"),
      )
    Nav3PerformanceArgumentMode.LARGE ->
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

@Volatile private var nav3PerformanceExtractorBlackhole = 0

internal fun consumeNav3PerformanceExtractorWork(
  mode: Nav3PerformanceExtractorMode,
  seed: Int,
) {
  if (mode == Nav3PerformanceExtractorMode.NORMAL) {
    return
  }

  var checksum = seed
  repeat(2_000) { index -> checksum = (checksum * 31) xor index }
  nav3PerformanceExtractorBlackhole = checksum
}

internal fun nav3PerformanceBackStackPreview(entries: List<String>): String {
  if (entries.size <= 8) {
    return entries.joinToString(" -> ")
  }

  return "${entries.size} entries: " +
    entries.take(3).joinToString(" -> ") +
    " -> ... -> " +
    entries.takeLast(3).joinToString(" -> ")
}

private fun Nav3PerformanceState.nameExtractorAverageMicros(): String =
  averageMicros(nameExtractorNanos, nameExtractorCalls)

private fun Nav3PerformanceState.argumentsExtractorAverageMicros(): String =
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
private const val AB_DISABLED_SECTION = "Nav3Stress.ab.disabled"
private const val AB_ENABLED_SECTION = "Nav3Stress.ab.enabled"
private const val MEASURED_SECTION = "Nav3Stress.measured"
private const val NAV_PERF_TAG = "NavPerformance"
