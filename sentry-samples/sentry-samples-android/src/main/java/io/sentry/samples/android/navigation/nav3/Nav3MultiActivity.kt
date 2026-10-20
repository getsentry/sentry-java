package io.sentry.samples.android.navigation.nav3

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import io.sentry.compose.SentryModifier.sentryTag
import io.sentry.compose.navigation3.SentryBackStackEntry
import io.sentry.compose.navigation3.SentryNavEffect
import io.sentry.compose.navigation3.SentryNavOptions
import io.sentry.samples.android.navigation.common.NavigationSampleConfig
import io.sentry.samples.android.navigation.common.RouteWorkOption
import io.sentry.samples.android.navigation.common.hasOnlyActivityUiLoadTransactions
import io.sentry.samples.android.navigation.common.showRouteWorkDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Activities that each own a Nav3 root (back stack, `SentryNavEffect`, and `NavDisplay`), which let us
 * exercise how separate effects hand off during Activity transactions.
 */

/** Keeps its navigation effect alive while checkout runs in another Activity. */
class Nav3ProductsActivity : ComponentActivity() {

  private var backStack: SnapshotStateList<Nav3Route>? = null

  /** An order ID returned before [backStack] exists (e.g., after Activity recreation). */
  private var pendingOrderId: String? = null

  // Pushes a destination from the result callback itself, which runs before this Activity resumes,
  // like an app that navigates in response to an Activity result.
  private val checkoutForResult =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      val orderId = result.data?.getStringExtra(EXTRA_ORDER_ID) ?: return@registerForActivityResult
      backStack?.add(Nav3Route.Confirmation(orderId)) ?: run { pendingOrderId = orderId }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val configuration = (intent.extras ?: Bundle()).nav3SampleConfiguration()
    NavigationTransactionHistory.install()
    if (!configuration.enableActivityUiLoadTransaction) {
      cancelCurrentActivityUiLoadTransaction()
    }
    setContent {
      val stack = rememberSaveableNav3BackStack(Nav3Route.ProductList)
      SideEffect {
        backStack = stack
        pendingOrderId?.let { orderId ->
          pendingOrderId = null
          stack.add(Nav3Route.Confirmation(orderId))
        }
      }
      Nav3SampleTheme {
        MultiRootNavDisplay(
          activity = this,
          title = "Products Activity",
          initialRoute = Nav3Route.ProductList,
          scenario = Nav3Scenario.MULTI_ACTIVITY,
          configuration = configuration,
          onFinish = { finish() },
          backStack = stack,
          onCheckout = { productId -> startActivity(checkoutIntent(productId, configuration)) },
          productDetailActions =
            listOf(
              ProductDetailAction(
                label = "Go to Translucent Checkout",
                testTag = nav3TestTag("product_detail_go_to_translucent_checkout"),
                onClick = { productId ->
                  startActivity(
                    checkoutIntent(productId, configuration)
                      .setClass(this, Nav3TranslucentCheckoutActivity::class.java)
                  )
                },
              ),
              ProductDetailAction(
                label = "Go to Checkout for Result",
                testTag = nav3TestTag("product_detail_go_to_checkout_for_result"),
                onClick = { productId ->
                  checkoutForResult.launch(
                    checkoutIntent(productId, configuration).putExtra(EXTRA_RETURN_RESULT, true)
                  )
                },
              ),
              ProductDetailAction(
                label = "Go to Checkout (Crash on First Composition)",
                testTag = nav3TestTag("product_detail_go_to_checkout_crash"),
                onClick = { productId ->
                  startActivity(
                    checkoutIntent(productId, configuration)
                      .putExtra(EXTRA_CRASH_ON_FIRST_COMPOSITION, true)
                  )
                },
              ),
            ),
        )
      }
    }
  }

  override fun onDestroy() {
    backStack = null
    super.onDestroy()
  }

  private fun checkoutIntent(productId: String, configuration: NavigationSampleConfig): Intent =
    Intent(this, Nav3CheckoutActivity::class.java)
      .putExtra(EXTRA_PRODUCT_ID, productId)
      .putExtras(Bundle().apply { putNav3SampleConfiguration(configuration) })
}

/** Owns the checkout back stack independently of the Products Activity. */
open class Nav3CheckoutActivity : ComponentActivity() {

  /** Whether to show checkout as a sheet, leaving the Activity underneath partly visible. */
  protected open val showAsSheet: Boolean = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val configuration = (intent.extras ?: Bundle()).nav3SampleConfiguration()
    val productId = intent.getStringExtra(EXTRA_PRODUCT_ID) ?: "42"
    val returnsResult = intent.getBooleanExtra(EXTRA_RETURN_RESULT, false)
    // Only crash on the first launch, not when the system restores this Activity.
    val crashOnFirstComposition =
      savedInstanceState == null && intent.getBooleanExtra(EXTRA_CRASH_ON_FIRST_COMPOSITION, false)
    NavigationTransactionHistory.install()
    if (!configuration.enableActivityUiLoadTransaction) {
      cancelCurrentActivityUiLoadTransaction()
    }
    setContent {
      Nav3SampleTheme {
        if (showAsSheet) {
          CheckoutSheet {
            CheckoutRoot(productId, configuration, returnsResult, crashOnFirstComposition)
          }
        } else {
          CheckoutRoot(productId, configuration, returnsResult, crashOnFirstComposition)
        }
      }
    }
  }

  @Composable
  private fun CheckoutRoot(
    productId: String,
    configuration: NavigationSampleConfig,
    returnsResult: Boolean,
    crashOnFirstComposition: Boolean,
  ) {
    MultiRootNavDisplay(
      activity = this,
      title = if (showAsSheet) "Translucent Checkout Activity" else "Checkout Activity",
      initialRoute = Nav3Route.Checkout(productId),
      scenario = Nav3Scenario.MULTI_ACTIVITY,
      configuration = configuration,
      onFinish = { finish() },
      onCompleteOrder =
        if (returnsResult) {
          { orderId ->
            setResult(RESULT_OK, Intent().putExtra(EXTRA_ORDER_ID, orderId))
            finish()
          }
        } else {
          null
        },
      crashOnFirstComposition = crashOnFirstComposition,
    )
  }
}

/**
 * Checkout in a translucent Activity. The Products Activity underneath stays visible, so it's
 * paused rather than stopped while checkout is open.
 */
class Nav3TranslucentCheckoutActivity : Nav3CheckoutActivity() {
  override val showAsSheet: Boolean = true
}

/** Shows [content] as a bottom sheet over a scrim, leaving the top of the screen see-through. */
@Composable
private fun CheckoutSheet(content: @Composable () -> Unit) {
  Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {
    Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f).align(Alignment.BottomCenter)) {
      content()
    }
  }
}

/** An extra Product Detail button, given the product's ID when clicked. */
internal class ProductDetailAction(
  val label: String,
  val testTag: String,
  val onClick: (productId: String) -> Unit,
)

@Composable
internal fun MultiActivityRoute(onOpenProducts: () -> Unit) {
  RouteScaffold(
    routeSpec = Nav3Route.MultiActivity.routeSpec(),
    testTagPrefix = nav3TestTag("route_multi_activity"),
    cardContent = {
      RouteButton(
        "Open products Activity",
        onClick = onOpenProducts,
        testTag = nav3TestTag("multi_activity_open_products"),
      )
    },
  )
}

/**
 * A self-contained Nav3 root (back stack, [SentryNavEffect], and [NavDisplay]) shared by the
 * Multi-Activity and Multi-Fragment samples, so each Activity or Fragment owns its own effect.
 *
 * @param backStack This root's back stack. Pass one to navigate it from outside the composition.
 * @param onCheckout Opens checkout outside this root (e.g., another Activity or Fragment). When
 *   `null`, checkout is pushed onto this root's own back stack.
 * @param productDetailActions Additional Product Detail buttons. Product Detail always offers a
 *   button that pushes a confirmation after a delay, so the push can happen while this root is
 *   covered by another one.
 * @param onCompleteOrder Handles Checkout's "Complete Order" outside this root (e.g., by returning
 *   a result). When `null`, the confirmation is pushed onto this root's own back stack.
 * @param crashOnFirstComposition Crashes while composing the Checkout destination, i.e., while the
 *   root's first screen is being created, to check that the crash is attributed to that screen.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun MultiRootNavDisplay(
  activity: ComponentActivity,
  title: String,
  initialRoute: Nav3Route,
  scenario: Nav3Scenario,
  configuration: NavigationSampleConfig,
  onFinish: () -> Unit,
  backStack: SnapshotStateList<Nav3Route> = rememberSaveableNav3BackStack(initialRoute),
  onCheckout: ((String) -> Unit)? = null,
  productDetailActions: List<ProductDetailAction> = emptyList(),
  onCompleteOrder: ((orderId: String) -> Unit)? = null,
  crashOnFirstComposition: Boolean = false,
) {
  val coroutineScope = rememberCoroutineScope()
  var routeWorkOptions by remember { mutableStateOf(setOf(RouteWorkOption.MANUAL_CHILD_SPAN)) }
  var showTransactionHistorySheet by remember { mutableStateOf(false) }
  var showCrashConfirmation by remember { mutableStateOf(false) }
  val options =
    remember(configuration) {
      SentryNavOptions {
        enableNavigationBreadcrumbs = configuration.enableNavigationBreadcrumbs
        enableNavigationTransactions = configuration.enableNavigationTransactions
        captureBackStack = configuration.captureBackStack
        maxCapturedBackStackEntries = configuration.maxCapturedBackStackEntries
      }
    }
  SentryNavEffect(
    backStack = backStack,
    backStackEntryMapper = { route ->
      SentryBackStackEntry(name = route.routeName, arguments = route.arguments)
    },
    options = options,
  )
  val onBack: () -> Unit = {
    if (backStack.size > 1) {
      backStack.removeLastOrNull()
    } else {
      onFinish()
    }
  }
  Scaffold(
    // Expose test tags as resource IDs so adb/uiautomator can target them.
    modifier =
      Modifier.fillMaxSize().safeDrawingPadding().semantics { testTagsAsResourceId = true },
    topBar = {
      Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
          )
          IconButton(
            onClick = { showTransactionHistorySheet = true },
            modifier =
              Modifier.sentryTag(nav3InteractionTag("Recent Transactions"))
                .testTag(nav3TestTag("top_bar_recent_transactions")),
          ) {
            Icon(Icons.Filled.AccountTree, contentDescription = "Recent transactions")
          }
          IconButton(
            onClick = {
              showRouteWorkDialog(activity, routeWorkOptions) { routeWorkOptions = it }
            },
            modifier =
              Modifier.sentryTag(nav3InteractionTag("Route Work Settings"))
                .testTag(nav3TestTag("top_bar_route_work_settings")),
          ) {
            Icon(Icons.Filled.Settings, contentDescription = "Route work settings")
          }
        }
        Text("Current route: ${backStack.last().displayRoute()}")
      }
    },
    bottomBar = {
      SentryControls(
        onCaptureException = { captureSampleException("Nav3 ${scenario.label}") },
        onCrashApp = { showCrashConfirmation = true },
      )
    },
  ) { padding ->
    // Only handle back while this root is resumed. Otherwise, a root that's still composed but no
    // longer current (e.g., a Fragment animating out, or a preloaded ViewPager2 page) could consume
    // back presses meant for the current root, popping a back stack the user can't see.
    val isResumed =
      LocalLifecycleOwner.current.lifecycle
        .currentStateAsState()
        .value
        .isAtLeast(Lifecycle.State.RESUMED)
    // Pass the parent explicitly: it's null while this root's view is detached (e.g., a recycled
    // ViewPager2 page that still recomposes), where the default parameter would throw.
    val parentDispatcherOwner = LocalNavigationEventDispatcherOwner.current
    CompositionLocalProvider(
      LocalNavigationEventDispatcherOwner provides
        rememberNavigationEventDispatcherOwner(enabled = isResumed, parent = parentDispatcherOwner)
    ) {
      NavDisplay(
        backStack = backStack,
        modifier = Modifier.fillMaxSize().padding(padding),
        onBack = onBack,
        entryProvider =
          entryProvider {
            entry<Nav3Route.ProductList> { route ->
              TracedNav3Route(route, scenario) {
                Nav3RouteWorkEffect(route, routeWorkOptions)
                ProductListRoute(backStack)
              }
            }
            entry<Nav3Route.ProductDetail> { route ->
              TracedNav3Route(route, scenario) {
                Nav3RouteWorkEffect(route, routeWorkOptions)
                ProductDetailRoute(
                  route = route,
                  backStack = backStack,
                  onCheckout = {
                    onCheckout?.invoke(route.productId)
                      ?: backStack.add(Nav3Route.Checkout(route.productId))
                  },
                  showOverlays = false,
                  extraActions = {
                    RouteButton(
                      "Push Confirmation in $DELAYED_NAVIGATION_SECONDS s",
                      onClick = {
                        coroutineScope.launch {
                          delay(DELAYED_NAVIGATION_SECONDS * 1_000L)
                          backStack += Nav3Route.Confirmation("delayed-${route.productId}")
                        }
                      },
                      testTag = nav3TestTag("product_detail_delayed_confirmation"),
                    )
                    productDetailActions.forEach { action ->
                      RouteButton(
                        action.label,
                        onClick = { action.onClick(route.productId) },
                        testTag = action.testTag,
                      )
                    }
                  },
                )
              }
            }
            entry<Nav3Route.Checkout> { route ->
              if (crashOnFirstComposition) {
                throw RuntimeException(
                  "Fatal Nav3 ${scenario.label} crash during first composition"
                )
              }
              TracedNav3Route(route, scenario) {
                Nav3RouteWorkEffect(route, routeWorkOptions)
                CheckoutRoute(
                  route = route,
                  backStack = backStack,
                  onCompleteOrder = onCompleteOrder ?: { backStack += Nav3Route.Confirmation(it) },
                )
              }
            }
            entry<Nav3Route.Confirmation> { route ->
              TracedNav3Route(route, scenario) {
                Nav3RouteWorkEffect(route, routeWorkOptions)
                ConfirmationRoute(route, backStack, rootRoute = initialRoute)
              }
            }
          },
      )
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
            crashSampleApp("Nav3 ${scenario.label}")
          }
        ) {
          Text("Crash")
        }
      },
    )
  }
  if (showTransactionHistorySheet) {
    NavigationTransactionHistorySheet(
      sampleName = "Nav3",
      transactions = NavigationTransactionHistory.transactions,
      showActivityUiLoadTransactionDelayMessage = configuration.hasOnlyActivityUiLoadTransactions,
      onDismissRequest = { showTransactionHistorySheet = false },
      onOpenTransaction = { url ->
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
      },
      onDumpTransactionUrl = { url ->
        Log.i("Nav3MultiActivity", "Sentry transaction URL: $url")
        Toast.makeText(activity, "Dumped transaction URL to logcat.", Toast.LENGTH_SHORT).show()
      },
      onCopyTransactionUrl = { url ->
        val clipboard = activity.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Sentry transaction URL", url))
        Toast.makeText(activity, "Copied transaction URL to clipboard.", Toast.LENGTH_SHORT).show()
      },
    )
  }
}

private const val DELAYED_NAVIGATION_SECONDS = 5
private const val EXTRA_PRODUCT_ID = "io.sentry.samples.android.navigation.nav3.product_id"
private const val EXTRA_RETURN_RESULT = "io.sentry.samples.android.navigation.nav3.return_result"
private const val EXTRA_ORDER_ID = "io.sentry.samples.android.navigation.nav3.order_id"
private const val EXTRA_CRASH_ON_FIRST_COMPOSITION =
  "io.sentry.samples.android.navigation.nav3.crash_on_first_composition"
