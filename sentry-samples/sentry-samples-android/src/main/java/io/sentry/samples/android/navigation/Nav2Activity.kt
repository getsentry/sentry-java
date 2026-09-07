package io.sentry.samples.android.navigation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.setPadding
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import io.sentry.Sentry
import io.sentry.SpanStatus
import io.sentry.android.navigation.SentryNavigationListener
import io.sentry.samples.android.GithubAPI
import io.sentry.samples.android.R
import io.sentry.samples.android.Repo
import io.sentry.samples.android.navigation.Nav2Destination.Home
import io.sentry.samples.android.navigation.Nav2Destination.Landing
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Sample activity for testing Sentry's
 * [NavController-based](https://developer.android.com/guide/navigation) integrations (aka, "Nav2").
 *
 * Exercises [SentryNavigationListener] both directly via fragment navigation ("Fragments" and "Deep
 * Link (Fragments)" tabs) and through our composable [withSentryObservableEffect] wrapper
 * ("Compose" tab).
 *
 * Developers can also stress test our Nav2 integration via the "Performance" tab.
 */
class Nav2Activity : AppCompatActivity() {

  private lateinit var navController: NavController

  /**
   * Sample-only mirror of [NavController] state used to display the current stack, drive deep-link
   * helpers, and keep shared destinations attributed to the active scenario.
   */
  private val backStack = mutableListOf<Nav2Destination>(Home)

  private lateinit var sentryNavigationListener: SentryNavigationListener
  private val enableNavigationBreadcrumbs = mutableStateOf(true)
  private val enableNavigationTransactions = mutableStateOf(true)

  private lateinit var previousConfig: Nav2SampleConfigSnapshot

  // Top bar config
  private val routeWorkOptions =
    mutableStateOf(setOf(RouteWorkOption.HTTP_REQUEST, RouteWorkOption.MANUAL_CHILD_SPAN))
  private lateinit var topBar: Nav2TopBar
  private var activeScenario = Nav2Scenario.COMPOSE

  // Main content
  private lateinit var contentHosts: Nav2ContentHosts
  private val performanceState = NavigationPerformanceState()

  // Transaction history bottom sheet
  private var isTransactionHistoryActive = false
  private val transactionHistory = Nav2TransactionHistory(isActive = { isTransactionHistoryActive })
  private val showTransactionHistorySheet = mutableStateOf(false)
  private var showActivityUiLoadTransactionDelayMessage = false
  private lateinit var transactionHistoryOverlay: ComposeView
  private val mainHandler = Handler(Looper.getMainLooper())

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    previousConfig = intent.previousNav2SampleConfigSnapshot(currentNav2SampleConfigSnapshot())

    val configuration = intent.nav2SampleConfig()
    configuration.applyToCurrentOptions()
    enableNavigationBreadcrumbs.value = configuration.enableNavigationBreadcrumbs
    enableNavigationTransactions.value = configuration.enableNavigationTransactions
    showActivityUiLoadTransactionDelayMessage = configuration.hasOnlyActivityUiLoadTransactions

    transactionHistory.install()

    activeScenario =
      if (configuration.enableActivityUiLoadTransaction) {
        Nav2Scenario.COMPOSE
      } else {
        Nav2Scenario.LANDING
      }

    sentryNavigationListener = createSentryNavigationListener()

    val navHostId = View.generateViewId()
    setContentView(createContentView(navHostId))

    val navHostFragment = NavHostFragment.create(R.navigation.nav2_sample)
    supportFragmentManager
      .beginTransaction()
      .replace(navHostId, navHostFragment)
      .setPrimaryNavigationFragment(navHostFragment)
      .commitNow()

    navController = navHostFragment.navController

    if (!configuration.enableActivityUiLoadTransaction) {
      val graph = navController.navInflater.inflate(R.navigation.nav2_sample)
      graph.setStartDestination(R.id.nav2_landing)
      navController.graph = graph
      backStack.resetTo(Landing)
    }

    navController.addOnDestinationChangedListener(sentryNavigationListener)
    navController.addOnDestinationChangedListener { _, destination, arguments ->
      updateNavigationUi(destination, arguments)
    }

    openScenario(activeScenario)
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
    mainHandler.removeCallbacksAndMessages(null)
    transactionHistory.uninstall()
    super.onDestroy()
  }

  internal fun navigateTo(destination: Nav2Destination) {
    backStack.add(destination)
    try {
      navController.navigate(destination.id, destination.arguments)
    } catch (e: IllegalArgumentException) {
      backStack.removeAt(backStack.lastIndex)
      throw e
    } catch (e: IllegalStateException) {
      backStack.removeAt(backStack.lastIndex)
      throw e
    }
  }

  internal fun navigateBack() {
    backStack.popTrackedBackStack { navController.popBackStack() }
  }

  internal fun resetToHome() {
    backStack.resetTo(Home)
    if (!navController.popBackStack(R.id.nav2_home, false)) {
      navController.setGraph(R.navigation.nav2_sample)
    }
  }

  internal fun openSyntheticProductDeepLink() {
    resetToHome()
    navigateTo(Nav2Destination.ProductList)
    navigateTo(Nav2Destination.ProductDetail("42", "deep-link", "email"))
  }

  private fun createSentryNavigationListener(): SentryNavigationListener {
    return SentryNavigationListener(
      enableNavigationBreadcrumbs = enableNavigationBreadcrumbs.value,
      enableNavigationTracing = enableNavigationTransactions.value,
    )
  }

  private fun createComposeNavHostView(): ComposeView =
    ComposeView(this).apply {
      setBackgroundColor(color(android.R.color.white))
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

      setContent {
        MaterialTheme {
          Nav2ComposeApp(
            navListener = sentryNavigationListener,
            routeWorkOptions = routeWorkOptions.value,
            onCaptureException = { captureSampleException("Nav2") },
            onCrashApp = { showCrashConfirmation("Nav2") },
            onRouteChanged = { _, currentRoute, backStack ->
              updateComposeNavigationUi(currentRoute, backStack)
            },
          )
        }
      }
    }

  private fun createContentView(navHostId: Int): View {
    val root = FrameLayout(this).apply { layoutParams = matchParentParams() }
    val mainContent =
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = matchParentParams()
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
          val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
          view.setPadding(0, systemBars.top, 0, systemBars.bottom)
          insets
        }
      }

    topBar =
      Nav2TopBar(
        context = this,
        onTransactionHistoryClick = { showTransactionHistorySheet() },
        onRouteWorkSettingsClick = { showRouteWorkDialog() },
        onScenarioClick = { scenario -> openScenario(scenario) },
      )
    contentHosts =
      Nav2ContentHosts(
        context = this,
        navHostId = navHostId,
        createComposeContent = { createComposeNavHostView() },
        createPerformanceContent = { createPerformanceView() },
      )

    mainContent.addView(topBar.view)
    mainContent.addView(contentHosts.view)
    mainContent.addView(createBottomBar())

    root.addView(mainContent, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    root.addView(
      createTransactionHistoryOverlay(),
      FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT),
    )

    return root
  }

  private fun createPerformanceView(): ComposeView =
    ComposeView(this).apply {
      setBackgroundColor(color(android.R.color.white))
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

      setContent {
        MaterialTheme {
          NavigationPerformancePanel(
            title = "Performance",
            description =
              "Stress the Nav2 listener path with deep fragment back stacks, destination changes, " +
                "and unrelated Compose recompositions. Use Perfetto sections prefixed with " +
                "Nav2Stress to inspect hot paths.",
            currentRoute = "/${backStack.lastOrNull()?.routeName ?: Nav2RouteNames.HOME}",
            backStack =
              navigationPerformanceBackStackPreview(
                backStack.map { destination -> "/${destination.routeName}" }
              ),
            state = performanceState,
            onBuildStack = { buildNav2PerformanceStack() },
            onReplaceTop = { replaceNav2PerformanceTop() },
          )
        }
      }
    }

  private fun createTransactionHistoryOverlay(): ComposeView =
    ComposeView(this).apply {
      transactionHistoryOverlay = this
      visibility = View.GONE
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

      setContent {
        MaterialTheme {
          if (showTransactionHistorySheet.value) {
            Nav2TransactionHistorySheet(
              transactions = transactionHistory.transactions,
              showActivityUiLoadTransactionDelayMessage = showActivityUiLoadTransactionDelayMessage,
              onDismissRequest = { hideTransactionHistorySheet() },
              onOpenTransaction = { url -> openTransactionInSentry(url) },
              onDumpTransactionUrl = { url -> dumpTransactionUrl(url) },
              onCopyTransactionUrl = { url -> copyTransactionUrl(url) },
            )
          }
        }
      }
    }

  private fun createBottomBar(): View {
    return LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER
      setPadding(12.dp)
      addView(
        LinearLayout(context).apply {
          orientation = LinearLayout.HORIZONTAL
          gravity = Gravity.CENTER
          layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
          addView(
            sentryEventButton("Capture Exception", R.id.nav2_capture_exception) {
              captureSampleException("Nav2")
            }
          )
          addView(
            sentryEventButton("Crash App", R.id.nav2_crash_app) { showCrashConfirmation("Nav2") }
          )
        }
      )
    }
  }

  private fun openScenario(scenario: Nav2Scenario) {
    activeScenario = scenario
    topBar.select(activeScenario)
    performanceState.stopAutomaticWork()
    when (scenario) {
      Nav2Scenario.LANDING -> {
        contentHosts.showFragments()
        updateNavigationUi(
          navController.currentDestination,
          navController.currentBackStackEntry?.arguments,
        )
      }
      Nav2Scenario.COMPOSE -> contentHosts.showCompose()
      Nav2Scenario.FRAGMENTS -> {
        contentHosts.showFragments()
        resetToHome()
        updateNavigationUi(
          navController.currentDestination,
          navController.currentBackStackEntry?.arguments,
        )
      }
      Nav2Scenario.DEEP_LINK -> {
        contentHosts.showFragments()
        resetToDestination(Nav2Destination.DeepLink)
        updateNavigationUi(
          navController.currentDestination,
          navController.currentBackStackEntry?.arguments,
        )
      }
      Nav2Scenario.PERFORMANCE -> {
        performanceState.resetCounters()
        contentHosts.showPerformance()
        updatePerformanceTopBar()
      }
    }
  }

  internal fun runRouteWorkAction(routeName: String) {
    RouteWorkOption.entries.forEach { option ->
      if (option !in routeWorkOptions.value) {
        return@forEach
      }

      tagNav2SampleAction(option.tagName, routeName)

      when (option) {
        RouteWorkOption.HTTP_REQUEST -> {
          GithubAPI.service
            .listRepos("getsentry")
            .enqueue(
              object : Callback<List<Repo>> {
                override fun onResponse(call: Call<List<Repo>>, response: Response<List<Repo>>) {
                  Thread { Sentry.flush(SENTRY_FLUSH_TIMEOUT_MILLIS) }.start()
                }

                override fun onFailure(call: Call<List<Repo>>, t: Throwable) {
                  Sentry.captureException(t)
                  Thread { Sentry.flush(SENTRY_FLUSH_TIMEOUT_MILLIS) }.start()
                }
              }
            )
        }

        RouteWorkOption.MANUAL_CHILD_SPAN -> recordManualChildSpan(routeName)
      }
    }
  }

  private fun updatePerformanceTopBar() {
    topBar.update(
      scenario = Nav2Scenario.PERFORMANCE,
      currentRoute = "/${backStack.lastOrNull()?.routeName ?: Nav2RouteNames.HOME}",
      backStack =
        navigationPerformanceBackStackPreview(
          backStack.map { destination -> "/${destination.routeName}" }
        ),
    )
  }

  private fun buildNav2PerformanceStack() {
    traceNavigationPerformanceSection("Nav2Stress.buildStack") {
      val generation = performanceState.nextGeneration()
      resetToHome()
      for (index in 1 until performanceState.stackDepth.coerceAtLeast(1)) {
        navigateTo(nav2PerformanceDestination(index, generation))
      }
      performanceState.markNavigationMutation()
    }
  }

  private fun replaceNav2PerformanceTop() {
    traceNavigationPerformanceSection("Nav2Stress.replaceTop") {
      val generation = performanceState.nextGeneration()
      if (backStack.size > 1) {
        navigateBack()
      }
      navigateTo(nav2PerformanceDestination(backStack.size, generation))
      performanceState.markNavigationMutation()
    }
  }

  private fun nav2PerformanceDestination(index: Int, generation: Int): Nav2Destination =
    when (index % 4) {
      0 -> Nav2Destination.ProductList
      1 ->
        Nav2Destination.ProductDetail(
          productId = "perf-$index",
          source = "performance",
          campaign = "generation-$generation",
        )
      2 -> Nav2Destination.Checkout(productId = "perf-$index")
      else -> Nav2Destination.Confirmation(orderId = "perf-$generation-$index")
    }

  private fun resetToDestination(destination: Nav2Destination) {
    backStack.resetTo(destination)

    val isStartingFromLanding = navController.currentDestination?.id == R.id.nav2_landing
    val popUpToDestination = if (isStartingFromLanding) R.id.nav2_landing else R.id.nav2_home

    navController.navigate(
      destination.id,
      destination.arguments,
      NavOptions.Builder()
        .setPopUpTo(popUpToDestination, isStartingFromLanding)
        .setLaunchSingleTop(true)
        .build(),
    )
  }

  private fun updateNavigationUi(destination: NavDestination?, arguments: Bundle?) {
    syncTrackedBackStack(destination, arguments)

    val trackedDestination = backStack.lastOrNull()
    val routeName = trackedDestination?.routeName ?: destination?.routeName() ?: Nav2RouteNames.HOME
    val currentRoute =
      trackedDestination?.displayRoute() ?: Nav2RouteSpecs.get(routeName).displayRoute(arguments)

    val scenario = fragmentTopBarScenario(trackedDestination)
    topBar.update(
      scenario = scenario,
      currentRoute = currentRoute,
      backStack = navControllerBackStack(),
    )

    if (activeScenario == Nav2Scenario.PERFORMANCE) {
      performanceState.markDestinationChange()
    }
  }

  private fun updateComposeNavigationUi(currentRoute: String, backStack: String) {
    topBar.update(
      scenario = Nav2Scenario.COMPOSE,
      currentRoute = currentRoute,
      backStack = backStack,
    )
  }

  private fun navControllerBackStack(): String {
    return backStack.joinToString(" -> ") { destination -> "/${destination.routeName}" }
  }

  private fun fragmentTopBarScenario(destination: Nav2Destination?): Nav2Scenario {
    if (activeScenario == Nav2Scenario.PERFORMANCE) {
      return Nav2Scenario.PERFORMANCE
    }

    return when (destination) {
      Nav2Destination.Landing -> Nav2Scenario.LANDING
      Nav2Destination.DeepLink -> Nav2Scenario.DEEP_LINK
      is Nav2Destination.ProductDetail -> {
        if (destination.source == "deep-link") {
          Nav2Scenario.DEEP_LINK
        } else {
          Nav2Scenario.FRAGMENTS
        }
      }
      is Nav2Destination.PromoDialog -> destination.scenario
      is Nav2Destination.ShareSheet -> destination.scenario
      else -> Nav2Scenario.FRAGMENTS
    }
  }

  private fun syncTrackedBackStack(destination: NavDestination?, arguments: Bundle?) {
    if (destination == null || backStack.lastOrNull()?.matches(destination, arguments) == true) {
      return
    }

    val destinationIndex = backStack.indexOfLast { trackedDestination ->
      trackedDestination.matches(destination, arguments)
    }
    if (destinationIndex >= 0) {
      backStack.subList(destinationIndex + 1, backStack.size).clear()
      return
    }

    destination.toNav2Destination(arguments)?.let { backStack.resetTo(it) }
  }

  internal fun cancelCurrentUiLoadTransaction() {
    Sentry.configureScope { scope ->
      scope.withTransaction { transaction ->
        if (transaction?.operation == ACTIVITY_UI_LOAD_OP) {
          transaction.forceFinish(SpanStatus.CANCELLED, false, null)
          scope.clearTransaction()
        }
      }
    }
  }

  internal fun tagCurrentScenarioOnTransaction() {
    tagCurrentNav2Scenario(activeScenario)
  }

  private fun showTransactionHistorySheet() {
    transactionHistoryOverlay.visibility = View.VISIBLE
    showTransactionHistorySheet.value = true
  }

  private fun hideTransactionHistorySheet() {
    showTransactionHistorySheet.value = false
    mainHandler.postDelayed(
      {
        if (!showTransactionHistorySheet.value) {
          transactionHistoryOverlay.visibility = View.GONE
        }
      },
      BOTTOM_SHEET_HIDE_DELAY_MILLIS,
    )
  }

  private fun openTransactionInSentry(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
  }

  private fun dumpTransactionUrl(url: String) {
    Log.i(TAG, "Sentry transaction URL: $url")
    Toast.makeText(this, "Dumped transaction URL to logcat.", Toast.LENGTH_SHORT).show()
  }

  private fun copyTransactionUrl(url: String) {
    val clipboard = getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Sentry transaction URL", url))
    Toast.makeText(this, "Copied transaction URL to clipboard.", Toast.LENGTH_SHORT).show()
  }

  private fun sentryEventButton(label: String, id: Int, onClick: () -> Unit): Button =
    Button(this).apply {
      this.id = id
      text = label
      isAllCaps = false
      setTextColor(color(android.R.color.white))
      background =
        RippleDrawable(
          ColorStateList.valueOf(0x33FFFFFF),
          GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20.dp.toFloat()
            setColor(color(R.color.colorAccentSoft))
          },
          GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20.dp.toFloat()
            setColor(color(android.R.color.white))
          },
        )
      stateListAnimator = null
      elevation = 0f
      translationZ = 0f
      setOnClickListener { onClick() }
      layoutParams =
        LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
          setMargins(6.dp, 0, 6.dp, 0)
        }
    }

  private fun showRouteWorkDialog() {
    showRouteWorkDialog(this, routeWorkOptions.value) { selectedOptions ->
      routeWorkOptions.value = selectedOptions
    }
  }

  internal fun captureSampleException(navName: String) {
    Sentry.captureException(RuntimeException("$navName sample exception button"))
    Thread { Sentry.flush(SENTRY_FLUSH_TIMEOUT_MILLIS) }.start()
  }

  internal fun showCrashConfirmation(navName: String) {
    AlertDialog.Builder(this)
      .setTitle("Crash app?")
      .setMessage("This will throw an uncaught exception and close the sample app.")
      .setNegativeButton("Cancel", null)
      .setPositiveButton("Crash") { _, _ -> crashSampleApp(navName) }
      .show()
  }

  private fun crashSampleApp(navName: String): Nothing {
    throw RuntimeException("Fatal $navName sample crash button")
  }

  private val Int.dp: Int
    get() = (this * resources.displayMetrics.density).toInt()

  private fun color(id: Int): Int = getColor(id)

  private fun matchParentParams(): ViewGroup.LayoutParams =
    ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
}

private const val BOTTOM_SHEET_HIDE_DELAY_MILLIS = 350L
private const val ACTIVITY_UI_LOAD_OP = "ui.load"
private const val TAG = "Nav2Activity"
