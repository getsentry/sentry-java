package io.sentry.samples.android.navigation.nav3

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Trace
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.ui.NavDisplay
import io.sentry.compose.navigation3.SentryNavEffect
import io.sentry.compose.navigation3.SentryNavOptions
import io.sentry.samples.android.navigation.NavigationSampleConfig
import io.sentry.samples.android.navigation.NavigationSampleConfigSnapshot
import io.sentry.samples.android.navigation.applyToCurrentOptions
import io.sentry.samples.android.navigation.common.NavigationPerformanceIntegrationMode
import io.sentry.samples.android.navigation.common.NavigationPerformancePreset
import io.sentry.samples.android.navigation.common.NavigationPerformanceRun
import io.sentry.samples.android.navigation.common.NavigationPerformanceState
import io.sentry.samples.android.navigation.common.NavigationSampleTheme
import io.sentry.samples.android.navigation.common.NavigationTransactionHistory
import io.sentry.samples.android.navigation.common.NavigationTransactionHistorySheet
import io.sentry.samples.android.navigation.common.NavigationTransactionTrace
import io.sentry.samples.android.navigation.common.RouteWorkOption
import io.sentry.samples.android.navigation.common.cancelCurrentActivityUiLoadTransaction
import io.sentry.samples.android.navigation.common.consumeNavigationPerformanceExtractorWork
import io.sentry.samples.android.navigation.common.navigationPerformanceArguments
import io.sentry.samples.android.navigation.common.showRouteWorkDialog
import io.sentry.samples.android.navigation.common.tagCurrentNavigationSampleScenario
import io.sentry.samples.android.navigation.currentNavigationSampleConfigSnapshot
import io.sentry.samples.android.navigation.hasOnlyActivityUiLoadTransactions
import io.sentry.samples.android.navigation.nav3SampleConfig
import io.sentry.samples.android.navigation.previousNav3SampleConfigSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Sample app Activity for testing Sentry's
 * [Nav3](https://developer.android.com/guide/navigation/navigation-3) integrations.
 *
 * Look at Google's [nav3-recipes](https://github.com/android/nav3-recipes) for helpful patterns to
 * test against. (This Activity doesn't address all of them yet, so update its implementation as
 * needed.)
 */
class Nav3Activity : ComponentActivity() {

  private lateinit var previousConfig: NavigationSampleConfigSnapshot
  private val performanceState = NavigationPerformanceState(measureRenderLatency = true)
  private var performanceRunRequest by mutableStateOf<NavigationPerformanceRunRequest?>(null)
  private var nextPerformanceRunRequestId = 0
  private var isTransactionHistoryActive = false
  private val transactionHistory =
    NavigationTransactionHistory(isActive = { isTransactionHistoryActive })
  private var showActivityUiLoadTransactionDelayMessage = false
  private var routeWorkOptions by
    mutableStateOf(setOf(RouteWorkOption.HTTP_REQUEST, RouteWorkOption.MANUAL_CHILD_SPAN))
  private var showTransactionHistorySheet by mutableStateOf(false)
  private var showCrashConfirmation by mutableStateOf(false)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    previousConfig =
      intent.previousNav3SampleConfigSnapshot(currentNavigationSampleConfigSnapshot())

    val configuration = intent.nav3SampleConfig()
    configuration.applyToCurrentOptions()
    showActivityUiLoadTransactionDelayMessage = configuration.hasOnlyActivityUiLoadTransactions

    if (!configuration.enableActivityUiLoadTransaction) {
      // Cancel the already-started Activity transaction before Compose installs SentryNavEffect,
      // otherwise the initial /Landing route transaction gets preempted by the lingering ui.load.
      cancelCurrentActivityUiLoadTransaction()
    }

    transactionHistory.install()

    val initialPerformancePreset =
      intent.getStringExtra(NAV3_PERFORMANCE_PRESET_EXTRA)?.let { presetName ->
        NavigationPerformancePreset.entries.firstOrNull { it.name == presetName }
      }
    val initialPerformanceRun =
      intent.getStringExtra(NAV3_PERFORMANCE_RUN_EXTRA)?.let { runName ->
        NavigationPerformanceRun.entries.firstOrNull { it.name == runName }
      }
    setContent {
      NavigationSampleTheme {
        Nav3SampleApp(
          performanceState = performanceState,
          initialPerformancePreset = initialPerformancePreset,
          initialPerformanceRun = initialPerformanceRun,
          performanceRunRequest = performanceRunRequest,
          configuration = configuration,
          transactions = transactionHistory.transactions,
          showActivityUiLoadTransactionDelayMessage = showActivityUiLoadTransactionDelayMessage,
          routeWorkOptions = routeWorkOptions,
          showTransactionHistorySheet = showTransactionHistorySheet,
          showCrashConfirmation = showCrashConfirmation,
          onShowTransactionHistorySheet = { showTransactionHistorySheet = true },
          onDismissTransactionHistorySheet = { showTransactionHistorySheet = false },
          onShowRouteWorkSettings = { showRouteWorkSettings() },
          onShowCrashConfirmation = { showCrashConfirmation = true },
          onDismissCrashConfirmation = { showCrashConfirmation = false },
          onOpenTransaction = { url -> openTransactionInSentry(url) },
          onDumpTransactionUrl = { url -> dumpTransactionUrl(url) },
          onCopyTransactionUrl = { url -> copyTransactionUrl(url) },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
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

  override fun onStart() {
    super.onStart()
    isTransactionHistoryActive = true
  }

  override fun onStop() {
    isTransactionHistoryActive = false
    performanceState.stopAutomaticWork()
    super.onStop()
  }

  override fun onDestroy() {
    if (isFinishing) {
      previousConfig.applyToCurrentOptions()
    }
    transactionHistory.uninstall()
    super.onDestroy()
  }

  private fun openTransactionInSentry(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
  }

  private fun dumpTransactionUrl(url: String) {
    Log.i(NAV3_TAG, "Sentry transaction URL: $url")
    Toast.makeText(this, "Dumped transaction URL to logcat.", Toast.LENGTH_SHORT).show()
  }

  private fun copyTransactionUrl(url: String) {
    val clipboard = getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Sentry transaction URL", url))
    Toast.makeText(this, "Copied transaction URL to clipboard.", Toast.LENGTH_SHORT).show()
  }

  private fun showRouteWorkSettings() {
    showRouteWorkDialog(this, routeWorkOptions) { selectedOptions ->
      routeWorkOptions = selectedOptions
    }
  }
}

@SuppressLint("ContextCastToActivity")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Nav3SampleApp(
  performanceState: NavigationPerformanceState,
  initialPerformancePreset: NavigationPerformancePreset? = null,
  initialPerformanceRun: NavigationPerformanceRun? = null,
  performanceRunRequest: NavigationPerformanceRunRequest? = null,
  configuration: NavigationSampleConfig,
  transactions: List<NavigationTransactionTrace>,
  showActivityUiLoadTransactionDelayMessage: Boolean,
  routeWorkOptions: Set<RouteWorkOption>,
  showTransactionHistorySheet: Boolean,
  showCrashConfirmation: Boolean,
  onShowTransactionHistorySheet: () -> Unit,
  onDismissTransactionHistorySheet: () -> Unit,
  onShowRouteWorkSettings: () -> Unit,
  onShowCrashConfirmation: () -> Unit,
  onDismissCrashConfirmation: () -> Unit,
  onOpenTransaction: (String) -> Unit,
  onDumpTransactionUrl: (String) -> Unit,
  onCopyTransactionUrl: (String) -> Unit,
) {
  val activity = LocalContext.current as? ComponentActivity
  val initialScenario =
    if (configuration.enableActivityUiLoadTransaction) {
      Nav3Scenario.SINGLE_STACK
    } else {
      Nav3Scenario.LANDING
    }
  val backStack = rememberSaveableNav3BackStack(initialScenario.initialRoute)
  val dialogSceneStrategy = remember { Nav3DialogSceneStrategy<Nav3Route>() }
  val bottomSheetSceneStrategy = remember { Nav3BottomSheetSceneStrategy<Nav3Route>() }

  var enableNavigationBreadcrumbs by remember {
    mutableStateOf(configuration.enableNavigationBreadcrumbs)
  }
  var enableNavigationTransactions by remember {
    mutableStateOf(configuration.enableNavigationTransactions)
  }
  var captureBackStack by remember { mutableStateOf(configuration.captureBackStack) }
  var maxCapturedBackStackEntries by remember {
    mutableIntStateOf(configuration.maxCapturedBackStackEntries)
  }
  var selectedScenario by rememberSaveable { mutableStateOf(initialScenario) }
  var customTransactionMode by rememberSaveable {
    mutableStateOf(Nav3CustomTransactionMode.PER_SCREEN)
  }
  var asyncBrowseProductsJob by remember { mutableStateOf<Job?>(null) }
  var isAsyncBrowseProductsRunning by remember { mutableStateOf(false) }
  val performanceScope = rememberCoroutineScope()
  val customTransactionsScope = rememberCoroutineScope()
  val customTransactionController = remember { Nav3CustomTransactionController() }

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
    Nav3CustomTransactionEffect(
      selectedScenario = selectedScenario,
      mode = customTransactionMode,
      backStack = backStack,
      controller = customTransactionController,
    )

    val nameExtractorCallsBefore = performanceState.nameExtractorCalls
    val argumentsExtractorCallsBefore = performanceState.argumentsExtractorCalls
    val extractorNanosBefore =
      performanceState.nameExtractorNanos + performanceState.argumentsExtractorNanos
    val startedAtNanos = System.nanoTime()
    if (isPerformanceScenario) {
      Trace.beginSection(performanceState.sentryNavEffectTraceSection())
    }
    SentryNavEffect(
      backStack = sentryBackStack,
      options = sentryNavOptions,
      nameExtractor = nameExtractor,
      argumentsExtractor = argumentsExtractor,
    )
    if (isPerformanceScenario) {
      Trace.endSection()
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

  LaunchedEffect(selectedScenario, sentryBackStack.lastOrNull()) {
    tagCurrentNavigationSampleScenario(selectedScenario.label)
  }

  LaunchedEffect(selectedScenario, customTransactionMode) {
    if (
      selectedScenario != Nav3Scenario.CUSTOM ||
        customTransactionMode != Nav3CustomTransactionMode.ASYNC_FROM_USER_ACTION
    ) {
      asyncBrowseProductsJob?.cancel()
      asyncBrowseProductsJob = null
      isAsyncBrowseProductsRunning = false
    }
  }

  Scaffold(
    modifier = Modifier.fillMaxSize().safeDrawingPadding(),
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      Nav3TopBar(
        backStack = backStack,
        selectedScenario = selectedScenario,
        maxCapturedBackStackEntries = maxCapturedBackStackEntries,
        onTransactionHistoryClick = onShowTransactionHistorySheet,
        onRouteWorkSettingsClick = onShowRouteWorkSettings,
        onScenarioSelected = { scenario ->
          if (scenario != Nav3Scenario.PERFORMANCE && performanceState.benchmarkRunning) {
            performanceState.cancelBenchmark()
          }
          selectedScenario = scenario
          performanceState.stopAutomaticWork()
          backStack.openScenario(scenario)
          if (scenario == Nav3Scenario.PERFORMANCE) {
            performanceState.resetCounters()
            performanceState.markNavigationMutation()
          }
        },
      )
    },
    bottomBar = {
      SentryControls(
        onCaptureException = { captureSampleException("Nav3") },
        onCrashApp = onShowCrashConfirmation,
      )
    },
  ) { innerPadding ->
    Box(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .background(MaterialTheme.colorScheme.background)
    ) {
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
          androidx.navigation3.runtime.entryProvider {
            entry<Nav3Route.SingleStack> { route ->
              TracedNav3Route(route, selectedScenario) {
                Nav3RouteWorkEffect(route, routeWorkOptions)
                SingleStackRoute(backStack)
              }
            }
            entry<Nav3Route.Custom> { route ->
              TracedNav3Route(route, selectedScenario) {
                Nav3RouteWorkEffect(route, routeWorkOptions)
                CustomRoute(
                  mode = customTransactionMode,
                  onModeSelected = { customTransactionMode = it },
                  isAsyncBrowseProductsRunning = isAsyncBrowseProductsRunning,
                  onBrowseProducts = {
                    if (customTransactionMode == Nav3CustomTransactionMode.ASYNC_FROM_USER_ACTION) {
                      if (!isAsyncBrowseProductsRunning) {
                        customTransactionController.startAsyncBrowseProductsTransaction()
                        isAsyncBrowseProductsRunning = true
                        asyncBrowseProductsJob = customTransactionsScope.launch {
                          val span =
                            io.sentry.Sentry.getSpan()
                              ?.startChild(
                                "test.navigation.async_browse_products",
                                "Nav3 Custom async browse products",
                              )
                          try {
                            delay(450)
                            backStack.add(Nav3Route.ProductList)
                          } finally {
                            span?.finish()
                            isAsyncBrowseProductsRunning = false
                            asyncBrowseProductsJob = null
                          }
                        }
                      }
                    } else {
                      backStack.add(Nav3Route.ProductList)
                    }
                  },
                )
              }
            }
            entry<Nav3Route.Landing> { route ->
              TracedNav3Route(route, selectedScenario) {
                Nav3RouteWorkEffect(route, routeWorkOptions)
                LandingRoute()
              }
            }
            entry<Nav3Route.DeepLink> { route ->
              TracedNav3Route(route, selectedScenario) {
                Nav3RouteWorkEffect(route, routeWorkOptions)
                DeepLinkRoute(backStack)
              }
            }
            entry<Nav3Route.ProductList> { route ->
              TracedNav3Route(route, selectedScenario) {
                Nav3RouteWorkEffect(route, routeWorkOptions)
                ProductListRoute(backStack)
              }
            }
            entry<Nav3Route.ProductDetail> { route ->
              TracedNav3Route(route, selectedScenario) {
                Nav3RouteWorkEffect(route, routeWorkOptions)
                ProductDetailRoute(route, backStack)
              }
            }
            entry<Nav3Route.Checkout> { route ->
              TracedNav3Route(route, selectedScenario) {
                Nav3RouteWorkEffect(route, routeWorkOptions)
                CheckoutRoute(route, backStack)
              }
            }
            entry<Nav3Route.Confirmation> { route ->
              TracedNav3Route(route, selectedScenario) {
                Nav3RouteWorkEffect(route, routeWorkOptions)
                ConfirmationRoute(
                  route = route,
                  backStack = backStack,
                  rootRoute =
                    if (selectedScenario == Nav3Scenario.CUSTOM) {
                      Nav3Route.Custom
                    } else {
                      Nav3Route.SingleStack
                    },
                )
              }
            }

            entry<Nav3Route.PromoDialog>(metadata = Nav3DialogSceneStrategy.dialog()) { route ->
              TracedNav3Route(route, selectedScenario) {
                Nav3RouteWorkEffect(route, routeWorkOptions)
                PromoDialogRoute(
                  route = route,
                  backStack = backStack,
                  onCaptureException = { captureSampleException("Nav3") },
                  onCrashApp = onShowCrashConfirmation,
                )
              }
            }
            entry<Nav3Route.ShareSheet>(metadata = Nav3BottomSheetSceneStrategy.bottomSheet()) {
              route ->
              TracedNav3Route(route, selectedScenario) {
                Nav3RouteWorkEffect(route, routeWorkOptions)
                ShareSheetRoute(
                  route = route,
                  backStack = backStack,
                  onCaptureException = { captureSampleException("Nav3") },
                  onCrashApp = onShowCrashConfirmation,
                )
              }
            }

            entry<Nav3Route.Multipane> { route ->
              TracedNav3Route(route, selectedScenario) {
                Nav3RouteWorkEffect(route, routeWorkOptions)
                FutureRoute(routeName = "Multipane", scenario = "multipane")
              }
            }
            entry<Nav3Route.Multistack> { route ->
              TracedNav3Route(route, selectedScenario) {
                Nav3RouteWorkEffect(route, routeWorkOptions)
                FutureRoute(routeName = "Multiple Stacks", scenario = "multistack")
              }
            }
            entry<Nav3Route.Performance> { route ->
              TracedNav3Route(route, selectedScenario) {
                Nav3RouteWorkEffect(route, routeWorkOptions)
                Nav3PerformanceRoute(
                  route = route,
                  backStack = backStack,
                  performanceState = performanceState,
                  maxCapturedBackStackEntries = maxCapturedBackStackEntries,
                  onMaxCapturedBackStackEntriesChange = { maxCapturedBackStackEntries = it },
                  onApplyPreset = applyPerformancePreset,
                  onRunBenchmark = runPerformanceBenchmark,
                )
              }
            }
          },
      )
    }
  }

  if (showTransactionHistorySheet) {
    NavigationTransactionHistorySheet(
      sampleName = "Nav3",
      transactions = transactions,
      showActivityUiLoadTransactionDelayMessage = showActivityUiLoadTransactionDelayMessage,
      onDismissRequest = onDismissTransactionHistorySheet,
      onOpenTransaction = onOpenTransaction,
      onDumpTransactionUrl = onDumpTransactionUrl,
      onCopyTransactionUrl = onCopyTransactionUrl,
    )
  }

  if (showCrashConfirmation) {
    AlertDialog(
      onDismissRequest = onDismissCrashConfirmation,
      title = { Text("Crash app?") },
      text = { Text("This will throw an uncaught exception and close the sample app.") },
      dismissButton = {
        TextButton(onClick = onDismissCrashConfirmation) { Text("Cancel") }
      },
      confirmButton = {
        TextButton(
          onClick = {
            onDismissCrashConfirmation()
            crashSampleApp("Nav3")
          }
        ) {
          Text("Crash")
        }
      },
    )
  }
}

private const val NAV3_TAG = "Nav3Activity"
