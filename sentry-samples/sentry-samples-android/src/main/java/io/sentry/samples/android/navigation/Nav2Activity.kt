package io.sentry.samples.android.navigation

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navArgument
import io.sentry.Sentry
import io.sentry.android.navigation.SentryNavigationListener
import io.sentry.compose.withSentryObservableEffect
import io.sentry.samples.android.GithubAPI
import io.sentry.samples.android.R

/**
 * Sample activity for testing Sentry's [Nav2](https://developer.android.com/guide/navigation)
 * integrations.
 *
 * Exercises [SentryNavigationListener] both directly through fragment navigation and through the
 * Compose `NavHostController.withSentryObservableEffect()` wrapper.
 */
class Nav2Activity : AppCompatActivity() {

  private lateinit var navController: NavController
  private lateinit var currentRouteText: TextView
  private lateinit var navControllerBackStackText: TextView
  private lateinit var currentRouteScroll: HorizontalScrollView
  private lateinit var contentContainer: FrameLayout
  private var composeNavHostView: ComposeView? = null
  private val tabViews = mutableMapOf<Nav2Scenario, Nav2TabView>()
  private val nav2BackStack = mutableListOf<Nav2Destination>(Nav2Destination.SingleStack)
  private var activeScenario = Nav2Scenario.SINGLE_STACK
  private var composeCurrentRouteName = Nav2ComposeDestination.SingleStack.routeName
  private var composeCurrentRouteText = Nav2ComposeDestination.SingleStack.displayRoute()
  private var composeBackStackText = Nav2ComposeDestination.SingleStack.backStackRoute()
  private val sentryNavigationListener = SentryNavigationListener()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val navHostId = View.generateViewId()
    setContentView(createContentView(navHostId))

    val navHostFragment = NavHostFragment.create(R.navigation.nav2_sample)
    supportFragmentManager
      .beginTransaction()
      .replace(navHostId, navHostFragment)
      .setPrimaryNavigationFragment(navHostFragment)
      .commitNow()

    navController = navHostFragment.navController
    navController.addOnDestinationChangedListener(sentryNavigationListener)
    navController.addOnDestinationChangedListener { _, destination, arguments ->
      updateChrome(destination, arguments)
    }

    updateChrome(navController.currentDestination, navController.currentBackStackEntry?.arguments)
  }

  internal fun navigateTo(destination: Nav2Destination) {
    nav2BackStack.add(destination)
    try {
      navController.navigate(destination.id, destination.arguments)
    } catch (e: RuntimeException) {
      nav2BackStack.removeAt(nav2BackStack.lastIndex)
      throw e
    }
    updateNavControllerBackStackText()
  }

  internal fun resetToSingleStack() {
    nav2BackStack.resetTo(Nav2Destination.SingleStack)
    if (!navController.popBackStack(R.id.nav2_single_stack, false)) {
      navController.setGraph(R.navigation.nav2_sample)
    }
    updateNavControllerBackStackText()
  }

  internal fun openSyntheticProductDeepLink() {
    resetToSingleStack()
    navigateTo(Nav2Destination.ProductList)
    navigateTo(Nav2Destination.ProductDetail("42", "deep-link", "email"))
  }

  internal fun navigateBack() {
    if (nav2BackStack.size > 1) {
      nav2BackStack.removeAt(nav2BackStack.lastIndex)
    }
    navController.popBackStack()
    updateNavControllerBackStackText()
  }

  private fun createContentView(navHostId: Int): View {
    val root =
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = matchParentParams()
        setOnApplyWindowInsetsListener { view, insets ->
          @Suppress("DEPRECATION")
          view.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
          insets
        }
      }

    root.addView(createTopBar())
    root.addView(createTabs())
    root.addView(
      FrameLayout(this).apply {
        id = navHostId
        contentContainer = this
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
      }
    )
    root.addView(createSentryControls())
    return root
  }

  private fun createComposeNavHostView(): ComposeView =
    ComposeView(this).apply {
      setBackgroundColor(color(android.R.color.white))
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        MaterialTheme {
          Nav2ComposeSingleStackApp(
            navListener = sentryNavigationListener,
            onRouteChanged = { routeName, currentRoute, backStack ->
              updateComposeChrome(routeName, currentRoute, backStack)
            },
          )
        }
      }
    }

  private fun createTopBar(): View {
    currentRouteText = bodyText()
    navControllerBackStackText = bodyText()
    currentRouteScroll = horizontalTextContainer(currentRouteText)
    navControllerBackStackText.isSingleLine = false

    return LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(24.dp, 18.dp, 24.dp, 12.dp)
      addView(
        TextView(context).apply {
          text = "Navigation 2"
          textSize = 20f
          setTextColor(color(android.R.color.black))
        }
      )
      addView(View(context), LinearLayout.LayoutParams(MATCH_PARENT, 12.dp))
      addView(currentRouteScroll)
      addView(navControllerBackStackText)
    }
  }

  private fun createTabs(): View {
    val tabRow =
      LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(24.dp, 0, 24.dp, 0)
      }

    Nav2Scenario.entries.forEach { scenario ->
      val tabContainer =
        LinearLayout(this).apply {
          orientation = LinearLayout.VERTICAL
          gravity = Gravity.CENTER
          minimumWidth = 120.dp
          setOnClickListener { openScenario(scenario) }
        }
      val textView =
        TextView(this).apply {
          text = scenario.label
          textSize = 14f
          gravity = Gravity.CENTER
          setPadding(18.dp, 14.dp, 18.dp, 10.dp)
        }
      val indicator =
        View(this).apply {
          layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 3.dp)
          setBackgroundColor(color(android.R.color.transparent))
        }
      tabContainer.addView(textView)
      tabContainer.addView(indicator)
      tabViews[scenario] = Nav2TabView(textView, indicator)
      tabRow.addView(tabContainer)
    }

    return HorizontalScrollView(this).apply {
      isHorizontalScrollBarEnabled = false
      addView(tabRow)
    }
  }

  private fun createSentryControls(): View {
    val sentryPink = color(R.color.colorAccent)
    val white = color(android.R.color.white)

    return LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      setPadding(12.dp)
      showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
      dividerPadding = 8.dp
      addView(
        nav2ControlButton("Exception", sentryPink, white) {
          val routeName = currentRouteName()
          tagNav2SampleAction("capture_exception", routeName)
          Sentry.captureException(RuntimeException("Nav2 sample exception from /$routeName"))
          Thread { Sentry.flush(SENTRY_FLUSH_TIMEOUT_MILLIS) }.start()
          Toast.makeText(context, "Captured exception from /$routeName", Toast.LENGTH_SHORT).show()
        }
      )
      addView(
        nav2ControlButton("Load Data", sentryPink, white) {
          val routeName = currentRouteName()
          tagNav2SampleAction("load_data", routeName)
          Thread {
              val message =
                try {
                  val repos = GithubAPI.service.listRepos("getsentry").execute().body().orEmpty()
                  "Loaded ${repos.size} repos"
                } catch (e: Throwable) {
                  Sentry.captureException(e)
                  "Request failed"
                }
              Sentry.flush(SENTRY_FLUSH_TIMEOUT_MILLIS)
              runOnUiThread { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
            }
            .start()
        }
      )
      addView(
        nav2ControlButton("Crash App", sentryPink, white) {
          val routeName = currentRouteName()
          AlertDialog.Builder(this@Nav2Activity)
            .setTitle("Crash app?")
            .setMessage("This sends a fatal crash from /$routeName and closes the app.")
            .setPositiveButton("Crash") { _, _ ->
              tagNav2SampleAction("crash", routeName)
              throw RuntimeException("Fatal Nav2 sample crash from /$routeName")
            }
            .setNegativeButton("Cancel", null)
            .show()
        }
      )
    }
  }

  private fun openScenario(scenario: Nav2Scenario) {
    when (scenario) {
      Nav2Scenario.SINGLE_STACK -> {
        showFragmentNavHost()
        resetToSingleStack()
      }
      Nav2Scenario.SINGLE_STACK_COMPOSE -> showComposeSingleStack()
      Nav2Scenario.DIALOGS_SHEETS -> {
        showFragmentNavHost()
        resetToDestination(Nav2Destination.DialogsAndSheets)
      }
      Nav2Scenario.DEEP_LINK -> {
        showFragmentNavHost()
        resetToDestination(Nav2Destination.DeepLink)
      }
    }
  }

  private fun showFragmentNavHost() {
    composeNavHostView?.let { view ->
      view.disposeComposition()
      contentContainer.removeView(view)
      composeNavHostView = null
    }
    composeCurrentRouteName = Nav2ComposeDestination.SingleStack.routeName
    composeCurrentRouteText = Nav2ComposeDestination.SingleStack.displayRoute()
    composeBackStackText = Nav2ComposeDestination.SingleStack.backStackRoute()
  }

  private fun showComposeSingleStack() {
    activeScenario = Nav2Scenario.SINGLE_STACK_COMPOSE
    if (composeNavHostView == null) {
      composeNavHostView =
        createComposeNavHostView().also { view ->
          contentContainer.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
    }
    currentRouteText.text = "Current tab: /SingleStackCompose"
    navControllerBackStackText.text = "Compose NavHost route details are shown below."
    updateTabSelection(activeScenario)
  }

  private fun resetToDestination(destination: Nav2Destination) {
    nav2BackStack.resetTo(destination)
    navController.navigate(
      destination.id,
      destination.arguments,
      NavOptions.Builder()
        .setPopUpTo(R.id.nav2_single_stack, false)
        .setLaunchSingleTop(true)
        .build(),
    )
    updateNavControllerBackStackText()
  }

  private fun updateChrome(destination: NavDestination?, arguments: Bundle?) {
    syncTrackedBackStack(destination, arguments)

    val routeName = destination?.routeName() ?: "SingleStack"
    val displayArguments = arguments.displayArguments()
    val currentRoute =
      if (displayArguments.isEmpty()) {
        "/$routeName"
      } else {
        "/$routeName { ${displayArguments.toDisplayString()} }"
      }

    currentRouteText.text = "Current route: $currentRoute"
    currentRouteScroll.post { currentRouteScroll.scrollTo(0, 0) }
    updateNavControllerBackStackText()

    activeScenario = currentScenario(destination, arguments)
    updateTabSelection(activeScenario)
  }

  private fun updateComposeChrome(routeName: String, currentRoute: String, backStack: String) {
    composeCurrentRouteName = routeName
    composeCurrentRouteText = currentRoute
    composeBackStackText = backStack

    if (activeScenario != Nav2Scenario.SINGLE_STACK_COMPOSE) {
      return
    }

    currentRouteText.text = "Current tab: /SingleStackCompose"
    navControllerBackStackText.text = "Compose NavHost route details are shown below."
  }

  private fun updateTabSelection(selectedScenario: Nav2Scenario) {
    tabViews.forEach { (scenario, tabView) ->
      val selected = scenario == selectedScenario
      tabView.label.setTextColor(
        color(if (selected) R.color.colorPrimary else android.R.color.black)
      )
      tabView.label.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
      tabView.indicator.setBackgroundColor(
        color(if (selected) R.color.colorPrimary else android.R.color.transparent)
      )
    }
  }

  private fun currentScenario(destination: NavDestination?, arguments: Bundle?): Nav2Scenario {
    return when (destination?.id) {
      R.id.nav2_dialogs_and_sheets -> Nav2Scenario.DIALOGS_SHEETS
      R.id.nav2_promo_dialog,
      R.id.nav2_share_sheet ->
        arguments?.getString(ARG_SCENARIO)?.toNav2Scenario() ?: Nav2Scenario.DIALOGS_SHEETS
      R.id.nav2_deep_link -> Nav2Scenario.DEEP_LINK
      R.id.nav2_product_detail ->
        if (arguments?.getString(ARG_SOURCE) == "deep-link") {
          Nav2Scenario.DEEP_LINK
        } else {
          Nav2Scenario.SINGLE_STACK
        }
      else -> Nav2Scenario.SINGLE_STACK
    }
  }

  private fun navControllerBackStack(): String {
    return nav2BackStack.joinToString(" -> ") { destination -> "/${destination.routeName}" }
  }

  private fun updateNavControllerBackStackText() {
    navControllerBackStackText.text = "NavController back stack: ${navControllerBackStack()}"
  }

  private fun syncTrackedBackStack(destination: NavDestination?, arguments: Bundle?) {
    if (
      destination == null || nav2BackStack.lastOrNull()?.matches(destination, arguments) == true
    ) {
      return
    }

    val destinationIndex = nav2BackStack.indexOfLast { trackedDestination ->
      trackedDestination.matches(destination, arguments)
    }
    if (destinationIndex >= 0) {
      nav2BackStack.subList(destinationIndex + 1, nav2BackStack.size).clear()
      return
    }

    destination.toNav2Destination(arguments)?.let { nav2BackStack.resetTo(it) }
  }

  private fun currentRouteName(): String =
    if (activeScenario == Nav2Scenario.SINGLE_STACK_COMPOSE) {
      composeCurrentRouteName
    } else {
      navController.currentDestination?.routeName() ?: "SingleStack"
    }

  private fun bodyText(): TextView =
    TextView(this).apply {
      textSize = 12f
      setTextColor(color(android.R.color.black))
      isSingleLine = true
    }

  private fun horizontalTextContainer(textView: TextView): HorizontalScrollView =
    HorizontalScrollView(this).apply {
      isHorizontalScrollBarEnabled = false
      addView(textView)
    }

  private fun nav2ControlButton(
    label: String,
    background: Int,
    foreground: Int,
    click: () -> Unit,
  ): Button =
    Button(this).apply {
      text = label
      isAllCaps = false
      setTextColor(foreground)
      backgroundTintList = ColorStateList.valueOf(background)
      setOnClickListener { click() }
      layoutParams =
        LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
          setMargins(6.dp, 0, 6.dp, 0)
        }
    }

  private val Int.dp: Int
    get() = (this * resources.displayMetrics.density).toInt()

  private fun color(id: Int): Int = getColor(id)

  private fun matchParentParams(): ViewGroup.LayoutParams =
    ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)

  companion object {
    private const val SENTRY_FLUSH_TIMEOUT_MILLIS = 5000L
  }
}

@Composable
private fun Nav2ComposeSingleStackApp(
  navListener: SentryNavigationListener,
  onRouteChanged: (routeName: String, currentRoute: String, backStack: String) -> Unit,
) {
  val navController = rememberNavController().withSentryObservableEffect(navListener)
  val backStack = rememberSaveableNav2ComposeBackStack()
  val currentDestination = backStack.lastOrNull() ?: Nav2ComposeDestination.SingleStack

  fun navigateTo(destination: Nav2ComposeDestination) {
    backStack.add(destination)
    navController.navigate(destination.route)
  }

  fun navigateBack() {
    if (backStack.size > 1) {
      backStack.removeAt(backStack.lastIndex)
      navController.popBackStack()
    }
  }

  fun resetToSingleStack() {
    backStack.resetTo(Nav2ComposeDestination.SingleStack)
    navController.navigate(Nav2ComposeDestination.SingleStack.route) {
      popUpTo(Nav2ComposeDestination.SingleStack.route) { inclusive = false }
      launchSingleTop = true
    }
  }

  BackHandler(enabled = backStack.size > 1) { navigateBack() }

  LaunchedEffect(currentDestination, backStack.size) {
    onRouteChanged(
      currentDestination.routeName,
      currentDestination.displayRoute(),
      backStack.toComposeBackStackText(),
    )
  }

  Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Nav2ComposeMiniChrome(
      currentRoute = currentDestination.displayRoute(),
      backStack = backStack.toComposeBackStackText(),
    )
    NavHost(
      navController = navController,
      startDestination = Nav2ComposeDestination.SingleStack.route,
      modifier = Modifier.weight(1f),
    ) {
      composable(Nav2ComposeDestination.SingleStack.route) {
        Nav2ComposeSingleStackRoute { navigateTo(Nav2ComposeDestination.ProductList) }
      }
      composable(Nav2ComposeDestination.ProductList.route) {
        Nav2ComposeProductListRoute(
          onOpenProduct42 = {
            navigateTo(
              Nav2ComposeDestination.ProductDetail(
                productId = "42",
                source = "product-list",
                campaign = "summer-sale",
              )
            )
          },
          onOpenProduct7 = {
            navigateTo(
              Nav2ComposeDestination.ProductDetail(productId = "7", source = "product-list")
            )
          },
        )
      }
      composable(
        route = Nav2ComposeDestination.PRODUCT_DETAIL_ROUTE,
        arguments =
          listOf(
            navArgument(ARG_PRODUCT_ID) { type = NavType.StringType },
            navArgument(ARG_SOURCE) { type = NavType.StringType },
            navArgument(ARG_CAMPAIGN) {
              type = NavType.StringType
              defaultValue = ""
            },
          ),
      ) { entry ->
        val productId = entry.arguments?.getString(ARG_PRODUCT_ID).orEmpty()
        val source = entry.arguments?.getString(ARG_SOURCE).orEmpty()
        val campaign = entry.arguments?.getString(ARG_CAMPAIGN).orEmpty()
        Nav2ComposeProductDetailRoute(
          productId = productId,
          source = source,
          campaign = campaign,
          onShowPromoDialog = {
            navigateTo(Nav2ComposeDestination.PromoDialog("detail-$productId"))
          },
          onOpenShareSheet = { navigateTo(Nav2ComposeDestination.ShareSheet(productId)) },
          onCheckout = { navigateTo(Nav2ComposeDestination.Checkout(productId)) },
        )
      }
      composable(
        route = Nav2ComposeDestination.CHECKOUT_ROUTE,
        arguments = listOf(navArgument(ARG_PRODUCT_ID) { type = NavType.StringType }),
      ) { entry ->
        val productId = entry.arguments?.getString(ARG_PRODUCT_ID).orEmpty()
        Nav2ComposeCheckoutRoute(
          productId = productId,
          onCompleteOrder = {
            navigateTo(Nav2ComposeDestination.Confirmation(orderId = "order-$productId"))
          },
        )
      }
      composable(
        route = Nav2ComposeDestination.CONFIRMATION_ROUTE,
        arguments = listOf(navArgument(ARG_ORDER_ID) { type = NavType.StringType }),
      ) { entry ->
        Nav2ComposeConfirmationRoute(
          orderId = entry.arguments?.getString(ARG_ORDER_ID).orEmpty(),
          onResetBackStack = { resetToSingleStack() },
        )
      }
      dialog(
        route = Nav2ComposeDestination.PROMO_DIALOG_ROUTE,
        arguments = listOf(navArgument(ARG_PROMO_ID) { type = NavType.StringType }),
      ) { entry ->
        Nav2ComposePromoDialogRoute(
          promoId = entry.arguments?.getString(ARG_PROMO_ID).orEmpty(),
          onDismiss = { navigateBack() },
        )
      }
      composable(
        route = Nav2ComposeDestination.SHARE_SHEET_ROUTE,
        arguments = listOf(navArgument(ARG_PRODUCT_ID) { type = NavType.StringType }),
      ) { entry ->
        Nav2ComposeShareSheetRoute(
          productId = entry.arguments?.getString(ARG_PRODUCT_ID).orEmpty(),
          onDone = { navigateBack() },
        )
      }
    }
  }
}

@Composable
private fun Nav2ComposeMiniChrome(currentRoute: String, backStack: String) {
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      text = "Nav2 Compose host",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text = "Current route: $currentRoute",
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      maxLines = 1,
    )
    Text(
      text = "Tracked back stack: $backStack",
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      maxLines = 1,
    )
  }
}

@Composable
private fun Nav2ComposeSingleStackRoute(onBrowseProducts: () -> Unit) {
  Nav2ComposeRouteScaffold(
    title = "Single Stack",
    description =
      "Start a single-backstack product flow through a Nav2 Compose NavHostController, then use " +
        "the Sentry UI to inspect route transactions, breadcrumbs, screen tracking, and captured " +
        "destination arguments.",
  ) {
    Nav2ComposeRouteButton("Browse Products", onBrowseProducts)
  }
}

@Composable
private fun Nav2ComposeProductListRoute(
  onOpenProduct42: () -> Unit,
  onOpenProduct7: () -> Unit,
) {
  Nav2ComposeRouteScaffold(
    title = "Product List",
    description = "This route starts the single-stack product journey.",
  ) {
    Nav2ComposeRouteButton("Open Product 42", onOpenProduct42)
    Nav2ComposeRouteButton("Open Product 7", onOpenProduct7)
  }
}

@Composable
private fun Nav2ComposeProductDetailRoute(
  productId: String,
  source: String,
  campaign: String,
  onShowPromoDialog: () -> Unit,
  onOpenShareSheet: () -> Unit,
  onCheckout: () -> Unit,
) {
  Nav2ComposeRouteScaffold(
    title = "Product Detail",
    description =
      "Arguments should appear on navigation breadcrumbs, transaction data, and Sentry's Nav2 " +
        "destination arguments.",
  ) {
    Nav2ComposeRouteInfo("productId", productId)
    Nav2ComposeRouteInfo("source", source)
    if (campaign.isNotEmpty()) {
      Nav2ComposeRouteInfo("campaign", campaign)
    }
    Nav2ComposeRouteButton("Show Promo Dialog", onShowPromoDialog)
    Nav2ComposeRouteButton("Open Share Sheet", onOpenShareSheet)
    Nav2ComposeRouteButton("Go to Checkout", onCheckout)
  }
}

@Composable
private fun Nav2ComposeCheckoutRoute(productId: String, onCompleteOrder: () -> Unit) {
  Nav2ComposeRouteScaffold(
    title = "Checkout",
    description = "Continue the same product flow to verify transaction rotation across routes.",
  ) {
    Nav2ComposeRouteInfo("productId", productId)
    Nav2ComposeRouteButton("Complete Order", onCompleteOrder)
  }
}

@Composable
private fun Nav2ComposeConfirmationRoute(orderId: String, onResetBackStack: () -> Unit) {
  Nav2ComposeRouteScaffold(title = "Confirmation", description = "End of the single-stack flow.") {
    Nav2ComposeRouteInfo("orderId", orderId)
    Nav2ComposeRouteButton("Reset Backstack", onResetBackStack)
  }
}

@Composable
private fun Nav2ComposePromoDialogRoute(promoId: String, onDismiss: () -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
  ) {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("Promo Dialog", style = MaterialTheme.typography.headlineSmall)
      Text("Dialog route promoId=$promoId")
      Button(onClick = onDismiss) { Text("Dismiss") }
    }
  }
}

@Composable
private fun Nav2ComposeShareSheetRoute(productId: String, onDone: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxSize().padding(16.dp),
    verticalArrangement = Arrangement.Bottom,
  ) {
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text("Share Sheet", style = MaterialTheme.typography.headlineSmall)
        Text("Bottom sheet route for productId=$productId")
        Button(onClick = onDone) { Text("Done") }
      }
    }
  }
}

@Composable
private fun Nav2ComposeRouteScaffold(
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
private fun Nav2ComposeRouteButton(
  label: String,
  onClick: () -> Unit,
  enabled: Boolean = true,
) {
  Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

@Composable
private fun Nav2ComposeRouteInfo(label: String, value: String) {
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

private fun SnapshotStateList<Nav2ComposeDestination>.resetTo(destination: Nav2ComposeDestination) {
  clear()
  add(destination)
}

@Composable
private fun rememberSaveableNav2ComposeBackStack(): SnapshotStateList<Nav2ComposeDestination> {
  return rememberSaveable(saver = nav2ComposeBackStackSaver()) {
    mutableStateListOf<Nav2ComposeDestination>(Nav2ComposeDestination.SingleStack)
  }
}

private fun nav2ComposeBackStackSaver() =
  listSaver<SnapshotStateList<Nav2ComposeDestination>, Bundle>(
    save = { stack -> stack.map { destination -> destination.toSavedState() } },
    restore = { savedDestinations ->
      mutableStateListOf<Nav2ComposeDestination>().apply {
        addAll(
          savedDestinations.map { savedDestination -> savedDestination.toNav2ComposeDestination() }
        )
        if (isEmpty()) {
          add(Nav2ComposeDestination.SingleStack)
        }
      }
    },
  )

private fun List<Nav2ComposeDestination>.toComposeBackStackText(): String =
  joinToString(" -> ") { destination -> destination.backStackRoute() }

class Nav2RouteFragment : Fragment() {
  override fun onCreateView(
    inflater: android.view.LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    val activity = requireActivity() as Nav2Activity
    val routeName = requireArguments().getString(ARG_ROUTE_NAME).orEmpty()

    return when (routeName) {
      "SingleStack" ->
        routeLayout(
          title = "Single Stack",
          description =
            "Start a single-backstack product flow, then use the Sentry UI to inspect route " +
              "transactions, breadcrumbs, screen tracking, and captured backstack context.",
          buttons =
            listOf("Browse Products" to { activity.navigateTo(Nav2Destination.ProductList) }),
        )
      "DialogsAndSheets" ->
        routeLayout(
          title = "Dialogs & Sheets",
          description =
            "Dialog and bottom sheet destinations are represented as Nav2 backstack entries for " +
              "comparison with the Nav3 sample.",
          buttons =
            listOf(
              "Show Dialog Destination" to
                {
                  activity.navigateTo(
                    Nav2Destination.PromoDialog("summer-sale", Nav2Scenario.DIALOGS_SHEETS)
                  )
                },
              "Show Bottom Sheet Destination" to
                {
                  activity.navigateTo(
                    Nav2Destination.ShareSheet("home", Nav2Scenario.DIALOGS_SHEETS)
                  )
                },
            ),
        )
      "DeepLink" ->
        routeLayout(
          title = "Deep Link",
          description =
            "Simulates opening a deep link that builds a synthetic backstack before landing on a " +
              "detail destination.",
          buttons =
            listOf("Go to deep link destination" to { activity.openSyntheticProductDeepLink() }),
        )
      "ProductList" ->
        routeLayout(
          title = "Product List",
          description = "This route starts the single-stack product journey.",
          buttons =
            listOf(
              "Open Product 42" to
                {
                  activity.navigateTo(
                    Nav2Destination.ProductDetail("42", "product-list", "summer-sale")
                  )
                },
              "Open Product 7" to
                {
                  activity.navigateTo(Nav2Destination.ProductDetail("7", "product-list"))
                },
            ),
        )
      "ProductDetail" -> productDetailLayout(activity)
      "Checkout" -> checkoutLayout(activity)
      "Confirmation" -> confirmationLayout(activity)
      "PromoDialog" -> promoDialogLayout(activity)
      "ShareSheet" -> shareSheetLayout(activity)
      else -> routeLayout("Unknown Route", routeName)
    }
  }

  private fun productDetailLayout(activity: Nav2Activity): View {
    val productId = requireArguments().getString(ARG_PRODUCT_ID).orEmpty()
    val source = requireArguments().getString(ARG_SOURCE).orEmpty()
    val campaign = requireArguments().getString(ARG_CAMPAIGN).orEmpty()
    val scenario =
      if (source == "deep-link") {
        Nav2Scenario.DEEP_LINK
      } else {
        Nav2Scenario.SINGLE_STACK
      }
    return routeLayout(
      title = "Product Detail",
      description =
        "Arguments should appear on navigation breadcrumbs, transaction data, and Sentry's Nav2 " +
          "destination arguments.",
      info =
        listOfNotNull(
          "productId" to productId,
          "source" to source,
          if (campaign.isNotEmpty()) "campaign" to campaign else null,
        ),
      buttons =
        listOf(
          "Show Promo Dialog" to
            {
              activity.navigateTo(Nav2Destination.PromoDialog("detail-$productId", scenario))
            },
          "Open Share Sheet" to
            {
              activity.navigateTo(Nav2Destination.ShareSheet(productId, scenario))
            },
          "Go to Checkout" to { activity.navigateTo(Nav2Destination.Checkout(productId)) },
        ),
    )
  }

  private fun checkoutLayout(activity: Nav2Activity): View {
    val productId = requireArguments().getString(ARG_PRODUCT_ID).orEmpty()
    return routeLayout(
      title = "Checkout",
      description = "Continue the same product flow to verify transaction rotation across routes.",
      info = listOf("productId" to productId),
      buttons =
        listOf(
          "Complete Order" to
            {
              activity.navigateTo(Nav2Destination.Confirmation("order-$productId"))
            }
        ),
    )
  }

  private fun confirmationLayout(activity: Nav2Activity): View {
    val orderId = requireArguments().getString(ARG_ORDER_ID).orEmpty()
    return routeLayout(
      title = "Confirmation",
      description = "End of the single-stack flow.",
      info = listOf("orderId" to orderId),
      buttons = listOf("Reset Backstack" to { activity.resetToSingleStack() }),
    )
  }

  private fun promoDialogLayout(activity: Nav2Activity): View {
    val promoId = requireArguments().getString(ARG_PROMO_ID).orEmpty()
    return centeredCard(
      title = "Promo Dialog",
      body = "Dialog route promoId=$promoId",
      button = "Dismiss" to { activity.navigateBack() },
    )
  }

  private fun shareSheetLayout(activity: Nav2Activity): View {
    val productId = requireArguments().getString(ARG_PRODUCT_ID).orEmpty()
    return bottomSheet(
      title = "Share Sheet",
      body = "Bottom sheet route for productId=$productId",
      button = "Done" to { activity.navigateBack() },
    )
  }

  private fun routeLayout(
    title: String,
    description: String,
    info: List<Pair<String, String>> = emptyList(),
    buttons: List<Pair<String, () -> Unit>> = emptyList(),
  ): View {
    return LinearLayout(requireContext()).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(16.dp)
      addView(titleText(title))
      addView(bodyText(description))
      if (info.isNotEmpty() || buttons.isNotEmpty()) {
        addView(
          LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp)
            setBackgroundColor(color(android.R.color.darker_gray))
            info.forEach { (label, value) -> addView(infoRow(label, value)) }
            buttons.forEach { (label, onClick) -> addView(routeButton(label, onClick)) }
          }
        )
      }
    }
  }

  private fun centeredCard(title: String, body: String, button: Pair<String, () -> Unit>): View =
    FrameLayout(requireContext()).apply {
      addView(
        LinearLayout(context).apply {
          orientation = LinearLayout.VERTICAL
          setPadding(24.dp)
          setBackgroundColor(NAV2_SURFACE_GRAY)
          addView(titleText(title))
          addView(bodyText(body))
          addView(routeButton(button.first, button.second))
        },
        FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER).apply {
          setMargins(32.dp, 0, 32.dp, 0)
        },
      )
    }

  private fun bottomSheet(title: String, body: String, button: Pair<String, () -> Unit>): View =
    FrameLayout(requireContext()).apply {
      addView(
        LinearLayout(context).apply {
          orientation = LinearLayout.VERTICAL
          setPadding(24.dp)
          setBackgroundColor(NAV2_SURFACE_GRAY)
          addView(titleText(title))
          addView(bodyText(body))
          addView(routeButton(button.first, button.second))
        },
        FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM).apply {
          setMargins(16.dp, 0, 16.dp, 12.dp)
        },
      )
    }

  private fun titleText(textValue: String): TextView =
    TextView(requireContext()).apply {
      text = textValue
      textSize = 26f
      setTypeface(null, Typeface.BOLD)
      setTextColor(color(android.R.color.black))
      setPadding(0, 0, 0, 12.dp)
    }

  private fun bodyText(textValue: String): TextView =
    TextView(requireContext()).apply {
      text = textValue
      textSize = 15f
      setTextColor(color(android.R.color.black))
      setPadding(0, 0, 0, 16.dp)
    }

  private fun infoRow(label: String, value: String): View =
    TextView(requireContext()).apply {
      text = "$label: $value"
      textSize = 14f
      setPadding(8.dp)
    }

  private fun routeButton(label: String, onClick: () -> Unit): Button =
    Button(requireContext()).apply {
      text = label
      isAllCaps = false
      setOnClickListener { onClick() }
      layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    }

  private val Int.dp: Int
    get() = (this * resources.displayMetrics.density).toInt()

  private fun color(id: Int): Int = requireContext().getColor(id)
}

private sealed class Nav2ComposeDestination(
  val routeName: String,
  val route: String,
  val arguments: Map<String, Any?> = emptyMap(),
) {
  data object SingleStack : Nav2ComposeDestination("SingleStack", "SingleStack")

  data object ProductList : Nav2ComposeDestination("ProductList", "ProductList")

  data class ProductDetail(
    val productId: String,
    val source: String,
    val campaign: String = "",
  ) :
    Nav2ComposeDestination(
      routeName = "ProductDetail",
      route =
        "ProductDetail/$productId/$source" +
          if (campaign.isNotEmpty()) "?campaign=$campaign" else "",
      arguments =
        mapOf(ARG_PRODUCT_ID to productId, ARG_SOURCE to source, ARG_CAMPAIGN to campaign)
          .filterValues { value -> value.isNotEmpty() },
    )

  data class Checkout(val productId: String) :
    Nav2ComposeDestination(
      routeName = "Checkout",
      route = "Checkout/$productId",
      arguments = mapOf(ARG_PRODUCT_ID to productId),
    )

  data class Confirmation(val orderId: String) :
    Nav2ComposeDestination(
      routeName = "Confirmation",
      route = "Confirmation/$orderId",
      arguments = mapOf(ARG_ORDER_ID to orderId),
    )

  data class PromoDialog(val promoId: String) :
    Nav2ComposeDestination(
      routeName = "PromoDialog",
      route = "PromoDialog/$promoId",
      arguments = mapOf(ARG_PROMO_ID to promoId),
    )

  data class ShareSheet(val productId: String) :
    Nav2ComposeDestination(
      routeName = "ShareSheet",
      route = "ShareSheet/$productId",
      arguments = mapOf(ARG_PRODUCT_ID to productId),
    )

  fun displayRoute(): String {
    return if (arguments.isEmpty()) {
      "/$routeName"
    } else {
      "/$routeName { ${arguments.toDisplayString()} }"
    }
  }

  fun backStackRoute(): String = "/$routeName"

  fun toSavedState(): Bundle =
    Bundle().apply {
      when (this@Nav2ComposeDestination) {
        Nav2ComposeDestination.SingleStack -> putString("type", "single_stack")
        Nav2ComposeDestination.ProductList -> putString("type", "product_list")
        is Nav2ComposeDestination.ProductDetail -> {
          putString("type", "product_detail")
          putString(ARG_PRODUCT_ID, productId)
          putString(ARG_SOURCE, source)
          putString(ARG_CAMPAIGN, campaign)
        }
        is Nav2ComposeDestination.Checkout -> {
          putString("type", "checkout")
          putString(ARG_PRODUCT_ID, productId)
        }
        is Nav2ComposeDestination.Confirmation -> {
          putString("type", "confirmation")
          putString(ARG_ORDER_ID, orderId)
        }
        is Nav2ComposeDestination.PromoDialog -> {
          putString("type", "promo_dialog")
          putString(ARG_PROMO_ID, promoId)
        }
        is Nav2ComposeDestination.ShareSheet -> {
          putString("type", "share_sheet")
          putString(ARG_PRODUCT_ID, productId)
        }
      }
    }

  companion object {
    const val PRODUCT_DETAIL_ROUTE = "ProductDetail/{product_id}/{source}?campaign={campaign}"
    const val CHECKOUT_ROUTE = "Checkout/{product_id}"
    const val CONFIRMATION_ROUTE = "Confirmation/{order_id}"
    const val PROMO_DIALOG_ROUTE = "PromoDialog/{promo_id}"
    const val SHARE_SHEET_ROUTE = "ShareSheet/{product_id}"
  }
}

private fun Bundle.toNav2ComposeDestination(): Nav2ComposeDestination {
  return when (getString("type")) {
    "single_stack" -> Nav2ComposeDestination.SingleStack
    "product_list" -> Nav2ComposeDestination.ProductList
    "product_detail" ->
      Nav2ComposeDestination.ProductDetail(
        productId = requireNotNull(getString(ARG_PRODUCT_ID)),
        source = requireNotNull(getString(ARG_SOURCE)),
        campaign = getString(ARG_CAMPAIGN).orEmpty(),
      )
    "checkout" ->
      Nav2ComposeDestination.Checkout(productId = requireNotNull(getString(ARG_PRODUCT_ID)))
    "confirmation" ->
      Nav2ComposeDestination.Confirmation(orderId = requireNotNull(getString(ARG_ORDER_ID)))
    "promo_dialog" ->
      Nav2ComposeDestination.PromoDialog(promoId = requireNotNull(getString(ARG_PROMO_ID)))
    "share_sheet" ->
      Nav2ComposeDestination.ShareSheet(productId = requireNotNull(getString(ARG_PRODUCT_ID)))
    else -> Nav2ComposeDestination.SingleStack
  }
}

internal sealed class Nav2Destination(
  val id: Int,
  val routeName: String,
  val arguments: Bundle = Bundle.EMPTY,
) {
  data object SingleStack : Nav2Destination(R.id.nav2_single_stack, "SingleStack")

  data object ProductList : Nav2Destination(R.id.nav2_product_list, "ProductList")

  data object DialogsAndSheets : Nav2Destination(R.id.nav2_dialogs_and_sheets, "DialogsAndSheets")

  data object DeepLink : Nav2Destination(R.id.nav2_deep_link, "DeepLink")

  data class ProductDetail(
    val productId: String,
    val source: String,
    val campaign: String = "",
  ) :
    Nav2Destination(
      R.id.nav2_product_detail,
      "ProductDetail",
      bundleOf(ARG_PRODUCT_ID to productId, ARG_SOURCE to source, ARG_CAMPAIGN to campaign),
    )

  data class Checkout(val productId: String) :
    Nav2Destination(R.id.nav2_checkout, "Checkout", bundleOf(ARG_PRODUCT_ID to productId))

  data class Confirmation(val orderId: String) :
    Nav2Destination(R.id.nav2_confirmation, "Confirmation", bundleOf(ARG_ORDER_ID to orderId))

  data class PromoDialog(val promoId: String, val scenario: Nav2Scenario) :
    Nav2Destination(
      R.id.nav2_promo_dialog,
      "PromoDialog",
      bundleOf(ARG_PROMO_ID to promoId, ARG_SCENARIO to scenario.name),
    )

  data class ShareSheet(val productId: String, val scenario: Nav2Scenario) :
    Nav2Destination(
      R.id.nav2_share_sheet,
      "ShareSheet",
      bundleOf(ARG_PRODUCT_ID to productId, ARG_SCENARIO to scenario.name),
    )
}

private fun MutableList<Nav2Destination>.resetTo(destination: Nav2Destination) {
  clear()
  add(destination)
}

private fun Nav2Destination.matches(destination: NavDestination, arguments: Bundle?): Boolean =
  id == destination.id && argumentsMatch(arguments)

private fun Nav2Destination.argumentsMatch(arguments: Bundle?): Boolean =
  when (this) {
    Nav2Destination.SingleStack,
    Nav2Destination.ProductList,
    Nav2Destination.DialogsAndSheets,
    Nav2Destination.DeepLink -> true
    is Nav2Destination.ProductDetail ->
      arguments?.getString(ARG_PRODUCT_ID) == productId && arguments.getString(ARG_SOURCE) == source
    is Nav2Destination.Checkout -> arguments?.getString(ARG_PRODUCT_ID) == productId
    is Nav2Destination.Confirmation -> arguments?.getString(ARG_ORDER_ID) == orderId
    is Nav2Destination.PromoDialog -> arguments?.getString(ARG_PROMO_ID) == promoId
    is Nav2Destination.ShareSheet -> arguments?.getString(ARG_PRODUCT_ID) == productId
  }

private fun NavDestination.toNav2Destination(arguments: Bundle?): Nav2Destination? =
  when (id) {
    R.id.nav2_single_stack -> Nav2Destination.SingleStack
    R.id.nav2_product_list -> Nav2Destination.ProductList
    R.id.nav2_dialogs_and_sheets -> Nav2Destination.DialogsAndSheets
    R.id.nav2_deep_link -> Nav2Destination.DeepLink
    R.id.nav2_product_detail ->
      Nav2Destination.ProductDetail(
        productId = arguments?.getString(ARG_PRODUCT_ID).orEmpty(),
        source = arguments?.getString(ARG_SOURCE).orEmpty(),
        campaign = arguments?.getString(ARG_CAMPAIGN).orEmpty(),
      )
    R.id.nav2_checkout -> Nav2Destination.Checkout(arguments?.getString(ARG_PRODUCT_ID).orEmpty())
    R.id.nav2_confirmation ->
      Nav2Destination.Confirmation(arguments?.getString(ARG_ORDER_ID).orEmpty())
    R.id.nav2_promo_dialog ->
      Nav2Destination.PromoDialog(
        promoId = arguments?.getString(ARG_PROMO_ID).orEmpty(),
        scenario =
          arguments?.getString(ARG_SCENARIO).orEmpty().toNav2Scenario()
            ?: Nav2Scenario.DIALOGS_SHEETS,
      )
    R.id.nav2_share_sheet ->
      Nav2Destination.ShareSheet(
        productId = arguments?.getString(ARG_PRODUCT_ID).orEmpty(),
        scenario =
          arguments?.getString(ARG_SCENARIO).orEmpty().toNav2Scenario()
            ?: Nav2Scenario.DIALOGS_SHEETS,
      )
    else -> null
  }

internal enum class Nav2Scenario(val label: String) {
  SINGLE_STACK("Single Stack"),
  SINGLE_STACK_COMPOSE("Single Stack (Compose)"),
  DIALOGS_SHEETS("Dialogs & Sheets"),
  DEEP_LINK("Deep Link"),
}

private data class Nav2TabView(val label: TextView, val indicator: View)

private fun String.toNav2Scenario(): Nav2Scenario? =
  Nav2Scenario.entries.firstOrNull { scenario -> scenario.name == this }

private fun NavDestination.routeName(): String = routeNameOrNull() ?: "SingleStack"

private fun NavDestination.routeNameOrNull(): String? = route ?: label?.toString()?.replace(" ", "")

private fun Bundle?.displayArguments(): Map<String, Any?> =
  this?.let { args ->
    val hiddenKeys = setOf(ARG_ROUTE_NAME, ARG_SCENARIO)
    val orderedKeys = listOf(ARG_PRODUCT_ID, ARG_SOURCE, ARG_CAMPAIGN, ARG_ORDER_ID, ARG_PROMO_ID)
    val remainingKeys =
      args.keySet().filterNot { key -> key in orderedKeys || key in hiddenKeys }.sorted()
    (orderedKeys + remainingKeys)
      .filter { key -> key !in hiddenKeys && args.getString(key).orEmpty().isNotEmpty() }
      .associateWith { key -> args.getString(key) }
  } ?: emptyMap()

private fun Map<String, Any?>.toDisplayString(): String =
  entries.joinToString(", ") { (key, value) -> "$key=$value" }

private fun tagNav2SampleAction(action: String, route: String) {
  Sentry.setTag("sample_action", "nav2_$action")
  Sentry.setTag("sample_nav2_route", route)
}

private const val ARG_ROUTE_NAME = "route_name"
private const val ARG_PRODUCT_ID = "product_id"
private const val ARG_SOURCE = "source"
private const val ARG_CAMPAIGN = "campaign"
private const val ARG_ORDER_ID = "order_id"
private const val ARG_PROMO_ID = "promo_id"
private const val ARG_SCENARIO = "scenario"

private const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT
private const val NAV2_SURFACE_GRAY = 0xFFEDE7F6.toInt()
