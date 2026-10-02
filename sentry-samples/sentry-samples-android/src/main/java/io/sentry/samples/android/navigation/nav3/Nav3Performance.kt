package io.sentry.samples.android.navigation.nav3

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

@Composable
internal fun Nav3PerformanceRoute(
  route: Nav3Route.Performance,
  backStack: SnapshotStateList<Nav3Route>,
  performanceState: Nav3PerformanceState,
  maxCapturedBackStackEntries: Int,
  onMaxCapturedBackStackEntriesChange: (Int) -> Unit,
  onApplyPreset: (Nav3PerformancePreset) -> Unit,
  onRunBenchmark: (Nav3PerformanceRun) -> Unit,
) {
  val benchmarkRunning = performanceState.benchmarkRunning
  if (benchmarkRunning) {
    Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text("Performance benchmark", style = MaterialTheme.typography.headlineMedium)
      Text(performanceState.benchmarkStatus, style = MaterialTheme.typography.bodyLarge)
      Text("Diagnostics will be published when the run completes.")
    }
    return
  }

  LaunchedEffect(route) { performanceState.markDestinationChange() }

  Nav3PerformancePanel(
    title = "Performance",
    description =
      "Stress SentryNavEffect with deep stacks, unrelated recompositions, route extraction, and " +
        "argument sanitization. Use Perfetto sections like SentryNavEffect.onBackStackChanged and " +
        "the Nav3Stress markers to inspect hot paths.",
    currentRoute = "/${route.previewName}",
    backStack = nav3PerformanceBackStackPreview(backStack.map { entry -> "/${entry.previewName}" }),
    state = performanceState,
    showExtractorControls = true,
    onBuildStack = {
      traceNav3PerformanceSection("Nav3Stress.buildStack") {
        performanceState.beginNavigationOperation()
        backStack.openPerformanceStack(
          depth = performanceState.stackDepth,
          generation = performanceState.nextGeneration(),
        )
        performanceState.markNavigationMutation()
      }
    },
    onMutateLowerEntry = {
      traceNav3PerformanceSection("Nav3Stress.mutateLowerEntry") {
        performanceState.beginNavigationOperation()
        backStack.mutatePerformanceLowerEntry(performanceState.nextGeneration())
        performanceState.markNavigationMutation()
      }
    },
    onReplaceTop = {
      traceNav3PerformanceSection("Nav3Stress.replaceTop") {
        performanceState.beginNavigationOperation()
        backStack.replacePerformanceTop(performanceState.nextGeneration())
        performanceState.markNavigationMutation()
      }
    },
    nav3Controls =
      Nav3PerformancePanelControls(
        actualStackEntries = backStack.size,
        maxCapturedBackStackEntries = maxCapturedBackStackEntries,
        onMaxCapturedBackStackEntriesChange = onMaxCapturedBackStackEntriesChange,
        onApplyPreset = onApplyPreset,
        onRunBenchmark = onRunBenchmark,
      ),
  )
}

internal suspend fun prepareNav3PerformancePreset(
  preset: Nav3PerformancePreset,
  state: Nav3PerformanceState,
  backStack: SnapshotStateList<Nav3Route>,
  onMaxCapturedBackStackEntriesChange: (Int) -> Unit,
) {
  state.startBenchmark("Preparing ${preset.label}")
  try {
    state.stackDepth = preset.stackDepth
    state.extractorMode = preset.extractorMode
    state.argumentMode = preset.argumentMode
    state.updateIntegrationMode(preset.integrationMode)
    onMaxCapturedBackStackEntriesChange(preset.maxCapturedBackStackEntries)
    backStack.openPerformanceStack(preset.stackDepth, state.nextGeneration())
    awaitNav3PerformanceFrames()
    if (
      !runNav3PerformanceIterations(
        iterationCount = PERFORMANCE_WARM_UP_ITERATIONS,
        run = Nav3PerformanceRun.TOP_REPLACEMENTS,
        state = state,
        backStack = backStack,
      )
    ) {
      return
    }
    state.resetCounters()
    state.suppressNextDestinationChange()
    state.cancelBenchmark(status = "Ready: ${preset.label}")
  } catch (e: CancellationException) {
    state.cancelBenchmark()
    throw e
  }
}

internal suspend fun runNav3PerformanceBenchmark(
  run: Nav3PerformanceRun,
  state: Nav3PerformanceState,
  backStack: SnapshotStateList<Nav3Route>,
  warmUpOnly: Boolean = false,
  skipWarmUp: Boolean = false,
) {
  if (!skipWarmUp) {
    state.startBenchmark("Warming up")
  }
  try {
    if (run == Nav3PerformanceRun.AB_COMPARISON) {
      runNav3PerformanceAbComparison(state, backStack)
      return
    }

    if (
      !skipWarmUp &&
        !runNav3PerformanceIterations(
          iterationCount = PERFORMANCE_WARM_UP_ITERATIONS,
          run = run,
          state = state,
          backStack = backStack,
        )
    ) {
      return
    }
    if (warmUpOnly) {
      state.finishWarmUp()
      return
    }
    state.startMeasuredIterations()
    if (!skipWarmUp) {
      state.updateBenchmarkStatus("Measuring $PERFORMANCE_MEASURED_ITERATIONS iterations")
    }
    if (
      !runNav3PerformanceIterations(
        iterationCount = PERFORMANCE_MEASURED_ITERATIONS,
        run = run,
        state = state,
        backStack = backStack,
      )
    ) {
      return
    }
    state.finishBenchmark()
  } catch (e: CancellationException) {
    state.cancelBenchmark()
    throw e
  }
}

private suspend fun runNav3PerformanceAbComparison(
  state: Nav3PerformanceState,
  backStack: SnapshotStateList<Nav3Route>,
) {
  val originalMode = state.integrationMode
  val modes =
    if (state.nextAbComparisonRunsDisabledFirst()) {
      listOf(
        Nav3PerformanceIntegrationMode.DISABLED,
        Nav3PerformanceIntegrationMode.FULL_STACK,
      )
    } else {
      listOf(
        Nav3PerformanceIntegrationMode.FULL_STACK,
        Nav3PerformanceIntegrationMode.DISABLED,
      )
    }
  val results = mutableListOf<String>()

  try {
    modes.forEach { mode ->
      state.updateBenchmarkStatus("Warming up ${mode.label}")
      state.updateIntegrationMode(mode)
      awaitNav3PerformanceFrames()
      if (
        !runNav3PerformanceIterations(
          iterationCount = PERFORMANCE_WARM_UP_ITERATIONS,
          run = Nav3PerformanceRun.TOP_REPLACEMENTS,
          state = state,
          backStack = backStack,
        )
      ) {
        return
      }
      state.startMeasuredIterations()
      state.updateBenchmarkStatus("Measuring ${mode.label}")
      state.beginAbPhase(mode)
      val completed =
        try {
          runNav3PerformanceIterations(
            iterationCount = PERFORMANCE_MEASURED_ITERATIONS,
            run = Nav3PerformanceRun.TOP_REPLACEMENTS,
            state = state,
            backStack = backStack,
          )
        } finally {
          state.finishAbPhase()
        }
      if (!completed) {
        return
      }
      state.stopCollectingMeasurements()
      results += state.benchmarkSummary(mode.label)
    }
  } finally {
    state.updateIntegrationMode(originalMode)
    awaitNav3PerformanceFrames()
  }

  state.finishBenchmark(results.joinToString(" | "))
}

private suspend fun runNav3PerformanceIterations(
  iterationCount: Int,
  run: Nav3PerformanceRun,
  state: Nav3PerformanceState,
  backStack: SnapshotStateList<Nav3Route>,
): Boolean {
  repeat(iterationCount) {
    if (!state.benchmarkRunning) {
      return false
    }
    performNav3PerformanceIteration(run, state, backStack)
    awaitNav3PerformanceFrames()
  }
  return true
}

private fun performNav3PerformanceIteration(
  run: Nav3PerformanceRun,
  state: Nav3PerformanceState,
  backStack: SnapshotStateList<Nav3Route>,
) {
  when (run) {
    Nav3PerformanceRun.UNRELATED_RECOMPOSITIONS -> state.markRecompositionRequest()
    Nav3PerformanceRun.TOP_REPLACEMENTS -> {
      state.beginNavigationOperation()
      backStack.replacePerformanceTop(state.nextGeneration())
      state.markNavigationMutation()
      state.markBenchmarkDestinationChange()
    }
    Nav3PerformanceRun.LOWER_ENTRY_MUTATIONS -> {
      state.beginNavigationOperation()
      backStack.mutatePerformanceLowerEntry(state.nextGeneration())
      state.markNavigationMutation()
    }
    Nav3PerformanceRun.AB_COMPARISON -> error("A/B comparison runs its own iterations")
  }
}

internal suspend fun awaitNav3PerformanceFrames() {
  withFrameNanos {}
  withFrameNanos {}
}

private const val PERFORMANCE_WARM_UP_ITERATIONS = 5
private const val PERFORMANCE_MEASURED_ITERATIONS = 20

internal const val NAV3_PERFORMANCE_PRESET_EXTRA = "nav3_performance_preset"
internal const val NAV3_PERFORMANCE_RUN_EXTRA = "nav3_performance_run"
internal const val NAV3_PERFORMANCE_WARM_UP_ONLY_EXTRA = "nav3_performance_warm_up_only"
internal const val NAV3_PERFORMANCE_SKIP_WARM_UP_EXTRA = "nav3_performance_skip_warm_up"

internal data class Nav3PerformanceRunRequest(
  val id: Int,
  val run: Nav3PerformanceRun,
  val warmUpOnly: Boolean,
  val skipWarmUp: Boolean,
)
