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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import io.sentry.compose.SentryModifier.sentryTag
import io.sentry.compose.navigation3.SentryBackStackEntry
import io.sentry.compose.navigation3.SentryNavEffect
import io.sentry.compose.navigation3.SentryNavOptions
import io.sentry.samples.android.navigation.common.NavigationSampleConfig
import io.sentry.samples.android.navigation.common.RouteWorkOption
import io.sentry.samples.android.navigation.common.hasOnlyActivityUiLoadTransactions
import io.sentry.samples.android.navigation.common.showRouteWorkDialog

/** Keeps its navigation effect alive while checkout runs in another Activity. */
class Nav3ProductsActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val configuration = (intent.extras ?: Bundle()).nav3SampleConfiguration()
    if (!configuration.enableActivityUiLoadTransaction) {
      cancelCurrentActivityUiLoadTransaction()
    }
    setContent {
      Nav3SampleTheme {
        MultiActivityNavDisplay(
          activity = this,
          initialRoute = Nav3Route.ProductList,
          configuration = configuration,
          onFinish = { finish() },
          onCheckout = { productId ->
            startActivity(
              Intent(this, Nav3CheckoutActivity::class.java)
                .putExtra(EXTRA_PRODUCT_ID, productId)
                .putExtras(Bundle().apply { putNav3SampleConfiguration(configuration) })
            )
          },
        )
      }
    }
  }
}

/** Owns the checkout back stack independently of the Products Activity. */
class Nav3CheckoutActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val configuration = (intent.extras ?: Bundle()).nav3SampleConfiguration()
    val productId = intent.getStringExtra(EXTRA_PRODUCT_ID) ?: "42"
    if (!configuration.enableActivityUiLoadTransaction) {
      cancelCurrentActivityUiLoadTransaction()
    }
    setContent {
      Nav3SampleTheme {
        MultiActivityNavDisplay(
          activity = this,
          initialRoute = Nav3Route.Checkout(productId),
          configuration = configuration,
          onFinish = { finish() },
        )
      }
    }
  }
}

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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MultiActivityNavDisplay(
  activity: ComponentActivity,
  initialRoute: Nav3Route,
  configuration: NavigationSampleConfig,
  onFinish: () -> Unit,
  onCheckout: (String) -> Unit = {},
) {
  val backStack = rememberSaveableNav3BackStack(initialRoute)
  var routeWorkOptions by remember { mutableStateOf(setOf(RouteWorkOption.MANUAL_CHILD_SPAN)) }
  var showTransactionHistorySheet by remember { mutableStateOf(false) }
  var showCrashConfirmation by remember { mutableStateOf(false) }
  val transactionHistory =
    remember(activity) {
      NavigationTransactionHistory {
        activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
      }
    }
  DisposableEffect(transactionHistory) {
    transactionHistory.install()
    onDispose { transactionHistory.uninstall() }
  }
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
    modifier = Modifier.fillMaxSize().safeDrawingPadding(),
    topBar = {
      Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(
            if (initialRoute is Nav3Route.Checkout) "Checkout Activity" else "Products Activity",
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
        onCaptureException = { captureSampleException("Nav3 Multi-Activity") },
        onCrashApp = { showCrashConfirmation = true },
      )
    },
  ) { padding ->
    NavDisplay(
      backStack = backStack,
      modifier = Modifier.fillMaxSize().padding(padding),
      onBack = onBack,
      entryProvider =
        entryProvider {
          entry<Nav3Route.ProductList> { route ->
            TracedNav3Route(route, Nav3Scenario.MULTI_ACTIVITY) {
              Nav3RouteWorkEffect(route, routeWorkOptions)
              ProductListRoute(backStack)
            }
          }
          entry<Nav3Route.ProductDetail> { route ->
            TracedNav3Route(route, Nav3Scenario.MULTI_ACTIVITY) {
              Nav3RouteWorkEffect(route, routeWorkOptions)
              ProductDetailRoute(
                route = route,
                backStack = backStack,
                onCheckout = { onCheckout(route.productId) },
                showOverlays = false,
              )
            }
          }
          entry<Nav3Route.Checkout> { route ->
            TracedNav3Route(route, Nav3Scenario.MULTI_ACTIVITY) {
              Nav3RouteWorkEffect(route, routeWorkOptions)
              CheckoutRoute(route, backStack)
            }
          }
          entry<Nav3Route.Confirmation> { route ->
            TracedNav3Route(route, Nav3Scenario.MULTI_ACTIVITY) {
              Nav3RouteWorkEffect(route, routeWorkOptions)
              ConfirmationRoute(route, backStack, rootRoute = initialRoute)
            }
          }
        },
    )
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
            crashSampleApp("Nav3 Multi-Activity")
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
      transactions = transactionHistory.transactions,
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

private const val EXTRA_PRODUCT_ID = "io.sentry.samples.android.navigation.nav3.product_id"
