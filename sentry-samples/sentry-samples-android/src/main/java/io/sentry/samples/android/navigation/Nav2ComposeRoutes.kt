package io.sentry.samples.android.navigation

import android.os.Bundle
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.AlertDialog as ComposeAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.font.FontWeight
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
import io.sentry.samples.android.GithubAPI
import io.sentry.samples.android.navigation.Nav2ComposeDestination.Checkout
import io.sentry.samples.android.navigation.Nav2ComposeDestination.Confirmation
import io.sentry.samples.android.navigation.Nav2ComposeDestination.Home
import io.sentry.samples.android.navigation.Nav2ComposeDestination.ProductDetail
import io.sentry.samples.android.navigation.Nav2ComposeDestination.ProductList
import io.sentry.samples.android.navigation.Nav2ComposeDestination.PromoDialog
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

@Composable
internal fun Nav2ComposeApp(
  navListener: SentryNavigationListener,
  routeWorkOptions: Set<RouteWorkOption>,
  onCaptureException: () -> Unit,
  onCrashApp: () -> Unit,
  onRouteChanged: (routeName: String, currentRoute: String, backStack: String) -> Unit,
) {

  val navController = rememberNavController().withSentryObservableEffect(navListener = navListener)
  val backStack = rememberSaveableNav2ComposeBackStack()
  val shareSheetProductId = rememberSaveable { mutableStateOf<String?>(null) }
  val currentDestination = backStack.lastOrNull() ?: Home

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

  BackHandler(enabled = shareSheetProductId.value != null) { dismissShareSheet() }
  BackHandler(enabled = shareSheetProductId.value == null && backStack.size > 1) { navigateBack() }

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

  Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    NavHost(
      navController = navController,
      startDestination = Home.route,
      modifier = Modifier.weight(1f),
    ) {
      composable(Home.route) {
        TracedNav2ComposeRoute(Home.routeName) {
          Nav2ComposeHomeRoute(routeSpec = Nav2RouteSpecs.home) { navigateTo(ProductList) }
        }
      }

      composable(ProductList.route) {
        TracedNav2ComposeRoute(ProductList.routeName) {
          Nav2ComposeProductListRoute(
            routeSpec = Nav2RouteSpecs.productList,
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
            navArgument(Nav2Args.PRODUCT_ID) { type = NavType.StringType },
            navArgument(Nav2Args.SOURCE) { type = NavType.StringType },
            navArgument(Nav2Args.CAMPAIGN) {
              type = NavType.StringType
              defaultValue = ""
            },
          ),
      ) { entry ->
        val productId = entry.arguments?.getString(Nav2Args.PRODUCT_ID).orEmpty()
        val source = entry.arguments?.getString(Nav2Args.SOURCE).orEmpty()
        val campaign = entry.arguments?.getString(Nav2Args.CAMPAIGN).orEmpty()
        TracedNav2ComposeRoute(Nav2RouteNames.PRODUCT_DETAIL) {
          Nav2ComposeProductDetailRoute(
            routeSpec = Nav2RouteSpecs.productDetail,
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
        arguments = listOf(navArgument(Nav2Args.PRODUCT_ID) { type = NavType.StringType }),
      ) { entry ->
        val productId = entry.arguments?.getString(Nav2Args.PRODUCT_ID).orEmpty()
        TracedNav2ComposeRoute(Nav2RouteNames.CHECKOUT) {
          Nav2ComposeCheckoutRoute(
            routeSpec = Nav2RouteSpecs.checkout,
            productId = productId,
            onCompleteOrder = {
              navigateTo(Confirmation(orderId = "order-$productId"))
            },
          )
        }
      }

      composable(
        route = Nav2ComposeDestination.CONFIRMATION_ROUTE,
        arguments = listOf(navArgument(Nav2Args.ORDER_ID) { type = NavType.StringType }),
      ) { entry ->
        TracedNav2ComposeRoute(Nav2RouteNames.CONFIRMATION) {
          Nav2ComposeConfirmationRoute(
            routeSpec = Nav2RouteSpecs.confirmation,
            orderId = entry.arguments?.getString(Nav2Args.ORDER_ID).orEmpty(),
            onResetBackStack = { resetToHome() },
          )
        }
      }

      dialog(
        route = Nav2ComposeDestination.PROMO_DIALOG_ROUTE,
        arguments = listOf(navArgument(Nav2Args.PROMO_ID) { type = NavType.StringType }),
      ) { entry ->
        // This dialog is a real Nav destination, so it participates in Nav2 the same way as the
        // rest of the route graph. Compare it with the share sheet overlay below when inspecting
        // Sentry's Nav2 breadcrumbs, destination arguments, and route transactions.
        TracedNav2ComposeRoute(Nav2RouteNames.PROMO_DIALOG) {
          Nav2ComposePromoDialogRoute(
            routeSpec = Nav2RouteSpecs.promoDialog,
            promoId = entry.arguments?.getString(Nav2Args.PROMO_ID).orEmpty(),
            onCaptureException = onCaptureException,
            onCrashApp = onCrashApp,
            onDismiss = { navigateBack() },
          )
        }
      }
    }

    shareSheetProductId.value?.let { productId ->
      // This share sheet is intentionally just a screen overlay, not a Nav destination. It lets
      // the sample compare how Sentry's Nav2 integration behaves for proper Nav destinations vs.
      // UI layered on top of the current route.
      Nav2ComposeShareSheetRoute(
        routeSpec = Nav2RouteSpecs.shareSheet,
        productId = productId,
        onCaptureException = onCaptureException,
        onCrashApp = onCrashApp,
        onDone = ::dismissShareSheet,
      )
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
          GithubAPI.service.listReposAsync("getsentry", 5)
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
private fun Nav2ComposeHomeRoute(routeSpec: Nav2RouteSpec, onBrowseProducts: () -> Unit) {
  Nav2ComposeActionRoute(routeSpec, buttons = listOf("Browse Products" to onBrowseProducts))
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Nav2ComposeProductListRoute(
  routeSpec: Nav2RouteSpec,
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
  routeSpec: Nav2RouteSpec,
  productId: String,
  source: String,
  campaign: String,
  onShowPromoDialog: () -> Unit,
  onOpenShareSheet: () -> Unit,
  onCheckout: () -> Unit,
) {
  LaunchedEffect(productId, source, campaign) {
    recordSimulatedBackgroundSpan(Nav2RouteNames.PRODUCT_DETAIL)
  }

  Nav2ComposeActionRoute(
    routeSpec,
    arguments =
      mapOf(
        Nav2Args.PRODUCT_ID to productId,
        Nav2Args.SOURCE to source,
        Nav2Args.CAMPAIGN to campaign,
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
  routeSpec: Nav2RouteSpec,
  productId: String,
  onCompleteOrder: () -> Unit,
) {
  Nav2ComposeActionRoute(
    routeSpec,
    arguments = mapOf(Nav2Args.PRODUCT_ID to productId),
    buttons = listOf("Complete Order" to onCompleteOrder),
  )
}

@Composable
private fun Nav2ComposeConfirmationRoute(
  routeSpec: Nav2RouteSpec,
  orderId: String,
  onResetBackStack: () -> Unit,
) {
  Nav2ComposeActionRoute(
    routeSpec,
    arguments = mapOf(Nav2Args.ORDER_ID to orderId),
    buttons = listOf("Reset Backstack" to onResetBackStack),
  )
}

@Composable
private fun Nav2ComposeActionRoute(
  routeSpec: Nav2RouteSpec,
  arguments: Map<String, Any?> = emptyMap(),
  buttons: List<Pair<String, () -> Unit>>,
) {
  Nav2ComposeRouteScaffold(routeSpec) {
    routeSpec.displayArguments(arguments).forEach { (label, value) ->
      Nav2ComposeRouteInfo(label, value)
    }
    buttons.forEach { (label, onClick) -> Nav2ComposeRouteButton(label, onClick) }
  }
}

@Composable
private fun Nav2ComposePromoDialogRoute(
  routeSpec: Nav2RouteSpec,
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
        routeSpec.displayArguments(mapOf(Nav2Args.PROMO_ID to promoId)).toDisplayString()
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
  routeSpec: Nav2RouteSpec,
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
      routeSpec.displayArguments(mapOf(Nav2Args.PRODUCT_ID to productId)).forEach { (label, value)
        ->
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
  routeSpec: Nav2RouteSpec,
  cardContent: (@Composable ColumnScope.() -> Unit)? = null,
  content: (@Composable ColumnScope.() -> Unit)? = null,
) {
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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

private sealed class Nav2ComposeDestination(
  val routeName: String,
  val route: String,
  val arguments: Map<String, Any?> = emptyMap(),
) {

  data object Home : Nav2ComposeDestination(Nav2RouteNames.HOME, Nav2RouteNames.HOME)

  data object ProductList :
    Nav2ComposeDestination(Nav2RouteNames.PRODUCT_LIST, Nav2RouteNames.PRODUCT_LIST)

  data class ProductDetail(
    val productId: String,
    val source: String,
    val campaign: String = "",
  ) :
    Nav2ComposeDestination(
      routeName = Nav2RouteNames.PRODUCT_DETAIL,
      route =
        "${Nav2RouteNames.PRODUCT_DETAIL}/$productId/$source" +
          if (campaign.isNotEmpty()) "?${Nav2Args.CAMPAIGN}=$campaign" else "",
      arguments =
        mapOf(
            Nav2Args.PRODUCT_ID to productId,
            Nav2Args.SOURCE to source,
            Nav2Args.CAMPAIGN to campaign,
          )
          .filterValues { value -> value.isNotEmpty() },
    )

  data class Checkout(val productId: String) :
    Nav2ComposeDestination(
      routeName = Nav2RouteNames.CHECKOUT,
      route = "${Nav2RouteNames.CHECKOUT}/$productId",
      arguments = mapOf(Nav2Args.PRODUCT_ID to productId),
    )

  data class Confirmation(val orderId: String) :
    Nav2ComposeDestination(
      routeName = Nav2RouteNames.CONFIRMATION,
      route = "${Nav2RouteNames.CONFIRMATION}/$orderId",
      arguments = mapOf(Nav2Args.ORDER_ID to orderId),
    )

  data class PromoDialog(val promoId: String) :
    Nav2ComposeDestination(
      routeName = Nav2RouteNames.PROMO_DIALOG,
      route = "${Nav2RouteNames.PROMO_DIALOG}/$promoId",
      arguments = mapOf(Nav2Args.PROMO_ID to promoId),
    )

  fun displayRoute(): String {
    return Nav2RouteSpecs.get(routeName).displayRoute(arguments)
  }

  fun backStackRoute(): String = "/$routeName"

  fun toSavedState(): Bundle =
    Bundle().apply {
      when (this@Nav2ComposeDestination) {
        Home -> putString("type", "home")
        ProductList -> putString("type", "product_list")
        is ProductDetail -> {
          putString("type", "product_detail")
          putString(Nav2Args.PRODUCT_ID, productId)
          putString(Nav2Args.SOURCE, source)
          putString(Nav2Args.CAMPAIGN, campaign)
        }
        is Checkout -> {
          putString("type", "checkout")
          putString(Nav2Args.PRODUCT_ID, productId)
        }
        is Confirmation -> {
          putString("type", "confirmation")
          putString(Nav2Args.ORDER_ID, orderId)
        }
        is PromoDialog -> {
          putString("type", "promo_dialog")
          putString(Nav2Args.PROMO_ID, promoId)
        }
      }
    }

  companion object {
    const val PRODUCT_DETAIL_ROUTE =
      Nav2RouteNames.PRODUCT_DETAIL +
        "/{" +
        Nav2Args.PRODUCT_ID +
        "}/{" +
        Nav2Args.SOURCE +
        "}?" +
        Nav2Args.CAMPAIGN +
        "={" +
        Nav2Args.CAMPAIGN +
        "}"
    const val CHECKOUT_ROUTE = Nav2RouteNames.CHECKOUT + "/{" + Nav2Args.PRODUCT_ID + "}"
    const val CONFIRMATION_ROUTE = Nav2RouteNames.CONFIRMATION + "/{" + Nav2Args.ORDER_ID + "}"
    const val PROMO_DIALOG_ROUTE = Nav2RouteNames.PROMO_DIALOG + "/{" + Nav2Args.PROMO_ID + "}"
  }
}

private fun Bundle.toNav2ComposeDestination(): Nav2ComposeDestination {
  return when (getString("type")) {
    "home" -> Home
    "product_list" -> ProductList
    "product_detail" ->
      ProductDetail(
        productId = requireNotNull(getString(Nav2Args.PRODUCT_ID)),
        source = requireNotNull(getString(Nav2Args.SOURCE)),
        campaign = getString(Nav2Args.CAMPAIGN).orEmpty(),
      )
    "checkout" -> Checkout(productId = requireNotNull(getString(Nav2Args.PRODUCT_ID)))
    "confirmation" -> Confirmation(orderId = requireNotNull(getString(Nav2Args.ORDER_ID)))
    "promo_dialog" -> PromoDialog(promoId = requireNotNull(getString(Nav2Args.PROMO_ID)))
    else -> Home
  }
}
