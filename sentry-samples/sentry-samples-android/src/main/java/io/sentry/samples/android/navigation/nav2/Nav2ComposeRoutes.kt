package io.sentry.samples.android.navigation.nav2

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog as ComposeAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.sentry.Sentry
import io.sentry.android.navigation.SentryNavigationListener
import io.sentry.compose.SentryModifier.sentryTag
import io.sentry.compose.SentryTraced
import io.sentry.compose.withSentryObservableEffect
import io.sentry.samples.android.R
import io.sentry.samples.android.navigation.common.NavArgs
import io.sentry.samples.android.navigation.common.RouteNames
import io.sentry.samples.android.navigation.common.RouteSpec
import io.sentry.samples.android.navigation.common.RouteSpecs
import io.sentry.samples.android.navigation.common.RouteWorkApi
import io.sentry.samples.android.navigation.common.RouteWorkOption
import io.sentry.samples.android.navigation.common.displayArguments
import io.sentry.samples.android.navigation.common.displayRoute
import io.sentry.samples.android.navigation.common.toDisplayString
import io.sentry.samples.android.navigation.nav2.Nav2ComposeDestination.Checkout
import io.sentry.samples.android.navigation.nav2.Nav2ComposeDestination.Confirmation
import io.sentry.samples.android.navigation.nav2.Nav2ComposeDestination.Custom
import io.sentry.samples.android.navigation.nav2.Nav2ComposeDestination.Home
import io.sentry.samples.android.navigation.nav2.Nav2ComposeDestination.ProductDetail
import io.sentry.samples.android.navigation.nav2.Nav2ComposeDestination.ProductList
import io.sentry.samples.android.navigation.nav2.Nav2ComposeDestination.PromoDialog
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

@Composable
internal fun Nav2ComposeApp(
  navListener: SentryNavigationListener,
  routeWorkOptions: Set<RouteWorkOption>,
  onCaptureException: () -> Unit,
  onCrashApp: () -> Unit,
  selectedScenario: Nav2Scenario,
  onRouteChanged: (routeName: String, currentRoute: String, backStack: String) -> Unit,
  onExitRoot: () -> Unit,
) {

  val navController = rememberNavController().withSentryObservableEffect(navListener = navListener)
  val backStack = rememberSaveableNav2ComposeBackStack()
  val shareSheetProductId = rememberSaveable { mutableStateOf<String?>(null) }
  val currentDestination = backStack.lastOrNull() ?: Home
  var customTransactionMode by rememberSaveable {
    mutableStateOf(Nav2CustomTransactionMode.PER_SCREEN)
  }
  var asyncBrowseProductsJob by rememberSaveable { mutableStateOf<Job?>(null) }
  var isAsyncBrowseProductsRunning by rememberSaveable { mutableStateOf(false) }
  val customTransactionsScope = androidx.compose.runtime.rememberCoroutineScope()
  val customTransactionController =
    androidx.compose.runtime.remember { Nav2CustomTransactionController() }

  fun navigateTo(destination: Nav2ComposeDestination) {
    backStack.add(destination)
    navController.navigate(destination.route)
  }

  fun navigateBack() {
    backStack.popTrackedBackStack { navController.popBackStack() }
  }

  fun openShareSheet(productId: String) {
    shareSheetProductId.value = productId
  }

  fun dismissShareSheet() {
    if (shareSheetProductId.value == null) {
      return
    }
    shareSheetProductId.value = null
  }

  fun resetToHome() {
    backStack.resetTo(Home)
    shareSheetProductId.value = null
    navController.navigate(Home.route) {
      popUpTo(Home.route) { inclusive = false }
      launchSingleTop = true
    }
  }

  LaunchedEffect(selectedScenario) {
    when (selectedScenario) {
      Nav2Scenario.COMPOSE -> {
        customTransactionController.cleanup()
        backStack.resetTo(Home)
        shareSheetProductId.value = null
        navController.navigate(Home.route) {
          popUpTo(Home.route) { inclusive = true }
          launchSingleTop = true
        }
      }
      Nav2Scenario.CUSTOM -> {
        backStack.resetTo(Custom)
        shareSheetProductId.value = null
        navController.navigate(Custom.route) {
          popUpTo(Home.route) { inclusive = true }
          launchSingleTop = true
        }
      }
      else -> Unit
    }
  }

  LaunchedEffect(currentDestination, customTransactionMode) {
    if (
      currentDestination != Custom &&
        customTransactionMode == Nav2CustomTransactionMode.ASYNC_FROM_USER_ACTION
    ) {
      asyncBrowseProductsJob?.cancel()
      asyncBrowseProductsJob = null
      isAsyncBrowseProductsRunning = false
    }
  }

  LaunchedEffect(currentDestination, backStack.size) {
    onRouteChanged(
      currentDestination.routeName,
      currentDestination.displayRoute(),
      backStack.toComposeBackStackText(),
    )
  }

  RouteWorkEffect(
    destination = currentDestination,
    routeWorkOptions = routeWorkOptions,
  )

  Nav2CustomTransactionEffect(
    selectedDestination = currentDestination,
    mode = customTransactionMode,
    controller = customTransactionController,
  )

  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(modifier = Modifier.fillMaxSize()) {
      NavHost(
        navController = navController,
        startDestination = Home.route,
        modifier = Modifier.weight(1f),
        enterTransition = { fadeIn(animationSpec = tween(COMPOSE_ROUTE_TRANSITION_MILLIS)) },
        exitTransition = { fadeOut(animationSpec = tween(COMPOSE_ROUTE_TRANSITION_MILLIS)) },
        popEnterTransition = { fadeIn(animationSpec = tween(COMPOSE_ROUTE_TRANSITION_MILLIS)) },
        popExitTransition = { fadeOut(animationSpec = tween(COMPOSE_ROUTE_TRANSITION_MILLIS)) },
      ) {
        composable(Home.route) {
          TracedNav2ComposeRoute(Home.routeName) {
            Nav2ComposeHomeRoute(routeSpec = RouteSpecs.home) { navigateTo(ProductList) }
          }
        }

        composable(Custom.route) {
          TracedNav2ComposeRoute(Custom.routeName) {
            Nav2ComposeCustomRoute(
              routeSpec = RouteSpecs.custom,
              mode = customTransactionMode,
              onModeSelected = { customTransactionMode = it },
              isAsyncBrowseProductsRunning = isAsyncBrowseProductsRunning,
              onBrowseProducts = {
                if (customTransactionMode == Nav2CustomTransactionMode.ASYNC_FROM_USER_ACTION) {
                  if (!isAsyncBrowseProductsRunning) {
                    customTransactionController.startAsyncBrowseProductsTransaction()
                    isAsyncBrowseProductsRunning = true
                    asyncBrowseProductsJob = customTransactionsScope.launch {
                      val span =
                        Sentry.getSpan()
                          ?.startChild(
                            "test.navigation.async_browse_products",
                            "Nav2 Custom async browse products",
                          )
                      try {
                        delay(250)
                        navigateTo(ProductList)
                      } finally {
                        span?.finish()
                        isAsyncBrowseProductsRunning = false
                        asyncBrowseProductsJob = null
                      }
                    }
                  }
                } else {
                  navigateTo(ProductList)
                }
              },
            )
          }
        }

        composable(ProductList.route) {
          TracedNav2ComposeRoute(ProductList.routeName) {
            Nav2ComposeProductListRoute(
              routeSpec = RouteSpecs.productList,
              onOpenProduct42 = {
                navigateTo(
                  ProductDetail(
                    productId = "42",
                    source = "product-list",
                    campaign = "summer-sale",
                  )
                )
              },
              onOpenProduct7 = {
                navigateTo(ProductDetail(productId = "7", source = "product-list"))
              },
            )
          }
        }

        composable(
          route = Nav2ComposeDestination.PRODUCT_DETAIL_ROUTE,
          arguments =
            listOf(
              navArgument(NavArgs.PRODUCT_ID) { type = NavType.StringType },
              navArgument(NavArgs.SOURCE) { type = NavType.StringType },
              navArgument(NavArgs.CAMPAIGN) {
                type = NavType.StringType
                defaultValue = ""
              },
            ),
        ) { entry ->
          val productId = entry.arguments?.getString(NavArgs.PRODUCT_ID).orEmpty()
          val source = entry.arguments?.getString(NavArgs.SOURCE).orEmpty()
          val campaign = entry.arguments?.getString(NavArgs.CAMPAIGN).orEmpty()
          TracedNav2ComposeRoute(RouteNames.PRODUCT_DETAIL) {
            Nav2ComposeProductDetailRoute(
              routeSpec = RouteSpecs.productDetail,
              productId = productId,
              source = source,
              campaign = campaign,
              onShowPromoDialog = {
                navigateTo(PromoDialog("detail-$productId"))
              },
              onOpenShareSheet = { openShareSheet(productId) },
              onCheckout = { navigateTo(Checkout(productId)) },
            )
          }
        }

        composable(
          route = Nav2ComposeDestination.CHECKOUT_ROUTE,
          arguments = listOf(navArgument(NavArgs.PRODUCT_ID) { type = NavType.StringType }),
        ) { entry ->
          val productId = entry.arguments?.getString(NavArgs.PRODUCT_ID).orEmpty()
          TracedNav2ComposeRoute(RouteNames.CHECKOUT) {
            Nav2ComposeCheckoutRoute(
              routeSpec = RouteSpecs.checkout,
              productId = productId,
              onCompleteOrder = {
                navigateTo(Confirmation(orderId = "order-$productId"))
              },
            )
          }
        }

        composable(
          route = Nav2ComposeDestination.CONFIRMATION_ROUTE,
          arguments = listOf(navArgument(NavArgs.ORDER_ID) { type = NavType.StringType }),
        ) { entry ->
          TracedNav2ComposeRoute(RouteNames.CONFIRMATION) {
            Nav2ComposeConfirmationRoute(
              routeSpec = RouteSpecs.confirmation,
              orderId = entry.arguments?.getString(NavArgs.ORDER_ID).orEmpty(),
              onResetBackStack = { resetToHome() },
            )
          }
        }

        dialog(
          route = Nav2ComposeDestination.PROMO_DIALOG_ROUTE,
          arguments = listOf(navArgument(NavArgs.PROMO_ID) { type = NavType.StringType }),
        ) { entry ->
          // This dialog is a real Nav destination, so it participates in Nav2 the same way as the
          // rest of the route graph. Compare it with the share sheet overlay below when inspecting
          // Sentry's Nav2 breadcrumbs, destination arguments, and route transactions.
          TracedNav2ComposeRoute(RouteNames.PROMO_DIALOG) {
            Nav2ComposePromoDialogRoute(
              routeSpec = RouteSpecs.promoDialog,
              promoId = entry.arguments?.getString(NavArgs.PROMO_ID).orEmpty(),
              onCaptureException = onCaptureException,
              onCrashApp = onCrashApp,
              onDismiss = { navigateBack() },
            )
          }
        }
      }

      // These BackHandlers are intentionally declared AFTER NavHost. NavHost installs its own
      // internal BackHandler that pops the real NavController; if ours ran second it would let
      // NavHost silently pop the controller while this sample's tracked back stack (which drives
      // the header and the root-exit decision) went stale. Composing ours last gives it priority in
      // the OnBackPressedDispatcher, so the tracked list and the NavController are only ever moved
      // together, and backing out of the root reliably exits the activity.
      BackHandler(enabled = shareSheetProductId.value != null) { dismissShareSheet() }
      BackHandler(enabled = shareSheetProductId.value == null) {
        if (backStack.size > 1) {
          navigateBack()
        } else {
          onExitRoot()
        }
      }

      shareSheetProductId.value?.let { productId ->
        // This share sheet is intentionally just a screen overlay, not a Nav destination. It lets
        // the sample compare how Sentry's Nav2 integration behaves for proper Nav destinations vs.
        // UI layered on top of the current route.
        Nav2ComposeShareSheetRoute(
          routeSpec = RouteSpecs.shareSheet,
          productId = productId,
          onCaptureException = onCaptureException,
          onCrashApp = onCrashApp,
          onDone = ::dismissShareSheet,
        )
      }
    }
  }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TracedNav2ComposeRoute(routeName: String, content: @Composable BoxScope.() -> Unit) {
  tagCurrentNav2Scenario(Nav2Scenario.COMPOSE)
  SentryTraced(
    tag = "Nav2 /$routeName",
    // Keep interaction tagging off here so route wrappers do not turn every Compose click into a
    // generic route-level interaction transaction.
    enableUserInteractionTracing = false,
    content = content,
  )
}

@Composable
private fun RouteWorkEffect(
  destination: Nav2ComposeDestination,
  routeWorkOptions: Set<RouteWorkOption>,
) {
  val currentOptions = rememberUpdatedState(routeWorkOptions)

  if (RouteWorkOption.MANUAL_CHILD_SPAN in currentOptions.value) {
    // Keep this synchronous to verify that Nav2 route transactions are bound before destination
    // composition runs, not merely before destination effects are launched.
    recordManualChildSpan(destination.routeName)
  }

  LaunchedEffect(destination) {
    runRouteWork(
      routeName = destination.routeName,
      options = currentOptions.value,
    )
  }
}

private suspend fun runRouteWork(
  routeName: String,
  options: Set<RouteWorkOption>,
) {
  RouteWorkOption.entries.forEach { option ->
    if (option !in options || option == RouteWorkOption.MANUAL_CHILD_SPAN) {
      return@forEach
    }

    tagNav2SampleAction(option.tagName, routeName)

    when (option) {
      RouteWorkOption.HTTP_REQUEST -> {
        try {
          RouteWorkApi.runRequest()
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

@Composable
private fun Nav2ComposeHomeRoute(routeSpec: RouteSpec, onBrowseProducts: () -> Unit) {
  Nav2ComposeActionRoute(routeSpec, buttons = listOf("Browse Products" to onBrowseProducts))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Nav2ComposeCustomRoute(
  routeSpec: RouteSpec,
  mode: Nav2CustomTransactionMode,
  onModeSelected: (Nav2CustomTransactionMode) -> Unit,
  isAsyncBrowseProductsRunning: Boolean,
  onBrowseProducts: () -> Unit,
) {
  val helperText =
    when (mode) {
      Nav2CustomTransactionMode.PER_SCREEN ->
        "Starts a custom transaction for every destination so route work runs under app-owned screen-level transactions."
      Nav2CustomTransactionMode.WHOLE_FLOW ->
        "Keeps one custom transaction open for the whole shopping journey until the flow returns to the Custom home screen."
      Nav2CustomTransactionMode.ASYNC_FROM_USER_ACTION ->
        "This mode starts a manual transaction from the button tap, waits for async work, and then pushes Product List."
      Nav2CustomTransactionMode.LINGERING ->
        "The lingering transaction stays active until you leave the Custom tab."
    }
  val sentryPink = colorResource(R.color.colorAccent)

  Nav2ComposeRouteScaffold(
    routeSpec = routeSpec,
    cardContent = {
      Text("Mode", style = MaterialTheme.typography.titleSmall)
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        Nav2CustomTransactionMode.entries.forEachIndexed { index, entry ->
          SegmentedButton(
            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 72.dp),
            shape =
              SegmentedButtonDefaults.itemShape(
                index = index,
                count = Nav2CustomTransactionMode.entries.size,
              ),
            onClick = { onModeSelected(entry) },
            selected = mode == entry,
            colors =
              SegmentedButtonDefaults.colors(
                activeContainerColor = sentryPink,
                activeContentColor = Color.White,
              ),
            icon = {},
            label = {
              Text(
                text = entry.label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
              )
            },
          )
        }
      }
      Text(mode.description, style = MaterialTheme.typography.bodyMedium)
      Nav2ComposeRouteButton(
        label =
          if (
            mode == Nav2CustomTransactionMode.ASYNC_FROM_USER_ACTION && isAsyncBrowseProductsRunning
          ) {
            "Starting async custom transaction..."
          } else {
            "Browse Products"
          },
        onClick = onBrowseProducts,
      )
    },
    content = { Text(helperText, style = MaterialTheme.typography.bodyMedium) },
  )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Nav2ComposeProductListRoute(
  routeSpec: RouteSpec,
  onOpenProduct42: () -> Unit,
  onOpenProduct7: () -> Unit,
) {
  var showProductItems by rememberSaveable { mutableStateOf(false) }

  Nav2ComposeRouteScaffold(
    routeSpec = routeSpec,
    cardContent = {
      SentryTraced(
        tag = "product_list_actions",
        modifier = Modifier.fillMaxWidth(),
        enableUserInteractionTracing = false,
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Nav2ComposeRouteButton("Open Product 42", onOpenProduct42)
          Nav2ComposeRouteButton("Open Product 7", onOpenProduct7)
        }
      }
    },
  ) {
    Nav2ComposeRouteButton(
      label = if (showProductItems) "Hide Product Items" else "Show Product Items",
      onClick = { showProductItems = !showProductItems },
    )

    if (showProductItems) {
      SentryTraced(
        tag = "product_list_items",
        modifier = Modifier.fillMaxWidth(),
        enableUserInteractionTracing = false,
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          repeat(PRODUCT_LIST_ITEM_COUNT) { index -> Nav2ComposeProductListItem(index + 1) }
        }
      }
    }
  }
}

@Composable
private fun Nav2ComposeProductDetailRoute(
  routeSpec: RouteSpec,
  productId: String,
  source: String,
  campaign: String,
  onShowPromoDialog: () -> Unit,
  onOpenShareSheet: () -> Unit,
  onCheckout: () -> Unit,
) {
  LaunchedEffect(productId, source, campaign) {
    recordSimulatedBackgroundSpan(RouteNames.PRODUCT_DETAIL)
  }

  Nav2ComposeActionRoute(
    routeSpec,
    arguments =
      mapOf(
        NavArgs.PRODUCT_ID to productId,
        NavArgs.SOURCE to source,
        NavArgs.CAMPAIGN to campaign,
      ),
    buttons =
      listOf(
        "Show Promo Dialog" to onShowPromoDialog,
        "Open Share Sheet" to onOpenShareSheet,
        "Go to Checkout" to onCheckout,
      ),
  )
}

@Composable
private fun Nav2ComposeCheckoutRoute(
  routeSpec: RouteSpec,
  productId: String,
  onCompleteOrder: () -> Unit,
) {
  Nav2ComposeActionRoute(
    routeSpec,
    arguments = mapOf(NavArgs.PRODUCT_ID to productId),
    buttons = listOf("Complete Order" to onCompleteOrder),
  )
}

@Composable
private fun Nav2ComposeConfirmationRoute(
  routeSpec: RouteSpec,
  orderId: String,
  onResetBackStack: () -> Unit,
) {
  Nav2ComposeActionRoute(
    routeSpec,
    arguments = mapOf(NavArgs.ORDER_ID to orderId),
    buttons = listOf("Reset Backstack" to onResetBackStack),
  )
}

@Composable
private fun Nav2ComposeActionRoute(
  routeSpec: RouteSpec,
  arguments: Map<String, Any?> = emptyMap(),
  buttons: List<Pair<String, () -> Unit>>,
) {
  Nav2ComposeRouteScaffold(
    routeSpec = routeSpec,
    cardContent = {
      routeSpec.displayArguments(arguments).forEach { (label, value) ->
        Nav2ComposeRouteInfo(label, value)
      }
      buttons.forEach { (label, onClick) -> Nav2ComposeRouteButton(label, onClick) }
    },
  )
}

@Composable
private fun Nav2ComposePromoDialogRoute(
  routeSpec: RouteSpec,
  promoId: String,
  onCaptureException: () -> Unit,
  onCrashApp: () -> Unit,
  onDismiss: () -> Unit,
) {
  ComposeAlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(routeSpec.title) },
    text = {
      val argumentText =
        routeSpec.displayArguments(mapOf(NavArgs.PROMO_ID to promoId)).toDisplayString()
      Text(
        listOfNotNull(routeSpec.description, argumentText.takeIf { it.isNotEmpty() })
          .joinToString("\n\n")
      )
    },
    confirmButton = {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
          onClick = onCaptureException,
          modifier = Modifier.sentryTag(nav2ComposeInteractionTag("Promo Dialog Exception")),
        ) {
          Text("Exception")
        }
        TextButton(
          onClick = onCrashApp,
          modifier = Modifier.sentryTag(nav2ComposeInteractionTag("Promo Dialog Crash App")),
        ) {
          Text("Crash App")
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(
          onClick = onDismiss,
          modifier = Modifier.sentryTag(nav2ComposeInteractionTag("Promo Dialog Dismiss")),
        ) {
          Text("Dismiss", color = Color.Gray)
        }
      }
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Nav2ComposeShareSheetRoute(
  routeSpec: RouteSpec,
  productId: String,
  onCaptureException: () -> Unit,
  onCrashApp: () -> Unit,
  onDone: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDone) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(routeSpec.title, style = MaterialTheme.typography.headlineSmall)
      routeSpec.description?.let { Text(it) }
      routeSpec.displayArguments(mapOf(NavArgs.PRODUCT_ID to productId)).forEach { (label, value) ->
        Text("$label=$value")
      }
      Button(
        onClick = onCaptureException,
        modifier =
          Modifier.fillMaxWidth().sentryTag(nav2ComposeInteractionTag("Share Sheet Exception")),
      ) {
        Text("Capture Exception")
      }
      Button(
        onClick = onCrashApp,
        modifier =
          Modifier.fillMaxWidth().sentryTag(nav2ComposeInteractionTag("Share Sheet Crash App")),
      ) {
        Text("Crash App")
      }
      Button(
        onClick = onDone,
        modifier = Modifier.fillMaxWidth().sentryTag(nav2ComposeInteractionTag("Share Sheet Done")),
      ) {
        Text("Done")
      }
      Spacer(Modifier.size(12.dp))
    }
  }
}

@Composable
private fun Nav2ComposeRouteScaffold(
  routeSpec: RouteSpec,
  cardContent: (@Composable ColumnScope.() -> Unit)? = null,
  content: (@Composable ColumnScope.() -> Unit)? = null,
) {
  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(
      modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
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
  }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Nav2ComposeProductListItem(index: Int) {
  SentryTraced(
    tag = "product_list_item_$index",
    modifier = Modifier.fillMaxWidth(),
    enableUserInteractionTracing = false,
  ) {
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text("Product #$index", fontWeight = FontWeight.Bold)
        Text("SKU-$index")
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
  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.fillMaxWidth().sentryTag(nav2ComposeInteractionTag(label)),
  ) {
    Text(label)
  }
}

private fun nav2ComposeInteractionTag(label: String): String = "Nav2 Compose $label"

private const val PRODUCT_LIST_ITEM_COUNT = 20
private const val COMPOSE_ROUTE_TRANSITION_MILLIS = 350

@Composable
private fun Nav2ComposeRouteInfo(label: String, value: String) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(8.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(label, fontWeight = FontWeight.Bold)
      Spacer(Modifier.size(12.dp))
      Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

private fun SnapshotStateList<Nav2ComposeDestination>.resetTo(destination: Nav2ComposeDestination) {
  clear()
  add(destination)
}

@Composable
private fun rememberSaveableNav2ComposeBackStack(): SnapshotStateList<Nav2ComposeDestination> {
  return rememberSaveable(saver = nav2ComposeBackStackSaver()) {
    mutableStateListOf<Nav2ComposeDestination>(Home)
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
          add(Home)
        }
      }
    },
  )

private fun List<Nav2ComposeDestination>.toComposeBackStackText(): String =
  joinToString(" -> ") { destination -> destination.backStackRoute() }

internal sealed class Nav2ComposeDestination(
  val routeName: String,
  val route: String,
  val arguments: Map<String, Any?> = emptyMap(),
) {

  data object Home : Nav2ComposeDestination(RouteNames.HOME, RouteNames.HOME)

  data object Custom : Nav2ComposeDestination(RouteNames.CUSTOM, RouteNames.CUSTOM)

  data object ProductList : Nav2ComposeDestination(RouteNames.PRODUCT_LIST, RouteNames.PRODUCT_LIST)

  data class ProductDetail(
    val productId: String,
    val source: String,
    val campaign: String = "",
  ) :
    Nav2ComposeDestination(
      routeName = RouteNames.PRODUCT_DETAIL,
      route =
        "${RouteNames.PRODUCT_DETAIL}/$productId/$source" +
          if (campaign.isNotEmpty()) "?${NavArgs.CAMPAIGN}=$campaign" else "",
      arguments =
        mapOf(
            NavArgs.PRODUCT_ID to productId,
            NavArgs.SOURCE to source,
            NavArgs.CAMPAIGN to campaign,
          )
          .filterValues { value -> value.isNotEmpty() },
    )

  data class Checkout(val productId: String) :
    Nav2ComposeDestination(
      routeName = RouteNames.CHECKOUT,
      route = "${RouteNames.CHECKOUT}/$productId",
      arguments = mapOf(NavArgs.PRODUCT_ID to productId),
    )

  data class Confirmation(val orderId: String) :
    Nav2ComposeDestination(
      routeName = RouteNames.CONFIRMATION,
      route = "${RouteNames.CONFIRMATION}/$orderId",
      arguments = mapOf(NavArgs.ORDER_ID to orderId),
    )

  data class PromoDialog(val promoId: String) :
    Nav2ComposeDestination(
      routeName = RouteNames.PROMO_DIALOG,
      route = "${RouteNames.PROMO_DIALOG}/$promoId",
      arguments = mapOf(NavArgs.PROMO_ID to promoId),
    )

  fun displayRoute(): String {
    return RouteSpecs.get(routeName).displayRoute(arguments)
  }

  fun backStackRoute(): String = "/$routeName"

  fun toSavedState(): Bundle =
    Bundle().apply {
      when (this@Nav2ComposeDestination) {
        Home -> putString("type", "home")
        Custom -> putString("type", "custom")
        ProductList -> putString("type", "product_list")
        is ProductDetail -> {
          putString("type", "product_detail")
          putString(NavArgs.PRODUCT_ID, productId)
          putString(NavArgs.SOURCE, source)
          putString(NavArgs.CAMPAIGN, campaign)
        }
        is Checkout -> {
          putString("type", "checkout")
          putString(NavArgs.PRODUCT_ID, productId)
        }
        is Confirmation -> {
          putString("type", "confirmation")
          putString(NavArgs.ORDER_ID, orderId)
        }
        is PromoDialog -> {
          putString("type", "promo_dialog")
          putString(NavArgs.PROMO_ID, promoId)
        }
      }
    }

  companion object {
    const val PRODUCT_DETAIL_ROUTE =
      RouteNames.PRODUCT_DETAIL +
        "/{" +
        NavArgs.PRODUCT_ID +
        "}/{" +
        NavArgs.SOURCE +
        "}?" +
        NavArgs.CAMPAIGN +
        "={" +
        NavArgs.CAMPAIGN +
        "}"
    const val CHECKOUT_ROUTE = RouteNames.CHECKOUT + "/{" + NavArgs.PRODUCT_ID + "}"
    const val CONFIRMATION_ROUTE = RouteNames.CONFIRMATION + "/{" + NavArgs.ORDER_ID + "}"
    const val PROMO_DIALOG_ROUTE = RouteNames.PROMO_DIALOG + "/{" + NavArgs.PROMO_ID + "}"
  }
}

private fun Bundle.toNav2ComposeDestination(): Nav2ComposeDestination {
  return when (getString("type")) {
    "home" -> Home
    "custom" -> Custom
    "product_list" -> ProductList
    "product_detail" ->
      ProductDetail(
        productId = requireNotNull(getString(NavArgs.PRODUCT_ID)),
        source = requireNotNull(getString(NavArgs.SOURCE)),
        campaign = getString(NavArgs.CAMPAIGN).orEmpty(),
      )
    "checkout" -> Checkout(productId = requireNotNull(getString(NavArgs.PRODUCT_ID)))
    "confirmation" -> Confirmation(orderId = requireNotNull(getString(NavArgs.ORDER_ID)))
    "promo_dialog" -> PromoDialog(promoId = requireNotNull(getString(NavArgs.PROMO_ID)))
    else -> Home
  }
}
