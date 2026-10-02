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
  onRunPerformance: (Nav3PerformanceRun) -> Unit,
) {
  val performanceRunActive = performanceState.performanceRunActive
  if (performanceRunActive) {
    Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text("Performance run", style = MaterialTheme.typography.headlineMedium)
      Text(performanceState.performanceStatus, style = MaterialTheme.typography.bodyLarge)
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
        onRunPerformance = onRunPerformance,
      ),
  )
}

internal suspend fun prepareNav3PerformancePreset(
  preset: Nav3PerformancePreset,
  state: Nav3PerformanceState,
  backStack: SnapshotStateList<Nav3Route>,
  onMaxCapturedBackStackEntriesChange: (Int) -> Unit,
) {
  state.startPerformanceRun("Preparing ${preset.label}")
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
    state.cancelPerformanceRun(status = "Ready: ${preset.label}")
  } catch (e: CancellationException) {
    state.cancelPerformanceRun()
    throw e
  }
}

internal suspend fun runNav3Performance(
  run: Nav3PerformanceRun,
  state: Nav3PerformanceState,
  backStack: SnapshotStateList<Nav3Route>,
  warmUpOnly: Boolean = false,
  skipWarmUp: Boolean = false,
) {
  if (!skipWarmUp) {
    state.startPerformanceRun("Warming up")
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
      state.updatePerformanceStatus("Measuring $PERFORMANCE_MEASURED_ITERATIONS iterations")
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
    state.finishPerformanceRun()
  } catch (e: CancellationException) {
    state.cancelPerformanceRun()
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
      state.updatePerformanceStatus("Warming up ${mode.label}")
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
      state.updatePerformanceStatus("Measuring ${mode.label}")
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
      results += state.performanceSummary(mode.label)
    }
  } finally {
    state.updateIntegrationMode(originalMode)
    awaitNav3PerformanceFrames()
  }

  state.finishPerformanceRun(results.joinToString(" | "))
}

private suspend fun runNav3PerformanceIterations(
  iterationCount: Int,
  run: Nav3PerformanceRun,
  state: Nav3PerformanceState,
  backStack: SnapshotStateList<Nav3Route>,
): Boolean {
  repeat(iterationCount) {
    if (!state.performanceRunActive) {
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
      state.markPerformanceDestinationChange()
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
