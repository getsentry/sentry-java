package io.sentry.samples.android.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import io.sentry.ITransaction
import io.sentry.Sentry
import io.sentry.SpanStatus
import io.sentry.TransactionOptions

internal enum class Nav2CustomTransactionMode(
  val label: String,
  val description: String,
) {
  PER_SCREEN(
    label = "Per Screen",
    description =
      "Starts a custom transaction for every destination so Nav2 route work runs under app-owned screen-level transactions.",
  ),
  WHOLE_FLOW(
    label = "Whole Flow",
    description =
      "Keeps one custom transaction open for the whole shopping journey until the flow returns to the Custom home screen.",
  ),
  LINGERING(
    label = "Lingering",
    description =
      "Starts one custom transaction and intentionally leaves it active across later routes to simulate a stale power-user transaction.",
  ),
  ASYNC_FROM_USER_ACTION(
    label = "Async From User Action",
    description =
      "Starts a custom transaction from the Browse Products button, waits for async work, then navigates to Product List while the manual transaction stays active.",
  ),
}

@Composable
internal fun Nav2CustomTransactionEffect(
  selectedDestination: Any,
  mode: Nav2CustomTransactionMode,
  controller: Nav2CustomTransactionController,
) {
  DisposableEffect(selectedDestination, mode) {
    controller.onDestinationChanged(selectedDestination, mode)
    onDispose {}
  }

  DisposableEffect(controller) { onDispose { controller.cleanup() } }
}

internal class Nav2CustomTransactionController {

  private var activeTransaction: ITransaction? = null
  private var activeMode: Nav2CustomTransactionMode? = null
  private var activeRouteName: String? = null

  fun onDestinationChanged(destination: Any, mode: Nav2CustomTransactionMode) {
    val currentRouteName =
      when (destination) {
        is Nav2ComposeDestination -> destination.routeName
        else -> return cleanup()
      }

    val isCustomScenario = destination is Nav2ComposeDestination.Custom || activeMode != null
    if (!isCustomScenario) {
      cleanup()
      return
    }

    when (mode) {
      Nav2CustomTransactionMode.PER_SCREEN -> handlePerScreen(currentRouteName)
      Nav2CustomTransactionMode.WHOLE_FLOW -> handleWholeFlow(currentRouteName)
      Nav2CustomTransactionMode.LINGERING -> handleLingering(currentRouteName)
      Nav2CustomTransactionMode.ASYNC_FROM_USER_ACTION -> handleAsyncFromUserAction(currentRouteName)
    }
  }

  fun startAsyncBrowseProductsTransaction() {
    finishActiveTransaction()
    activeTransaction =
      startCustomTransaction(
        name = "custom.tap_to_browse_products",
        operation = "ui.action",
        mode = Nav2CustomTransactionMode.ASYNC_FROM_USER_ACTION,
        routeName = Nav2RouteNames.CUSTOM,
      )
    activeMode = Nav2CustomTransactionMode.ASYNC_FROM_USER_ACTION
    activeRouteName = Nav2RouteNames.CUSTOM
    activeTransaction?.setData("sample.async_trigger", "browse_products")
  }

  fun cleanup() {
    finishActiveTransaction()
  }

  private fun handlePerScreen(currentRoute: String) {
    if (activeMode == Nav2CustomTransactionMode.PER_SCREEN && activeRouteName == currentRoute) {
      return
    }

    finishActiveTransaction()
    activeTransaction =
      startCustomTransaction(
        name = "custom.${currentRoute.lowercase()}_screen",
        operation = "ui.screen.manual",
        mode = Nav2CustomTransactionMode.PER_SCREEN,
        routeName = currentRoute,
      )
    activeMode = Nav2CustomTransactionMode.PER_SCREEN
    activeRouteName = currentRoute
  }

  private fun handleWholeFlow(currentRoute: String) {
    if (currentRoute == Nav2RouteNames.CUSTOM) {
      if (activeMode == Nav2CustomTransactionMode.WHOLE_FLOW) {
        finishActiveTransaction()
      }
      return
    }

    if (activeMode != Nav2CustomTransactionMode.WHOLE_FLOW || activeTransaction == null) {
      finishActiveTransaction()
      activeTransaction =
        startCustomTransaction(
          name = "custom.checkout_flow",
          operation = "ui.flow.manual",
          mode = Nav2CustomTransactionMode.WHOLE_FLOW,
          routeName = currentRoute,
        )
      activeMode = Nav2CustomTransactionMode.WHOLE_FLOW
    }

    activeRouteName = currentRoute
    activeTransaction?.setData("sample.current_route", currentRoute)
  }

  private fun handleLingering(currentRoute: String) {
    if (activeMode == Nav2CustomTransactionMode.LINGERING && activeTransaction != null) {
      activeTransaction?.setData("sample.current_route", currentRoute)
      return
    }

    finishActiveTransaction()
    activeTransaction =
      startCustomTransaction(
        name = "custom.lingering_navigation_transaction",
        operation = "ui.flow.manual",
        mode = Nav2CustomTransactionMode.LINGERING,
        routeName = currentRoute,
      )
    activeMode = Nav2CustomTransactionMode.LINGERING
    activeRouteName = currentRoute
  }

  private fun handleAsyncFromUserAction(currentRoute: String) {
    if (activeMode != Nav2CustomTransactionMode.ASYNC_FROM_USER_ACTION) {
      finishActiveTransaction()
      return
    }

    val transaction = activeTransaction ?: return
    activeRouteName = currentRoute
    transaction.setData("sample.current_route", currentRoute)

    if (currentRoute != Nav2RouteNames.CUSTOM && currentRoute != Nav2RouteNames.PRODUCT_LIST) {
      finishActiveTransaction()
    }
  }

  private fun startCustomTransaction(
    name: String,
    operation: String,
    mode: Nav2CustomTransactionMode,
    routeName: String,
  ): ITransaction {
    val options =
      TransactionOptions().also {
        it.isBindToScope = true
        it.isWaitForChildren = true
        it.idleTimeout = null
      }
    return Sentry.startTransaction(name, operation, options).apply {
      setTag("sample_nav2_scenario", Nav2Scenario.CUSTOM.label)
      setTag("sample_nav2_custom_mode", mode.label)
      setData("sample.custom_transaction", true)
      setData("sample.current_route", routeName)
    }
  }

  private fun finishActiveTransaction() {
    val transaction = activeTransaction ?: return
    transaction.finish(transaction.status ?: SpanStatus.OK)
    Sentry.configureScope { scope ->
      scope.withTransaction { current ->
        if (current == transaction) {
          scope.clearTransaction()
        }
      }
    }
    activeTransaction = null
    activeMode = null
    activeRouteName = null
  }
}
