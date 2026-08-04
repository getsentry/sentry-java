package io.sentry.samples.android.navigation.nav3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.sentry.Sentry
import io.sentry.compose.SentryModifier.sentryTag
import io.sentry.compose.SentryTraced
import io.sentry.samples.android.GithubAPI
import io.sentry.samples.android.R
import io.sentry.samples.android.navigation.common.RouteNames
import io.sentry.samples.android.navigation.common.RouteSpec
import io.sentry.samples.android.navigation.common.RouteSpecs
import io.sentry.samples.android.navigation.common.RouteWorkOption
import io.sentry.samples.android.navigation.common.SENTRY_FLUSH_TIMEOUT_MILLIS
import io.sentry.samples.android.navigation.common.cancelCurrentActivityUiLoadTransaction
import io.sentry.samples.android.navigation.common.displayArguments
import io.sentry.samples.android.navigation.common.emitSampleNavigationSpan
import io.sentry.samples.android.navigation.common.recordSimulatedBackgroundSpan
import io.sentry.samples.android.navigation.common.tagCurrentNavigationSampleScenario
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

@ExperimentalComposeUiApi
@Composable
internal fun TracedNav3Route(
  route: Nav3Route,
  scenario: Nav3Scenario,
  content: @Composable BoxScope.() -> Unit,
) {
  tagCurrentNav3Scenario(scenario)
  SentryTraced(
    tag = "Nav3 /${route.routeName}",
    enableUserInteractionTracing = false,
    content = content,
  )
}

private fun tagCurrentNav3Scenario(scenario: Nav3Scenario) {
  Sentry.getSpan()?.setTag("sample_nav3_scenario", scenario.label)
  tagCurrentNavigationSampleScenario(scenario.label)
}

@Composable
internal fun Nav3RouteWorkEffect(
  route: Nav3Route,
  routeWorkOptions: Set<RouteWorkOption>,
) {
  val currentOptions = rememberUpdatedState(routeWorkOptions)

  if (RouteWorkOption.MANUAL_CHILD_SPAN in currentOptions.value) {
    // Keep this synchronous to verify that Nav3 route transactions are bound before destination
    // composition runs, not merely before destination effects are launched.
    runManualNav3RouteActivationSpan(route)
  }

  LaunchedEffect(route) {
    runNav3RouteWork(
      route = route,
      options = currentOptions.value,
    )
  }
}

private suspend fun runNav3RouteWork(
  route: Nav3Route,
  options: Set<RouteWorkOption>,
) {
  RouteWorkOption.entries.forEach { option ->
    if (option !in options || option == RouteWorkOption.MANUAL_CHILD_SPAN) {
      return@forEach
    }

    tagNav3SampleAction(option.tagName, route)
    when (option) {
      RouteWorkOption.HTTP_REQUEST -> {
        try {
          GithubAPI.runRouteWorkRequest()
        } catch (e: IOException) {
          Sentry.captureException(e)
        } catch (e: HttpException) {
          Sentry.captureException(e)
        } finally {
          withContext(Dispatchers.IO) { Sentry.flush(SENTRY_FLUSH_TIMEOUT_MILLIS) }
        }
      }
      RouteWorkOption.MANUAL_CHILD_SPAN -> Unit
    }
  }
}

internal fun captureSampleException(navName: String) {
  Sentry.captureException(RuntimeException("$navName sample exception button"))
  Thread { Sentry.flush(SENTRY_FLUSH_TIMEOUT_MILLIS) }.start()
}

@Composable
internal fun SentryControls(
  onCaptureException: () -> Unit,
  onCrashApp: () -> Unit,
) {
  Surface(shadowElevation = 8.dp) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Nav3SentryButton(
        label = "Capture Exception",
        onClick = onCaptureException,
        modifier = Modifier.weight(1f),
      )
      Nav3SentryButton(
        label = "Crash App",
        onClick = onCrashApp,
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
internal fun Nav3SentryButton(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  interactionLabel: String = label,
) {
  Button(
    onClick = onClick,
    modifier = modifier.sentryTag(nav3InteractionTag(interactionLabel)),
    colors =
      ButtonDefaults.buttonColors(
        containerColor = colorResource(R.color.colorAccentSoft),
        contentColor = Color.White,
      ),
  ) {
    Text(label)
  }
}

internal fun crashSampleApp(navName: String): Nothing {
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

private fun tagNav3SampleAction(action: String, route: Nav3Route) {
  Sentry.setTag("sample_action", "nav3_$action")
  Sentry.setTag("sample_nav3_route", route.routeName)
}

@Composable
internal fun RouteScaffold(
  routeSpec: RouteSpec,
  cardContent: (@Composable ColumnScope.() -> Unit)? = null,
  footerContent: (@Composable ColumnScope.() -> Unit)? = null,
  content: (@Composable ColumnScope.() -> Unit)? = null,
) {
  Column(
    modifier =
      Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)
  ) {
    Column(
      modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        routeSpec.title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
      )
      routeSpec.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
      if (cardContent != null) {
        Card(
          colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            cardContent()
          }
        }
      }
      content?.invoke(this)
    }

    if (footerContent != null) {
      Spacer(Modifier.size(12.dp))
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { footerContent() }
    }
  }
}

@Composable
internal fun RouteButton(label: String, onClick: () -> Unit) {
  Button(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().sentryTag(nav3InteractionTag(label)),
  ) {
    Text(label)
  }
}

@Composable
internal fun RouteInfo(label: String, value: String) {
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

@Composable
internal fun SingleStackRoute(backStack: SnapshotStateList<Nav3Route>) {
  RouteScaffold(routeSpec = RouteSpecs.home) {
    RouteButton("Browse Products") { backStack.add(Nav3Route.ProductList) }
  }
}

@Composable
internal fun CustomRoute(
  mode: Nav3CustomTransactionMode,
  onModeSelected: (Nav3CustomTransactionMode) -> Unit,
  isAsyncBrowseProductsRunning: Boolean,
  onBrowseProducts: () -> Unit,
) {
  RouteScaffold(
    routeSpec = Nav3Route.Custom.routeSpec(),
    cardContent = {
      Nav3CustomTransactionModeSelector(selected = mode, onSelected = onModeSelected)
      RouteInfo("Selected mode", mode.label)
      Text(mode.description, style = MaterialTheme.typography.bodyMedium)
      RouteButton(
        label =
          if (mode == Nav3CustomTransactionMode.ASYNC_FROM_USER_ACTION) {
            if (isAsyncBrowseProductsRunning) {
              "Starting async custom transaction..."
            } else {
              "Browse Products via Async Custom Transaction"
            }
          } else {
            "Browse Products"
          },
        onClick = onBrowseProducts,
      )
    },
    footerContent = {
      if (mode == Nav3CustomTransactionMode.LINGERING) {
        Text(
          "The lingering transaction stays active until you leave the Custom tab.",
          style = MaterialTheme.typography.bodySmall,
        )
      }
    },
  ) {
    if (mode == Nav3CustomTransactionMode.ASYNC_FROM_USER_ACTION) {
      Text(
        "This mode starts a manual transaction from the button tap, waits for async work, and " +
          "then pushes Product List.",
        style = MaterialTheme.typography.bodyMedium,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Nav3CustomTransactionModeSelector(
  selected: Nav3CustomTransactionMode,
  onSelected: (Nav3CustomTransactionMode) -> Unit,
) {
  Text("Mode", style = MaterialTheme.typography.titleSmall)
  SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
    Nav3CustomTransactionMode.entries.forEachIndexed { index, mode ->
      SegmentedButton(
        shape =
          SegmentedButtonDefaults.itemShape(
            index = index,
            count = Nav3CustomTransactionMode.entries.size,
          ),
        onClick = { onSelected(mode) },
        selected = selected == mode,
        icon = {},
        label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
      )
    }
  }
}

@Composable
internal fun LandingRoute() {
  LaunchedEffect(Unit) { cancelCurrentActivityUiLoadTransaction() }
  RouteScaffold(routeSpec = RouteSpecs.landing)
}

@Composable
internal fun DeepLinkRoute(backStack: SnapshotStateList<Nav3Route>) {
  RouteScaffold(
    routeSpec = Nav3Route.DeepLink.routeSpec(),
    cardContent = {
      RouteButton("Go to deep link destination") { backStack.openSyntheticProductDeepLink() }
    },
  )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ProductListRoute(backStack: SnapshotStateList<Nav3Route>) {
  var showProductItems by rememberSaveable { mutableStateOf(false) }

  RouteScaffold(
    routeSpec = RouteSpecs.productList,
    cardContent = {
      SentryTraced(
        tag = "product_list_actions",
        modifier = Modifier.fillMaxWidth(),
        enableUserInteractionTracing = false,
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          RouteButton("Open Product 42") {
            backStack.add(
              Nav3Route.ProductDetail(
                productId = "42",
                source = "product-list",
                campaign = "summer-sale",
              )
            )
          }
          RouteButton("Open Product 7") {
            backStack.add(Nav3Route.ProductDetail(productId = "7", source = "product-list"))
          }
        }
      }
    },
    content = {
      if (showProductItems) {
        SentryTraced(
          tag = "product_list_items",
          modifier = Modifier.fillMaxWidth(),
          enableUserInteractionTracing = false,
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(PRODUCT_LIST_ITEM_COUNT) { index -> Nav3ProductListItem(index + 1) }
          }
        }
      }
    },
    footerContent = {
      RouteButton(
        label = if (showProductItems) "Hide Product Items" else "Show Product Items",
        onClick = { showProductItems = !showProductItems },
      )
    },
  )
}

@Composable
internal fun ProductDetailRoute(
  route: Nav3Route.ProductDetail,
  backStack: SnapshotStateList<Nav3Route>,
) {
  LaunchedEffect(route.productId, route.source, route.campaign) {
    recordSimulatedBackgroundSpan(RouteNames.PRODUCT_DETAIL, "Nav3")
  }

  RouteScaffold(
    routeSpec = RouteSpecs.productDetail,
    cardContent = {
      RouteSpecs.productDetail.displayArguments(route.arguments).forEach { (label, value) ->
        RouteInfo(label, value)
      }
      RouteButton("Show Promo Dialog") {
        backStack.add(Nav3Route.PromoDialog("detail-${route.productId}"))
      }
      RouteButton("Open Share Sheet") { backStack.add(Nav3Route.ShareSheet(route.productId)) }
      RouteButton("Go to Checkout") { backStack.add(Nav3Route.Checkout(route.productId)) }
    },
    footerContent = {
      RouteButton("Emit a span") { emitSampleNavigationSpan(route.routeName, "Nav3") }
    },
  )
}

@Composable
internal fun CheckoutRoute(
  route: Nav3Route.Checkout,
  backStack: SnapshotStateList<Nav3Route>,
) {
  RouteScaffold(routeSpec = RouteSpecs.checkout) {
    RouteSpecs.checkout.displayArguments(route.arguments).forEach { (label, value) ->
      RouteInfo(label, value)
    }
    RouteButton("Complete Order") {
      backStack.add(Nav3Route.Confirmation(orderId = "order-${route.productId}"))
    }
  }
}

@Composable
internal fun ConfirmationRoute(
  route: Nav3Route.Confirmation,
  backStack: SnapshotStateList<Nav3Route>,
  rootRoute: Nav3Route,
) {
  RouteScaffold(routeSpec = RouteSpecs.confirmation) {
    RouteSpecs.confirmation.displayArguments(route.arguments).forEach { (label, value) ->
      RouteInfo(label, value)
    }
    RouteButton("Reset Backstack") { backStack.resetTo(rootRoute) }
  }
}

@Composable
internal fun PromoDialogRoute(
  route: Nav3Route.PromoDialog,
  backStack: SnapshotStateList<Nav3Route>,
  onCaptureException: () -> Unit,
  onCrashApp: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
  ) {
    Column(
      modifier = Modifier.padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      val routeSpec = RouteSpecs.promoDialog
      Text(routeSpec.title, style = MaterialTheme.typography.headlineSmall)
      routeSpec.description?.let { Text(it) }
      routeSpec.displayArguments(route.arguments).forEach { (label, value) ->
        Text("$label=$value")
      }
      Nav3SentryButton(
        label = "Capture Exception",
        onClick = onCaptureException,
        modifier = Modifier.fillMaxWidth(),
        interactionLabel = "Promo Dialog Exception",
      )
      Nav3SentryButton(
        label = "Crash App",
        onClick = onCrashApp,
        modifier = Modifier.fillMaxWidth(),
        interactionLabel = "Promo Dialog Crash App",
      )
      Button(
        onClick = { backStack.removeLastOrNull() },
        modifier = Modifier.fillMaxWidth().sentryTag(nav3InteractionTag("Promo Dialog Dismiss")),
      ) {
        Text("Dismiss")
      }
    }
  }
}

@Composable
internal fun ShareSheetRoute(
  route: Nav3Route.ShareSheet,
  backStack: SnapshotStateList<Nav3Route>,
  onCaptureException: () -> Unit,
  onCrashApp: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    val routeSpec = RouteSpecs.shareSheet
    Text(routeSpec.title, style = MaterialTheme.typography.headlineSmall)
    routeSpec.description?.let { Text(it) }
    routeSpec.displayArguments(route.arguments).forEach { (label, value) -> Text("$label=$value") }
    Nav3SentryButton(
      label = "Capture Exception",
      onClick = onCaptureException,
      modifier = Modifier.fillMaxWidth(),
      interactionLabel = "Share Sheet Exception",
    )
    Nav3SentryButton(
      label = "Crash App",
      onClick = onCrashApp,
      modifier = Modifier.fillMaxWidth(),
      interactionLabel = "Share Sheet Crash App",
    )
    Button(
      onClick = { backStack.removeLastOrNull() },
      modifier = Modifier.fillMaxWidth().sentryTag(nav3InteractionTag("Share Sheet Done")),
    ) {
      Text("Done")
    }
    Spacer(Modifier.size(12.dp))
  }
}

@Composable
internal fun FutureRoute(routeName: String, scenario: String) {
  RouteScaffold(
    routeSpec =
      RouteSpecs.get(routeName)
        .copy(
          title = "$routeName: WIP",
          description =
            "Reserved for a future milestone when SentryNavEffect supports $scenario navigation " +
              "state.",
        )
  )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Nav3ProductListItem(index: Int) {
  SentryTraced(
    tag = "product_list_item_$index",
    modifier = Modifier.fillMaxWidth(),
    enableUserInteractionTracing = false,
  ) {
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      modifier = Modifier.fillMaxWidth(),
    ) {
      androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text("Product #$index", fontWeight = FontWeight.Bold)
        Text("SKU-$index")
      }
    }
  }
}

private const val PRODUCT_LIST_ITEM_COUNT = 20
