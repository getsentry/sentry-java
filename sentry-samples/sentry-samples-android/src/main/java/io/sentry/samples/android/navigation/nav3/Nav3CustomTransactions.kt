package io.sentry.samples.android.navigation.nav3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import io.sentry.ITransaction
import io.sentry.Sentry
import io.sentry.SpanStatus
import io.sentry.TransactionOptions

internal enum class Nav3CustomTransactionMode(
  val label: String,
  val description: String,
) {
  PER_SCREEN(
    label = "Per Screen",
    description =
      "Starts a custom transaction for every destination so Nav3 route work runs under app-owned " +
        "screen-level transactions.",
  ),
  WHOLE_FLOW(
    label = "Whole Flow",
    description =
      "Keeps one custom transaction open for the whole shopping journey until the flow returns " +
        "to the Custom home screen.",
  ),
  LINGERING(
    label = "Lingering",
    description =
      "Starts one custom transaction and intentionally leaves it active across later routes to " +
        "simulate a stale power-user transaction.",
  ),
  ASYNC_FROM_USER_ACTION(
    label = "Async From User Action",
    description =
      "Starts a custom transaction from the Browse Products button, waits for async work, then " +
        "navigates to Product List while the manual transaction stays active.",
  ),
}

@Composable
internal fun Nav3CustomTransactionEffect(
  selectedScenario: Nav3Scenario,
  mode: Nav3CustomTransactionMode,
  backStack: List<Nav3Route>,
  controller: Nav3CustomTransactionController,
) {
  val backStackSnapshot = backStack.toList()

  DisposableEffect(selectedScenario, mode, backStackSnapshot) {
    controller.onBackStackChanged(
      isCustomScenario = selectedScenario == Nav3Scenario.CUSTOM,
      mode = mode,
      backStack = backStackSnapshot,
    )
    onDispose {}
  }

  DisposableEffect(controller) { onDispose { controller.cleanup() } }
}

internal class Nav3CustomTransactionController {

  private var activeTransaction: ITransaction? = null
  private var activeMode: Nav3CustomTransactionMode? = null
  private var activeRouteName: String? = null

  fun onBackStackChanged(
    isCustomScenario: Boolean,
    mode: Nav3CustomTransactionMode,
    backStack: List<Nav3Route>,
  ) {
    if (!isCustomScenario) {
      cleanup()
      return
    }

    val currentRoute =
      backStack.lastOrNull()
        ?: run {
          cleanup()
          return
        }

    when (mode) {
      Nav3CustomTransactionMode.PER_SCREEN -> handlePerScreen(currentRoute)
      Nav3CustomTransactionMode.WHOLE_FLOW -> handleWholeFlow(currentRoute)
      Nav3CustomTransactionMode.LINGERING -> handleLingering(currentRoute)
      Nav3CustomTransactionMode.ASYNC_FROM_USER_ACTION -> handleAsyncFromUserAction(currentRoute)
    }
  }

  fun startAsyncBrowseProductsTransaction() {
    finishActiveTransaction()
    activeTransaction =
      startCustomTransaction(
        name = "power_user.tap_to_browse_products",
        operation = "ui.action",
        mode = Nav3CustomTransactionMode.ASYNC_FROM_USER_ACTION,
        routeName = Nav3Route.Custom.routeName,
      )
    activeMode = Nav3CustomTransactionMode.ASYNC_FROM_USER_ACTION
    activeRouteName = Nav3Route.Custom.routeName
    activeTransaction?.setData("sample.async_trigger", "browse_products")
  }

  fun cleanup() {
    finishActiveTransaction()
  }

  private fun handlePerScreen(currentRoute: Nav3Route) {
    if (
      activeMode == Nav3CustomTransactionMode.PER_SCREEN &&
        activeRouteName == currentRoute.routeName
    ) {
      return
    }

    finishActiveTransaction()
    activeTransaction =
      startCustomTransaction(
        name = "power_user.${currentRoute.routeName.lowercase()}_screen",
        operation = "ui.screen.manual",
        mode = Nav3CustomTransactionMode.PER_SCREEN,
        routeName = currentRoute.routeName,
      )
    activeMode = Nav3CustomTransactionMode.PER_SCREEN
    activeRouteName = currentRoute.routeName
  }

  private fun handleWholeFlow(currentRoute: Nav3Route) {
    if (currentRoute == Nav3Route.Custom) {
      if (activeMode == Nav3CustomTransactionMode.WHOLE_FLOW) {
        finishActiveTransaction()
      }
      return
    }

    if (activeMode != Nav3CustomTransactionMode.WHOLE_FLOW || activeTransaction == null) {
      finishActiveTransaction()
      activeTransaction =
        startCustomTransaction(
          name = "power_user.checkout_flow",
          operation = "ui.flow.manual",
          mode = Nav3CustomTransactionMode.WHOLE_FLOW,
          routeName = currentRoute.routeName,
        )
      activeMode = Nav3CustomTransactionMode.WHOLE_FLOW
    }

    activeRouteName = currentRoute.routeName
    activeTransaction?.setData("sample.current_route", currentRoute.routeName)
  }

  private fun handleLingering(currentRoute: Nav3Route) {
    if (activeMode == Nav3CustomTransactionMode.LINGERING && activeTransaction != null) {
      activeTransaction?.setData("sample.current_route", currentRoute.routeName)
      return
    }

    finishActiveTransaction()
    activeTransaction =
      startCustomTransaction(
        name = "power_user.lingering_navigation_transaction",
        operation = "ui.flow.manual",
        mode = Nav3CustomTransactionMode.LINGERING,
        routeName = currentRoute.routeName,
      )
    activeMode = Nav3CustomTransactionMode.LINGERING
    activeRouteName = currentRoute.routeName
  }

  private fun handleAsyncFromUserAction(currentRoute: Nav3Route) {
    if (activeMode != Nav3CustomTransactionMode.ASYNC_FROM_USER_ACTION) {
      finishActiveTransaction()
      return
    }

    val transaction = activeTransaction ?: return
    activeMode = Nav3CustomTransactionMode.ASYNC_FROM_USER_ACTION
    activeRouteName = currentRoute.routeName
    transaction.setData("sample.current_route", currentRoute.routeName)

    if (currentRoute != Nav3Route.Custom && currentRoute !is Nav3Route.ProductList) {
      finishActiveTransaction()
    }
  }

  private fun startCustomTransaction(
    name: String,
    operation: String,
    mode: Nav3CustomTransactionMode,
    routeName: String,
  ): ITransaction {
    val options =
      TransactionOptions().also {
        it.isBindToScope = true
        it.isWaitForChildren = true
        it.idleTimeout = null
      }
    return Sentry.startTransaction(name, operation, options).apply {
      setTag("sample_nav3_scenario", Nav3Scenario.CUSTOM.label)
      setTag("sample_nav3_custom_mode", mode.label)
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
