package io.sentry.samples.android.navigation

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.sentry.compose.navigation3.SentryNav3Effect
import io.sentry.compose.navigation3.SentryNav3Options
import io.sentry.samples.android.GithubAPI
import io.sentry.samples.android.R
import kotlinx.coroutines.Dispatchers
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

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { MaterialTheme { Nav3SampleApp() } }
  }
}

@SuppressLint("ContextCastToActivity")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Nav3SampleApp() {
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

  SentryNav3Effect(
    backStack = backStack,
    options =
      SentryNav3Options().apply {
        this.enableNavigationBreadcrumbs = enableNavigationBreadcrumbs
        this.enableNavigationTransactions = enableNavigationTransactions
        this.captureBackStack = captureBackStack
        this.maxCapturedBackStackEntries = maxCapturedBackStackEntries
      },
    nameExtractor = { route -> route.routeName },
    argumentsExtractor = { route -> route.arguments },
  )

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
          selectedScenario = scenario
          backStack.openScenario(scenario)
        },
      )
      Box(modifier = Modifier.weight(1f)) {
        NavDisplay(
          backStack = backStack,
          modifier = Modifier.fillMaxSize(),
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
                Nav3RouteActivationEffect(route, routeActivationAction)
                SingleStackRoute(backStack)
              }
              entry<Nav3Route.DialogsAndSheets> { route ->
                Nav3RouteActivationEffect(route, routeActivationAction)
                DialogsAndSheetsRoute(backStack)
              }
              entry<Nav3Route.DeepLink> { route ->
                Nav3RouteActivationEffect(route, routeActivationAction)
                DeepLinkRoute(backStack)
              }
              entry<Nav3Route.ProductList> { route ->
                Nav3RouteActivationEffect(route, routeActivationAction)
                ProductListRoute(backStack)
              }
              entry<Nav3Route.ProductDetail> { route ->
                Nav3RouteActivationEffect(route, routeActivationAction)
                ProductDetailRoute(route, backStack)
              }
              entry<Nav3Route.Checkout> { route ->
                Nav3RouteActivationEffect(route, routeActivationAction)
                CheckoutRoute(route, backStack)
              }
              entry<Nav3Route.Confirmation> { route ->
                Nav3RouteActivationEffect(route, routeActivationAction)
                ConfirmationRoute(route, backStack)
              }
              entry<Nav3Route.PromoDialog>(metadata = Nav3DialogSceneStrategy.dialog()) { route ->
                Nav3RouteActivationEffect(route, routeActivationAction)
                PromoDialogRoute(route, backStack)
              }
              entry<Nav3Route.ShareSheet>(metadata = Nav3BottomSheetSceneStrategy.bottomSheet()) {
                route ->
                Nav3RouteActivationEffect(route, routeActivationAction)
                ShareSheetRoute(route, backStack)
              }
              entry<Nav3Route.Multipane> { route ->
                Nav3RouteActivationEffect(route, routeActivationAction)
                FutureRoute(routeName = "Multipane", scenario = "multipane")
              }
              entry<Nav3Route.Multistack> { route ->
                Nav3RouteActivationEffect(route, routeActivationAction)
                FutureRoute(routeName = "Multiple Stacks", scenario = "multistack")
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
    SentryNav3OptionsMenuItem(
      label = "Navigation breadcrumbs",
      checked = enableNavigationBreadcrumbs,
      onCheckedChange = onEnableNavigationBreadcrumbsChange,
    )
    SentryNav3OptionsMenuItem(
      label = "Navigation transactions",
      checked = enableNavigationTransactions,
      onCheckedChange = onEnableNavigationTransactionsChange,
    )
    SentryNav3OptionsMenuItem(
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
private fun SentryNav3OptionsMenuItem(
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
  val span = Sentry.getSpan()?.startChild("ui.load", "Nav3 /${route.routeName} route activation")
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
    else -> Nav3Route.SingleStack
  }
}

@Composable
private fun FutureRoute(routeName: String, scenario: String) {
  RouteScaffold(
    title = "$routeName: WIP",
    description =
      "Reserved for a future milestone when SentryNav3Effect supports $scenario navigation " +
        "state.",
  )
}

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
}

private enum class Nav3Scenario(val label: String) {
  SINGLE_STACK("Single Stack"),
  DIALOGS_SHEETS("Dialogs & Sheets"),
  DEEP_LINK("Deep Link"),
  MULTIPANE("Multipane"),
  MULTIPLE_STACKS("Multistack"),
}
