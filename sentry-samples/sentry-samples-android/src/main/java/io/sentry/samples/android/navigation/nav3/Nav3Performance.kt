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
import io.sentry.samples.android.navigation.common.Nav3PerformanceControls
import io.sentry.samples.android.navigation.common.NavigationPerformanceIntegrationMode
import io.sentry.samples.android.navigation.common.NavigationPerformancePanel
import io.sentry.samples.android.navigation.common.NavigationPerformancePreset
import io.sentry.samples.android.navigation.common.NavigationPerformanceRun
import io.sentry.samples.android.navigation.common.NavigationPerformanceState
import io.sentry.samples.android.navigation.common.navigationPerformanceBackStackPreview
import io.sentry.samples.android.navigation.common.traceNavigationPerformanceSection
import kotlinx.coroutines.CancellationException

@Composable
internal fun Nav3PerformanceRoute(
  route: Nav3Route.Performance,
  backStack: SnapshotStateList<Nav3Route>,
  performanceState: NavigationPerformanceState,
  maxCapturedBackStackEntries: Int,
  onMaxCapturedBackStackEntriesChange: (Int) -> Unit,
  onApplyPreset: (NavigationPerformancePreset) -> Unit,
  onRunBenchmark: (NavigationPerformanceRun) -> Unit,
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

  NavigationPerformancePanel(
    title = "Performance",
    description =
      "Stress SentryNavEffect with deep stacks, unrelated recompositions, route extraction, and " +
        "argument sanitization. Use Perfetto sections like SentryNavEffect.onBackStackChanged and " +
        "the Nav3Stress markers to inspect hot paths.",
    currentRoute = "/${route.previewName}",
    backStack =
      navigationPerformanceBackStackPreview(backStack.map { entry -> "/${entry.previewName}" }),
    state = performanceState,
    showExtractorControls = true,
    onBuildStack = {
      traceNavigationPerformanceSection("Nav3Stress.buildStack") {
        performanceState.beginNavigationOperation()
        backStack.openPerformanceStack(
          depth = performanceState.stackDepth,
          generation = performanceState.nextGeneration(),
        )
        performanceState.markNavigationMutation()
      }
    },
    onMutateLowerEntry = {
      traceNavigationPerformanceSection("Nav3Stress.mutateLowerEntry") {
        performanceState.beginNavigationOperation()
        backStack.mutatePerformanceLowerEntry(performanceState.nextGeneration())
        performanceState.markNavigationMutation()
      }
    },
    onReplaceTop = {
      traceNavigationPerformanceSection("Nav3Stress.replaceTop") {
        performanceState.beginNavigationOperation()
        backStack.replacePerformanceTop(performanceState.nextGeneration())
        performanceState.markNavigationMutation()
      }
    },
    nav3Controls =
      Nav3PerformanceControls(
        actualStackEntries = backStack.size,
        maxCapturedBackStackEntries = maxCapturedBackStackEntries,
        onMaxCapturedBackStackEntriesChange = onMaxCapturedBackStackEntriesChange,
        onApplyPreset = onApplyPreset,
        onRunBenchmark = onRunBenchmark,
      ),
  )
}

internal suspend fun prepareNavigationPerformancePreset(
  preset: NavigationPerformancePreset,
  state: NavigationPerformanceState,
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
    awaitNavigationPerformanceFrames()
    if (
      !runNavigationPerformanceIterations(
        iterationCount = PERFORMANCE_WARM_UP_ITERATIONS,
        run = NavigationPerformanceRun.TOP_REPLACEMENTS,
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

internal suspend fun runNavigationPerformanceBenchmark(
  run: NavigationPerformanceRun,
  state: NavigationPerformanceState,
  backStack: SnapshotStateList<Nav3Route>,
  warmUpOnly: Boolean = false,
  skipWarmUp: Boolean = false,
) {
  if (!skipWarmUp) {
    state.startBenchmark("Warming up")
  }
  try {
    if (run == NavigationPerformanceRun.AB_COMPARISON) {
      runNavigationPerformanceAbComparison(state, backStack)
      return
    }

    if (
      !skipWarmUp &&
        !runNavigationPerformanceIterations(
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
      !runNavigationPerformanceIterations(
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

private suspend fun runNavigationPerformanceAbComparison(
  state: NavigationPerformanceState,
  backStack: SnapshotStateList<Nav3Route>,
) {
  val originalMode = state.integrationMode
  val modes =
    if (state.nextAbComparisonRunsDisabledFirst()) {
      listOf(
        NavigationPerformanceIntegrationMode.DISABLED,
        NavigationPerformanceIntegrationMode.FULL_STACK,
      )
    } else {
      listOf(
        NavigationPerformanceIntegrationMode.FULL_STACK,
        NavigationPerformanceIntegrationMode.DISABLED,
      )
    }
  val results = mutableListOf<String>()

  try {
    modes.forEach { mode ->
      state.updateBenchmarkStatus("Warming up ${mode.label}")
      state.updateIntegrationMode(mode)
      awaitNavigationPerformanceFrames()
      if (
        !runNavigationPerformanceIterations(
          iterationCount = PERFORMANCE_WARM_UP_ITERATIONS,
          run = NavigationPerformanceRun.TOP_REPLACEMENTS,
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
          runNavigationPerformanceIterations(
            iterationCount = PERFORMANCE_MEASURED_ITERATIONS,
            run = NavigationPerformanceRun.TOP_REPLACEMENTS,
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
    awaitNavigationPerformanceFrames()
  }

  state.finishBenchmark(results.joinToString(" | "))
}

private suspend fun runNavigationPerformanceIterations(
  iterationCount: Int,
  run: NavigationPerformanceRun,
  state: NavigationPerformanceState,
  backStack: SnapshotStateList<Nav3Route>,
): Boolean {
  repeat(iterationCount) {
    if (!state.benchmarkRunning) {
      return false
    }
    performNavigationPerformanceIteration(run, state, backStack)
    awaitNavigationPerformanceFrames()
  }
  return true
}

private fun performNavigationPerformanceIteration(
  run: NavigationPerformanceRun,
  state: NavigationPerformanceState,
  backStack: SnapshotStateList<Nav3Route>,
) {
  when (run) {
    NavigationPerformanceRun.UNRELATED_RECOMPOSITIONS -> state.markRecompositionRequest()
    NavigationPerformanceRun.TOP_REPLACEMENTS -> {
      state.beginNavigationOperation()
      backStack.replacePerformanceTop(state.nextGeneration())
      state.markNavigationMutation()
      state.markBenchmarkDestinationChange()
    }
    NavigationPerformanceRun.LOWER_ENTRY_MUTATIONS -> {
      state.beginNavigationOperation()
      backStack.mutatePerformanceLowerEntry(state.nextGeneration())
      state.markNavigationMutation()
    }
    NavigationPerformanceRun.AB_COMPARISON -> error("A/B comparison runs its own iterations")
  }
}

internal suspend fun awaitNavigationPerformanceFrames() {
  withFrameNanos {}
  withFrameNanos {}
}

private const val PERFORMANCE_WARM_UP_ITERATIONS = 5
private const val PERFORMANCE_MEASURED_ITERATIONS = 20

internal const val NAV3_PERFORMANCE_PRESET_EXTRA = "nav3_performance_preset"
internal const val NAV3_PERFORMANCE_RUN_EXTRA = "nav3_performance_run"
internal const val NAV3_PERFORMANCE_WARM_UP_ONLY_EXTRA = "nav3_performance_warm_up_only"
internal const val NAV3_PERFORMANCE_SKIP_WARM_UP_EXTRA = "nav3_performance_skip_warm_up"

internal data class NavigationPerformanceRunRequest(
  val id: Int,
  val run: NavigationPerformanceRun,
  val warmUpOnly: Boolean,
  val skipWarmUp: Boolean,
)
