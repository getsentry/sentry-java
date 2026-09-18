package io.sentry.samples.android.navigation

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import io.sentry.Sentry
import io.sentry.compose.SentryTraced
import io.sentry.compose.navigation3.SentryNavEffect
import io.sentry.compose.navigation3.SentryNavOptions
import io.sentry.samples.android.GithubAPI
import io.sentry.samples.android.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sample app Activity for testing Sentry's
 * [Nav3](https://developer.android.com/guide/navigation/navigation-3) integrations.
 *
 * Look at Google's [nav3-recipes](https://github.com/android/nav3-recipes) for helpful patterns to
 * test against. (This Activity doesn't address all of them yet, so update its implementation as
 * needed.)
 */
class Nav3Activity : ComponentActivity() {

  private var previousEnableUserInteractionTracing = false
  private val performanceState = NavigationPerformanceState(measureRenderLatency = true)
  private var performanceRunRequest by mutableStateOf<NavigationPerformanceRunRequest?>(null)
  private var nextPerformanceRunRequestId = 0

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val options = Sentry.getCurrentScopes().options
    previousEnableUserInteractionTracing = options.isEnableUserInteractionTracing
    options.isEnableUserInteractionTracing = false

    val initialPerformancePreset =
      intent.getStringExtra(NAV3_PERFORMANCE_PRESET_EXTRA)?.let { presetName ->
        NavigationPerformancePreset.entries.firstOrNull { it.name == presetName }
      }
    val initialPerformanceRun =
      intent.getStringExtra(NAV3_PERFORMANCE_RUN_EXTRA)?.let { runName ->
        NavigationPerformanceRun.entries.firstOrNull { it.name == runName }
      }
    setContent {
      MaterialTheme {
        Nav3SampleApp(
          performanceState = performanceState,
          initialPerformancePreset = initialPerformancePreset,
          initialPerformanceRun = initialPerformanceRun,
          performanceRunRequest = performanceRunRequest,
        )
      }
    }
  }

  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    val runName = intent.getStringExtra(NAV3_PERFORMANCE_RUN_EXTRA) ?: return
    val run = NavigationPerformanceRun.entries.firstOrNull { it.name == runName } ?: return
    val skipWarmUp = intent.getBooleanExtra(NAV3_PERFORMANCE_SKIP_WARM_UP_EXTRA, false)
    if (!skipWarmUp || !performanceState.benchmarkRunning) {
      performanceState.startBenchmark("Starting")
    }
    performanceRunRequest =
      NavigationPerformanceRunRequest(
        id = ++nextPerformanceRunRequestId,
        run = run,
        warmUpOnly = intent.getBooleanExtra(NAV3_PERFORMANCE_WARM_UP_ONLY_EXTRA, false),
        skipWarmUp = skipWarmUp,
      )
  }

  override fun onDestroy() {
    Sentry.getCurrentScopes().options.isEnableUserInteractionTracing =
      previousEnableUserInteractionTracing
    super.onDestroy()
  }
}

@SuppressLint("ContextCastToActivity")
@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun Nav3SampleApp(
  performanceState: NavigationPerformanceState,
  initialPerformancePreset: NavigationPerformancePreset? = null,
  initialPerformanceRun: NavigationPerformanceRun? = null,
  performanceRunRequest: NavigationPerformanceRunRequest? = null,
) {
  val activity = LocalContext.current as? ComponentActivity
  // Covers the saveable-backstack recipe without a separate scenario.
  val backStack = rememberSaveableNav3BackStack()
  val dialogSceneStrategy = remember { Nav3DialogSceneStrategy<Nav3Route>() }
  val bottomSheetSceneStrategy = remember { Nav3BottomSheetSceneStrategy<Nav3Route>() }

  var enableNavigationBreadcrumbs by remember { mutableStateOf(true) }
  var enableNavigationTransactions by remember { mutableStateOf(true) }
  var captureBackStack by remember { mutableStateOf(true) }
  var maxCapturedBackStackEntries by remember { mutableIntStateOf(10) }
  var routeActivationAction by remember { mutableStateOf(RouteActivationAction.NONE) }
  var selectedScenario by rememberSaveable { mutableStateOf(Nav3Scenario.SINGLE_STACK) }
  var showCrashConfirmation by remember { mutableStateOf(false) }
  val performanceScope = rememberCoroutineScope()

  if (selectedScenario == Nav3Scenario.PERFORMANCE) {
    @Suppress("UNUSED_EXPRESSION") performanceState.recompositionTick
  }

  val isPerformanceScenario = selectedScenario == Nav3Scenario.PERFORMANCE
  val sentryBackStack = if (isPerformanceScenario) backStack.toList() else backStack
  val integrationMode = performanceState.integrationMode
  val effectiveCaptureBackStack =
    if (isPerformanceScenario) integrationMode.captureBackStack else captureBackStack
  val sentryNavOptions =
    remember(
      enableNavigationBreadcrumbs,
      enableNavigationTransactions,
      effectiveCaptureBackStack,
      maxCapturedBackStackEntries,
    ) {
      SentryNavOptions(
        enableNavigationBreadcrumbs = enableNavigationBreadcrumbs,
        enableNavigationTransactions = enableNavigationTransactions,
        captureBackStack = effectiveCaptureBackStack,
        maxCapturedBackStackEntries = maxCapturedBackStackEntries,
      )
    }
  val performanceExtractorMode = performanceState.extractorMode
  val nameExtractor =
    remember(isPerformanceScenario, performanceExtractorMode) {
      { route: Nav3Route ->
        if (isPerformanceScenario) {
          performanceState.recordNameExtractor("Nav3Stress.nameExtractor") {
            consumeNavigationPerformanceExtractorWork(
              performanceExtractorMode,
              route.performanceSeed,
            )
            route.routeName
          }
        } else {
          route.routeName
        }
      }
    }
  val performanceArgumentMode = performanceState.argumentMode
  val argumentsExtractor =
    if (isPerformanceScenario && !integrationMode.includeArguments) {
      null
    } else {
      remember(isPerformanceScenario, performanceExtractorMode, performanceArgumentMode) {
        { route: Nav3Route ->
          if (isPerformanceScenario) {
            performanceState.recordArgumentsExtractor("Nav3Stress.argumentsExtractor") {
              consumeNavigationPerformanceExtractorWork(
                performanceExtractorMode,
                route.performanceSeed,
              )
              if (route is Nav3Route.Performance) {
                navigationPerformanceArguments(
                  performanceArgumentMode,
                  route.index,
                  route.generation,
                )
              } else {
                route.arguments
              }
            }
          } else {
            route.arguments
          }
        }
      }
    }

  if (!isPerformanceScenario || integrationMode != NavigationPerformanceIntegrationMode.DISABLED) {
    val nameExtractorCallsBefore = performanceState.nameExtractorCalls
    val argumentsExtractorCallsBefore = performanceState.argumentsExtractorCalls
    val extractorNanosBefore =
      performanceState.nameExtractorNanos + performanceState.argumentsExtractorNanos
    val startedAtNanos = System.nanoTime()
    if (isPerformanceScenario) {
      android.os.Trace.beginSection(performanceState.sentryNavEffectTraceSection())
    }
    SentryNavEffect(
      backStack = sentryBackStack,
      options = sentryNavOptions,
      nameExtractor = nameExtractor,
      argumentsExtractor = argumentsExtractor,
    )
    if (isPerformanceScenario) {
      android.os.Trace.endSection()
      performanceState.recordSentryNavEffect(
        durationNanos = System.nanoTime() - startedAtNanos,
        nameExtractorCallsBefore = nameExtractorCallsBefore,
        argumentsExtractorCallsBefore = argumentsExtractorCallsBefore,
        extractorNanosBefore = extractorNanosBefore,
      )
    }
  }

  SideEffect { performanceState.recordComposition() }

  val applyPerformancePreset: (NavigationPerformancePreset) -> Unit = { preset ->
    if (!performanceState.benchmarkRunning) {
      performanceScope.launch {
        prepareNavigationPerformancePreset(
          preset = preset,
          state = performanceState,
          backStack = backStack,
          onMaxCapturedBackStackEntriesChange = { maxCapturedBackStackEntries = it },
        )
      }
    }
  }
  val runPerformanceBenchmark: (NavigationPerformanceRun) -> Unit = { run ->
    if (!performanceState.benchmarkRunning) {
      performanceScope.launch {
        runNavigationPerformanceBenchmark(
          run = run,
          state = performanceState,
          backStack = backStack,
        )
      }
    }
  }

  LaunchedEffect(initialPerformancePreset, initialPerformanceRun) {
    val preset = initialPerformancePreset ?: return@LaunchedEffect
    selectedScenario = Nav3Scenario.PERFORMANCE
    backStack.openScenario(Nav3Scenario.PERFORMANCE)
    awaitNavigationPerformanceFrames()
    prepareNavigationPerformancePreset(
      preset = preset,
      state = performanceState,
      backStack = backStack,
      onMaxCapturedBackStackEntriesChange = { maxCapturedBackStackEntries = it },
    )
    if (initialPerformanceRun != null) {
      runNavigationPerformanceBenchmark(
        run = initialPerformanceRun,
        state = performanceState,
        backStack = backStack,
      )
    }
  }

  LaunchedEffect(performanceRunRequest) {
    val request = performanceRunRequest ?: return@LaunchedEffect
    runNavigationPerformanceBenchmark(
      run = request.run,
      state = performanceState,
      backStack = backStack,
      warmUpOnly = request.warmUpOnly,
      skipWarmUp = request.skipWarmUp,
    )
  }

  Scaffold(
    topBar = {
      Nav3TopBar(
        backStack = backStack,
        maxCapturedBackStackEntries = maxCapturedBackStackEntries,
        enableNavigationBreadcrumbs = enableNavigationBreadcrumbs,
        onEnableNavigationBreadcrumbsChange = { enableNavigationBreadcrumbs = it },
        enableNavigationTransactions = enableNavigationTransactions,
        onEnableNavigationTransactionsChange = { enableNavigationTransactions = it },
        captureBackStack = captureBackStack,
        onCaptureBackStackChange = { captureBackStack = it },
        onMaxCapturedBackStackEntriesChange = { maxCapturedBackStackEntries = it },
      )
    },
    bottomBar = {
      SentryControls(
        selectedAction = routeActivationAction,
        onActionSelected = { action -> routeActivationAction = action },
        onCaptureException = { captureSampleException("Nav3") },
        onCrashApp = { showCrashConfirmation = true },
      )
    },
  ) { innerPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      ScenarioBar(
        selectedScenario = selectedScenario,
        onScenarioSelected = { scenario ->
          if (scenario != Nav3Scenario.PERFORMANCE && performanceState.benchmarkRunning) {
            performanceState.cancelBenchmark()
          }
          selectedScenario = scenario
          performanceState.autoRecompose = false
          performanceState.autoNavigate = false
          backStack.openScenario(scenario)
          if (scenario == Nav3Scenario.PERFORMANCE) {
            performanceState.resetCounters()
            performanceState.markNavigationMutation()
          }
        },
      )
      Box(modifier = Modifier.weight(1f)) {
        NavDisplay(
          backStack = backStack,
          modifier =
            Modifier.fillMaxSize().drawWithContent {
              drawContent()
              performanceState.recordFirstDraw()
            },
          onBack = {
            if (backStack.size > 1) {
              backStack.removeLastOrNull()
            } else {
              activity?.finish()
            }
          },
          sceneStrategies = listOf(dialogSceneStrategy, bottomSheetSceneStrategy),
          entryProvider =
            entryProvider {
              entry<Nav3Route.SingleStack> { route ->
                TracedNav3Route(route) {
                  Nav3RouteActivationEffect(route, routeActivationAction)
                  SingleStackRoute(backStack)
                }
              }
              entry<Nav3Route.DialogsAndSheets> { route ->
                TracedNav3Route(route) {
                  Nav3RouteActivationEffect(route, routeActivationAction)
                  DialogsAndSheetsRoute(backStack)
                }
              }
              entry<Nav3Route.DeepLink> { route ->
                TracedNav3Route(route) {
                  Nav3RouteActivationEffect(route, routeActivationAction)
                  DeepLinkRoute(backStack)
                }
              }
              entry<Nav3Route.ProductList> { route ->
                TracedNav3Route(route) {
                  Nav3RouteActivationEffect(route, routeActivationAction)
                  ProductListRoute(backStack)
                }
              }
              entry<Nav3Route.ProductDetail> { route ->
                TracedNav3Route(route) {
                  Nav3RouteActivationEffect(route, routeActivationAction)
                  ProductDetailRoute(route, backStack)
                }
              }
              entry<Nav3Route.Checkout> { route ->
                TracedNav3Route(route) {
                  Nav3RouteActivationEffect(route, routeActivationAction)
                  CheckoutRoute(route, backStack)
                }
              }
              entry<Nav3Route.Confirmation> { route ->
                TracedNav3Route(route) {
                  Nav3RouteActivationEffect(route, routeActivationAction)
                  ConfirmationRoute(route, backStack)
                }
              }
              entry<Nav3Route.PromoDialog>(metadata = Nav3DialogSceneStrategy.dialog()) { route ->
                TracedNav3Route(route) {
                  Nav3RouteActivationEffect(route, routeActivationAction)
                  PromoDialogRoute(route, backStack)
                }
              }
              entry<Nav3Route.ShareSheet>(metadata = Nav3BottomSheetSceneStrategy.bottomSheet()) {
                route ->
                TracedNav3Route(route) {
                  Nav3RouteActivationEffect(route, routeActivationAction)
                  ShareSheetRoute(route, backStack)
                }
              }
              entry<Nav3Route.Multipane> { route ->
                TracedNav3Route(route) {
                  Nav3RouteActivationEffect(route, routeActivationAction)
                  FutureRoute(routeName = "Multipane", scenario = "multipane")
                }
              }
              entry<Nav3Route.Multistack> { route ->
                TracedNav3Route(route) {
                  Nav3RouteActivationEffect(route, routeActivationAction)
                  FutureRoute(routeName = "Multiple Stacks", scenario = "multistack")
                }
              }
              entry<Nav3Route.Performance> { route ->
                TracedNav3Route(route) {
                  Nav3RouteActivationEffect(route, routeActivationAction)
                  Nav3PerformanceRoute(
                    route = route,
                    backStack = backStack,
                    performanceState = performanceState,
                    maxCapturedBackStackEntries = maxCapturedBackStackEntries,
                    onMaxCapturedBackStackEntriesChange = {
                      maxCapturedBackStackEntries = it
                    },
                    onApplyPreset = applyPerformancePreset,
                    onRunBenchmark = runPerformanceBenchmark,
                  )
                }
              }
            },
        )
      }
    }
  }

  if (showCrashConfirmation) {
    AlertDialog(
      onDismissRequest = { showCrashConfirmation = false },
      title = { Text("Crash app?") },
      text = { Text("This will throw an uncaught exception and close the sample app.") },
      dismissButton = {
        TextButton(onClick = { showCrashConfirmation = false }) { Text("Cancel") }
      },
      confirmButton = {
        TextButton(
          onClick = {
            showCrashConfirmation = false
            crashSampleApp("Nav3")
          }
        ) {
          Text("Crash")
        }
      },
    )
  }
}

@ExperimentalComposeUiApi
@Composable
private fun TracedNav3Route(route: Nav3Route, content: @Composable BoxScope.() -> Unit) {
  SentryTraced(
    tag = "Nav3 /${route.routeName}",
    enableUserInteractionTracing = false,
    content = content,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Nav3TopBar(
  backStack: List<Nav3Route>,
  maxCapturedBackStackEntries: Int,
  enableNavigationBreadcrumbs: Boolean,
  onEnableNavigationBreadcrumbsChange: (Boolean) -> Unit,
  enableNavigationTransactions: Boolean,
  onEnableNavigationTransactionsChange: (Boolean) -> Unit,
  captureBackStack: Boolean,
  onCaptureBackStackChange: (Boolean) -> Unit,
  onMaxCapturedBackStackEntriesChange: (Int) -> Unit,
) {
  val currentRoute = backStack.lastOrNull() ?: Nav3Route.SingleStack
  val currentRouteArguments = currentRoute.arguments.toDisplayString()
  val currentRouteText =
    if (currentRouteArguments.isEmpty()) {
      "/${currentRoute.routeName}"
    } else {
      "/${currentRoute.routeName} { $currentRouteArguments }"
    }
  val capturedBackStackEntries =
    backStack.takeLast(maxCapturedBackStackEntries).map { route -> "/${route.routeName}" }
  val capturedBackStack =
    capturedBackStackEntries
      .mapIndexed { index, route ->
        if (index == 0 && backStack.size > maxCapturedBackStackEntries) {
          "✂️ $route"
        } else {
          route
        }
      }
      .joinToString(" -> ")

  TopAppBar(
    title = {
      Column {
        Text("Navigation 3")
        Spacer(Modifier.height(12.dp))
        Text(
          text = "Current route: $currentRouteText",
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          maxLines = 1,
        )
        Text(
          text = "Captured back stack: $capturedBackStack",
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          maxLines = 1,
        )
      }
    },
    actions = {
      Nav3SettingsMenu(
        enableNavigationBreadcrumbs = enableNavigationBreadcrumbs,
        onEnableNavigationBreadcrumbsChange = onEnableNavigationBreadcrumbsChange,
        enableNavigationTransactions = enableNavigationTransactions,
        onEnableNavigationTransactionsChange = onEnableNavigationTransactionsChange,
        captureBackStack = captureBackStack,
        onCaptureBackStackChange = onCaptureBackStackChange,
        maxCapturedBackStackEntries = maxCapturedBackStackEntries,
        onMaxCapturedBackStackEntriesChange = onMaxCapturedBackStackEntriesChange,
      )
    },
  )
}

private fun Map<String, Any?>.toDisplayString(): String =
  entries.joinToString(", ") { (key, value) -> "$key=$value" }

@Composable
private fun Nav3SettingsMenu(
  enableNavigationBreadcrumbs: Boolean,
  onEnableNavigationBreadcrumbsChange: (Boolean) -> Unit,
  enableNavigationTransactions: Boolean,
  onEnableNavigationTransactionsChange: (Boolean) -> Unit,
  captureBackStack: Boolean,
  onCaptureBackStackChange: (Boolean) -> Unit,
  maxCapturedBackStackEntries: Int,
  onMaxCapturedBackStackEntriesChange: (Int) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }

  IconButton(onClick = { expanded = true }) {
    Icon(imageVector = Icons.Filled.Settings, contentDescription = "Nav3 settings")
  }

  DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
    SentryNavOptionsMenuItem(
      label = "Navigation breadcrumbs",
      checked = enableNavigationBreadcrumbs,
      onCheckedChange = onEnableNavigationBreadcrumbsChange,
    )
    SentryNavOptionsMenuItem(
      label = "Navigation transactions",
      checked = enableNavigationTransactions,
      onCheckedChange = onEnableNavigationTransactionsChange,
    )
    SentryNavOptionsMenuItem(
      label = "Capture backstack",
      checked = captureBackStack,
      onCheckedChange = onCaptureBackStackChange,
    )

    Column(
      modifier = Modifier.width(280.dp).padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text("Max captured backstack entries")
      Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedButton(
          modifier = Modifier.size(44.dp),
          contentPadding = PaddingValues(0.dp),
          enabled = maxCapturedBackStackEntries > 1,
          onClick = {
            onMaxCapturedBackStackEntriesChange((maxCapturedBackStackEntries - 1).coerceAtLeast(1))
          },
        ) {
          Text("-", style = MaterialTheme.typography.titleLarge)
        }
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          shape = RoundedCornerShape(12.dp),
        ) {
          Text(
            text = "$maxCapturedBackStackEntries",
            modifier = Modifier.width(64.dp).padding(vertical = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
          )
        }
        OutlinedButton(
          modifier = Modifier.size(44.dp),
          contentPadding = PaddingValues(0.dp),
          onClick = { onMaxCapturedBackStackEntriesChange(maxCapturedBackStackEntries + 1) },
        ) {
          Text("+", style = MaterialTheme.typography.titleLarge)
        }
      }
    }
  }
}

@Composable
private fun SentryNavOptionsMenuItem(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  DropdownMenuItem(
    text = { Text(label) },
    onClick = { onCheckedChange(!checked) },
    trailingIcon = { Checkbox(checked = checked, onCheckedChange = onCheckedChange) },
  )
}

@Composable
private fun ScenarioBar(
  selectedScenario: Nav3Scenario,
  onScenarioSelected: (Nav3Scenario) -> Unit,
) {
  val scenarios = Nav3Scenario.entries

  PrimaryScrollableTabRow(
    selectedTabIndex = scenarios.indexOf(selectedScenario),
    edgePadding = 16.dp,
  ) {
    scenarios.forEach { scenario ->
      Tab(
        selected = selectedScenario == scenario,
        onClick = { onScenarioSelected(scenario) },
        text = { Text(scenario.label) },
      )
    }
  }
}

private fun SnapshotStateList<Nav3Route>.openScenario(scenario: Nav3Scenario) {
  when (scenario) {
    Nav3Scenario.SINGLE_STACK -> resetTo(Nav3Route.SingleStack)
    Nav3Scenario.DIALOGS_SHEETS -> resetTo(Nav3Route.DialogsAndSheets)
    Nav3Scenario.DEEP_LINK -> resetTo(Nav3Route.DeepLink)
    Nav3Scenario.MULTIPANE -> resetTo(Nav3Route.Multipane)
    Nav3Scenario.MULTIPLE_STACKS -> resetTo(Nav3Route.Multistack)
    Nav3Scenario.PERFORMANCE -> resetTo(Nav3Route.Performance(index = 0, generation = 0))
  }
}

@Composable
private fun SentryControls(
  selectedAction: RouteActivationAction,
  onActionSelected: (RouteActivationAction) -> Unit,
  onCaptureException: () -> Unit,
  onCrashApp: () -> Unit,
) {
  val sentryPink = colorResource(R.color.colorAccent)

  Surface(shadowElevation = 8.dp) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.Bottom,
    ) {
      Row(
        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
      ) {
        RouteActivationActionDropdown(
          selectedAction = selectedAction,
          sentryPink = sentryPink,
          onActionSelected = onActionSelected,
        )
        Button(onClick = onCaptureException) { Text("Exception") }
        Button(onClick = onCrashApp) { Text("Crash App") }
      }
    }
  }
}

@Composable
private fun RouteActivationActionDropdown(
  selectedAction: RouteActivationAction,
  sentryPink: Color,
  onActionSelected: (RouteActivationAction) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }

  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      "Route work",
      modifier = Modifier.padding(start = 16.dp),
      style = MaterialTheme.typography.bodySmall,
    )
    OutlinedButton(onClick = { expanded = true }) { Text(selectedAction.label) }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      RouteActivationAction.entries.forEach { action ->
        DropdownMenuItem(
          text = {
            Text(
              action.label,
              color = if (action == selectedAction) sentryPink else Color.Unspecified,
            )
          },
          onClick = {
            expanded = false
            onActionSelected(action)
          },
        )
      }
    }
  }
}

@Composable
private fun Nav3RouteActivationEffect(
  route: Nav3Route,
  routeActivationAction: RouteActivationAction,
) {
  val currentAction = rememberUpdatedState(routeActivationAction)

  if (currentAction.value == RouteActivationAction.MANUAL_CHILD_SPAN) {
    // Keep this synchronous to verify that Nav3 route transactions are bound before destination
    // composition runs, not merely before destination effects are launched.
    runManualNav3RouteActivationSpan(route)
    return
  }

  LaunchedEffect(route) {
    runNav3RouteActivationAction(
      route = route,
      action = currentAction.value,
    )
  }
}

private suspend fun runNav3RouteActivationAction(
  route: Nav3Route,
  action: RouteActivationAction,
) {
  if (action == RouteActivationAction.NONE) {
    return
  }

  tagNav3SampleAction(action.tagName, route)
  when (action) {
    RouteActivationAction.NONE -> Unit
    RouteActivationAction.HTTP_REQUEST -> {
      try {
        GithubAPI.service.listReposAsync("getsentry", 5)
      } catch (e: Throwable) {
        Sentry.captureException(e)
      } finally {
        withContext(Dispatchers.IO) { Sentry.flush(SENTRY_FLUSH_TIMEOUT_MILLIS) }
      }
    }
    RouteActivationAction.MANUAL_CHILD_SPAN -> runManualNav3RouteActivationSpan(route)
  }
}

private fun captureSampleException(navName: String) {
  Sentry.captureException(RuntimeException("$navName sample exception button"))
  Thread { Sentry.flush(SENTRY_FLUSH_TIMEOUT_MILLIS) }.start()
}

private fun crashSampleApp(navName: String): Nothing {
  throw RuntimeException("Fatal $navName sample crash button")
}

private fun runManualNav3RouteActivationSpan(route: Nav3Route) {
  val span =
    Sentry.getSpan()
      ?.startChild(
        "test.navigation.route_activation",
        "Nav3 /${route.routeName} route activation",
      )
  span?.setData("sample.route_activation", true)
  span?.finish()
}

@Composable
private fun SingleStackRoute(backStack: SnapshotStateList<Nav3Route>) {
  RouteScaffold(
    title = "Single Stack",
    description =
      "Start a single-backstack product flow, then use the Sentry UI to inspect route " +
        "transactions, breadcrumbs, screen tracking, and captured backstack context.",
  ) {
    RouteButton("Browse Products") { backStack.add(Nav3Route.ProductList) }
  }
}

@Composable
private fun DeepLinkRoute(backStack: SnapshotStateList<Nav3Route>) {
  RouteScaffold(
    title = "Deep Link",
    description =
      "Simulates opening a deep link that builds a synthetic backstack before landing on a detail " +
        "destination.",
  ) {
    RouteButton("Go to deep link destination") { backStack.openSyntheticProductDeepLink() }
  }
}

@Composable
private fun DialogsAndSheetsRoute(backStack: SnapshotStateList<Nav3Route>) {
  RouteScaffold(
    title = "Dialogs & Sheets",
    description =
      "These destinations use Nav3 scene metadata and overlay scene strategies while the Sentry " +
        "controls remain visible in the Activity bottom bar.",
  ) {
    RouteButton("Show Dialog Destination") {
      backStack.add(Nav3Route.PromoDialog(promoId = "summer-sale"))
    }
    RouteButton("Show Bottom Sheet Destination") {
      backStack.add(Nav3Route.ShareSheet(productId = "home"))
    }
  }
}

@Composable
private fun ProductListRoute(backStack: SnapshotStateList<Nav3Route>) {
  RouteScaffold(
    title = "Product List",
    description = "This route starts the single-stack product journey.",
  ) {
    RouteButton("Open Product 42") {
      backStack.add(
        Nav3Route.ProductDetail(productId = "42", source = "product-list", campaign = "summer-sale")
      )
    }
    RouteButton("Open Product 7") {
      backStack.add(Nav3Route.ProductDetail(productId = "7", source = "product-list"))
    }
  }
}

@Composable
private fun ProductDetailRoute(
  route: Nav3Route.ProductDetail,
  backStack: SnapshotStateList<Nav3Route>,
) {
  RouteScaffold(
    title = "Product Detail",
    description =
      "Arguments should appear on navigation breadcrumbs, transaction data, and the navigation " +
        "backstack context.",
  ) {
    RouteInfo("productId", route.productId)
    RouteInfo("source", route.source)
    route.campaign?.let { RouteInfo("campaign", it) }
    RouteButton("Show Promo Dialog") {
      backStack.add(Nav3Route.PromoDialog("detail-${route.productId}"))
    }
    RouteButton("Open Share Sheet") {
      backStack.add(Nav3Route.ShareSheet(route.productId))
    }
    RouteButton("Go to Checkout") { backStack.add(Nav3Route.Checkout(route.productId)) }
  }
}

@Composable
private fun CheckoutRoute(route: Nav3Route.Checkout, backStack: SnapshotStateList<Nav3Route>) {
  RouteScaffold(
    title = "Checkout",
    description = "Continue the same product flow to verify transaction rotation across routes.",
  ) {
    RouteInfo("productId", route.productId)
    RouteButton("Complete Order") {
      backStack.add(Nav3Route.Confirmation(orderId = "order-${route.productId}"))
    }
  }
}

@Composable
private fun ConfirmationRoute(
  route: Nav3Route.Confirmation,
  backStack: SnapshotStateList<Nav3Route>,
) {
  RouteScaffold(
    title = "Confirmation",
    description = "End of the single-stack flow.",
  ) {
    RouteInfo("orderId", route.orderId)
    RouteButton("Reset Backstack") { backStack.resetTo(Nav3Route.SingleStack) }
  }
}

@Composable
private fun PromoDialogRoute(
  route: Nav3Route.PromoDialog,
  backStack: SnapshotStateList<Nav3Route>,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
  ) {
    Column(
      modifier = Modifier.padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text("Promo Dialog", style = MaterialTheme.typography.headlineSmall)
      Text("Dialog route promoId=${route.promoId}")
      Button(onClick = { backStack.removeLastOrNull() }) { Text("Dismiss") }
    }
  }
}

@Composable
private fun ShareSheetRoute(route: Nav3Route.ShareSheet, backStack: SnapshotStateList<Nav3Route>) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("Share Sheet", style = MaterialTheme.typography.headlineSmall)
    Text("Bottom sheet route for productId=${route.productId}")
    Button(onClick = { backStack.removeLastOrNull() }) { Text("Done") }
    Spacer(Modifier.height(12.dp))
  }
}

@Composable
private fun rememberSaveableNav3BackStack(): SnapshotStateList<Nav3Route> {
  return rememberSaveable(saver = nav3BackStackSaver()) {
    mutableStateListOf<Nav3Route>(Nav3Route.SingleStack)
  }
}

private fun nav3BackStackSaver() =
  listSaver<SnapshotStateList<Nav3Route>, Bundle>(
    save = { stack -> stack.map { route -> route.toSavedState() } },
    restore = { savedRoutes ->
      mutableStateListOf<Nav3Route>().apply {
        addAll(savedRoutes.map { savedRoute -> savedRoute.toNav3Route() })
        if (isEmpty()) {
          add(Nav3Route.SingleStack)
        }
      }
    },
  )

private fun Nav3Route.toSavedState(): Bundle =
  Bundle().apply {
    when (this@toSavedState) {
      Nav3Route.SingleStack -> putString("type", "single_stack")
      Nav3Route.DialogsAndSheets -> putString("type", "dialogs_and_sheets")
      Nav3Route.DeepLink -> putString("type", "deep_link")
      Nav3Route.ProductList -> putString("type", "product_list")
      is Nav3Route.ProductDetail -> {
        putString("type", "product_detail")
        putString("product_id", productId)
        putString("source", source)
        putString("campaign", campaign)
      }
      is Nav3Route.Checkout -> {
        putString("type", "checkout")
        putString("product_id", productId)
      }
      is Nav3Route.Confirmation -> {
        putString("type", "confirmation")
        putString("order_id", orderId)
      }
      is Nav3Route.PromoDialog -> {
        putString("type", "promo_dialog")
        putString("promo_id", promoId)
      }
      is Nav3Route.ShareSheet -> {
        putString("type", "share_sheet")
        putString("product_id", productId)
      }
      Nav3Route.Multipane -> putString("type", "multipane")
      Nav3Route.Multistack -> putString("type", "multistack")
      is Nav3Route.Performance -> {
        putString("type", "performance")
        putInt("index", index)
        putInt("generation", generation)
      }
    }
  }

private fun Bundle.toNav3Route(): Nav3Route {
  return when (getString("type")) {
    "single_stack" -> Nav3Route.SingleStack
    "dialogs_and_sheets" -> Nav3Route.DialogsAndSheets
    "deep_link" -> Nav3Route.DeepLink
    "product_list" -> Nav3Route.ProductList
    "product_detail" ->
      Nav3Route.ProductDetail(
        productId = requireNotNull(getString("product_id")),
        source = requireNotNull(getString("source")),
        campaign = getString("campaign"),
      )
    "checkout" -> Nav3Route.Checkout(productId = requireNotNull(getString("product_id")))
    "confirmation" -> Nav3Route.Confirmation(orderId = requireNotNull(getString("order_id")))
    "promo_dialog" -> Nav3Route.PromoDialog(promoId = requireNotNull(getString("promo_id")))
    "share_sheet" -> Nav3Route.ShareSheet(productId = requireNotNull(getString("product_id")))
    "multipane" -> Nav3Route.Multipane
    "multistack" -> Nav3Route.Multistack
    "performance" ->
      Nav3Route.Performance(
        index = getInt("index"),
        generation = getInt("generation"),
      )
    else -> Nav3Route.SingleStack
  }
}

@Composable
private fun FutureRoute(routeName: String, scenario: String) {
  RouteScaffold(
    title = "$routeName: WIP",
    description =
      "Reserved for a future milestone when SentryNavEffect supports $scenario navigation " +
        "state.",
  )
}

@Composable
private fun Nav3PerformanceRoute(
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
        "argument sanitization. Use Perfetto sections prefixed with Nav3Stress to inspect hot paths.",
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

private suspend fun prepareNavigationPerformancePreset(
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

private suspend fun runNavigationPerformanceBenchmark(
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

private suspend fun awaitNavigationPerformanceFrames() {
  withFrameNanos {}
  withFrameNanos {}
}

private const val PERFORMANCE_WARM_UP_ITERATIONS = 5
private const val PERFORMANCE_MEASURED_ITERATIONS = 20
internal const val NAV3_PERFORMANCE_PRESET_EXTRA = "nav3_performance_preset"
internal const val NAV3_PERFORMANCE_RUN_EXTRA = "nav3_performance_run"
internal const val NAV3_PERFORMANCE_WARM_UP_ONLY_EXTRA = "nav3_performance_warm_up_only"
internal const val NAV3_PERFORMANCE_SKIP_WARM_UP_EXTRA = "nav3_performance_skip_warm_up"

private data class NavigationPerformanceRunRequest(
  val id: Int,
  val run: NavigationPerformanceRun,
  val warmUpOnly: Boolean,
  val skipWarmUp: Boolean,
)

@Composable
private fun RouteScaffold(
  title: String,
  description: String,
  content: (@Composable ColumnScope.() -> Unit)? = null,
) {
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text(description, style = MaterialTheme.typography.bodyMedium)
    if (content != null) {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          content()
        }
      }
    }
  }
}

@Composable
private fun RouteButton(label: String, onClick: () -> Unit) {
  Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

@Composable
private fun RouteInfo(label: String, value: String) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
        .padding(12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(label, fontWeight = FontWeight.Bold)
    Spacer(Modifier.size(12.dp))
    Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

private fun SnapshotStateList<Nav3Route>.resetTo(route: Nav3Route) {
  clear()
  add(route)
}

private fun SnapshotStateList<Nav3Route>.openSyntheticProductDeepLink() {
  clear()
  add(Nav3Route.SingleStack)
  add(Nav3Route.ProductList)
  add(Nav3Route.ProductDetail(productId = "42", source = "deep-link", campaign = "email"))
}

private fun SnapshotStateList<Nav3Route>.openPerformanceStack(depth: Int, generation: Int) {
  clear()
  repeat(depth.coerceAtLeast(1)) { index ->
    add(Nav3Route.Performance(index = index, generation = generation))
  }
}

private fun SnapshotStateList<Nav3Route>.mutatePerformanceLowerEntry(generation: Int) {
  if (isEmpty()) {
    add(Nav3Route.Performance(index = 0, generation = generation))
    return
  }

  val index = if (size > 1) 0 else lastIndex
  set(index, Nav3Route.Performance(index = index, generation = generation))
}

private fun SnapshotStateList<Nav3Route>.replacePerformanceTop(generation: Int) {
  if (isEmpty()) {
    add(Nav3Route.Performance(index = 0, generation = generation))
    return
  }

  set(lastIndex, Nav3Route.Performance(index = lastIndex, generation = generation))
}

private fun tagNav3SampleAction(action: String, route: Nav3Route) {
  Sentry.setTag("sample_action", "nav3_$action")
  Sentry.setTag("sample_nav3_route", route.routeName)
}

private const val SENTRY_FLUSH_TIMEOUT_MILLIS = 5000L

private sealed interface Nav3Route {
  val routeName: String
  val arguments: Map<String, Any?>
    get() = emptyMap()

  val previewName: String
    get() = routeName

  val performanceSeed: Int
    get() = hashCode()

  data object SingleStack : Nav3Route {
    override val routeName: String = "SingleStack"
  }

  data object DialogsAndSheets : Nav3Route {
    override val routeName: String = "DialogsAndSheets"
  }

  data object DeepLink : Nav3Route {
    override val routeName: String = "DeepLink"
  }

  data object ProductList : Nav3Route {
    override val routeName: String = "ProductList"
  }

  data class ProductDetail(
    val productId: String,
    val source: String,
    val campaign: String? = null,
  ) : Nav3Route {
    override val routeName: String = "ProductDetail"
    override val arguments: Map<String, Any?> =
      mapOf("product_id" to productId, "source" to source, "campaign" to campaign).filterValues {
        it != null
      }
    override val previewName: String = "ProductDetail($productId)"
  }

  data class Checkout(val productId: String) : Nav3Route {
    override val routeName: String = "Checkout"
    override val arguments: Map<String, Any?> = mapOf("product_id" to productId)
    override val previewName: String = "Checkout($productId)"
  }

  data class Confirmation(val orderId: String) : Nav3Route {
    override val routeName: String = "Confirmation"
    override val arguments: Map<String, Any?> = mapOf("order_id" to orderId)
    override val previewName: String = "Confirmation($orderId)"
  }

  data class PromoDialog(val promoId: String) : Nav3Route {
    override val routeName: String = "PromoDialog"
    override val arguments: Map<String, Any?> = mapOf("promo_id" to promoId)
    override val previewName: String = "PromoDialog($promoId)"
  }

  data class ShareSheet(val productId: String) : Nav3Route {
    override val routeName: String = "ShareSheet"
    override val arguments: Map<String, Any?> = mapOf("product_id" to productId)
    override val previewName: String = "ShareSheet($productId)"
  }

  data object Multipane : Nav3Route {
    override val routeName: String = "Multipane"
    override val arguments: Map<String, Any?> = mapOf("scenario" to "multipane")
  }

  data object Multistack : Nav3Route {
    override val routeName: String = "Multistack"
    override val arguments: Map<String, Any?> = mapOf("scenario" to "multistack")
  }

  data class Performance(val index: Int, val generation: Int) : Nav3Route {
    override val routeName: String = "Performance"
    override val arguments: Map<String, Any?> = mapOf("index" to index, "generation" to generation)
    override val previewName: String = "Performance($index:$generation)"
    override val performanceSeed: Int = 31 * index + generation
  }
}

private enum class Nav3Scenario(val label: String) {
  SINGLE_STACK("Single Stack"),
  DIALOGS_SHEETS("Dialogs & Sheets"),
  DEEP_LINK("Deep Link"),
  MULTIPANE("Multipane"),
  MULTIPLE_STACKS("Multistack"),
  PERFORMANCE("Performance"),
}
