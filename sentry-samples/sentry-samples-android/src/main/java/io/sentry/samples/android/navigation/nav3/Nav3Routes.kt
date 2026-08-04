package io.sentry.samples.android.navigation.nav3

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import io.sentry.samples.android.navigation.common.NavArgs
import io.sentry.samples.android.navigation.common.RouteNames
import io.sentry.samples.android.navigation.common.RouteSpec
import io.sentry.samples.android.navigation.common.RouteSpecs
import io.sentry.samples.android.navigation.common.displayRoute

internal fun Nav3Route.displayRoute(): String = routeSpec().displayRoute(arguments)

internal fun Nav3Route.routeSpec(): RouteSpec =
  when (this) {
    Nav3Route.Landing -> RouteSpecs.landing
    Nav3Route.SingleStack -> RouteSpecs.home
    Nav3Route.Custom ->
      RouteSpecs.home.copy(
        routeName = Nav3Route.Custom.routeName,
        title = "Custom Transactions",
        description =
          "Reuses the single-stack shopping flow while simulating a power user who starts their " +
            "own manual transactions.",
      )
    Nav3Route.DeepLink ->
      RouteSpec(
        routeName = Nav3Route.DeepLink.routeName,
        title = "Deep Link",
        description =
          "Simulates opening a deep link that builds a synthetic backstack before landing on a " +
            "detail destination.",
      )
    Nav3Route.ProductList -> RouteSpecs.productList
    is Nav3Route.ProductDetail -> RouteSpecs.productDetail
    is Nav3Route.Checkout -> RouteSpecs.checkout
    is Nav3Route.Confirmation -> RouteSpecs.confirmation
    is Nav3Route.PromoDialog -> RouteSpecs.promoDialog
    is Nav3Route.ShareSheet -> RouteSpecs.shareSheet
    Nav3Route.Multipane,
    Nav3Route.Multistack,
    is Nav3Route.Performance -> RouteSpec(routeName = routeName, title = routeName)
  }

@Composable
internal fun rememberSaveableNav3BackStack(initialRoute: Nav3Route): SnapshotStateList<Nav3Route> {
  return rememberSaveable(saver = nav3BackStackSaver()) { mutableStateListOf(initialRoute) }
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
      Nav3Route.Landing -> putString("type", "landing")
      Nav3Route.SingleStack -> putString("type", "single_stack")
      Nav3Route.Custom -> putString("type", "custom")
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
      is Nav3Route.Performance -> {
        putString("type", "performance")
        putInt("index", index)
        putInt("generation", generation)
      }
    }
  }

private fun Bundle.toNav3Route(): Nav3Route {
  return when (getString("type")) {
    "landing" -> Nav3Route.Landing
    "single_stack" -> Nav3Route.SingleStack
    "custom" -> Nav3Route.Custom
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
    "performance" ->
      Nav3Route.Performance(
        index = getInt("index"),
        generation = getInt("generation"),
      )
    else -> Nav3Route.SingleStack
  }
}

internal fun SnapshotStateList<Nav3Route>.resetTo(route: Nav3Route) {
  clear()
  add(route)
}

internal fun SnapshotStateList<Nav3Route>.openScenario(scenario: Nav3Scenario) {
  when (scenario) {
    Nav3Scenario.LANDING -> resetTo(Nav3Route.Landing)
    Nav3Scenario.SINGLE_STACK -> resetTo(Nav3Route.SingleStack)
    Nav3Scenario.CUSTOM -> resetTo(Nav3Route.Custom)
    Nav3Scenario.DEEP_LINK -> resetTo(Nav3Route.DeepLink)
    Nav3Scenario.MULTIPANE -> resetTo(Nav3Route.Multipane)
    Nav3Scenario.MULTIPLE_STACKS -> resetTo(Nav3Route.Multistack)
    Nav3Scenario.PERFORMANCE -> resetTo(Nav3Route.Performance(index = 0, generation = 0))
  }
}

internal fun SnapshotStateList<Nav3Route>.openSyntheticProductDeepLink() {
  clear()
  add(Nav3Route.SingleStack)
  add(Nav3Route.ProductList)
  add(Nav3Route.ProductDetail(productId = "42", source = "deep-link", campaign = "email"))
}

internal fun SnapshotStateList<Nav3Route>.openPerformanceStack(depth: Int, generation: Int) {
  clear()
  repeat(depth.coerceAtLeast(1)) { index ->
    add(Nav3Route.Performance(index = index, generation = generation))
  }
}

internal fun SnapshotStateList<Nav3Route>.mutatePerformanceLowerEntry(generation: Int) {
  if (isEmpty()) {
    add(Nav3Route.Performance(index = 0, generation = generation))
    return
  }

  val index = if (size > 1) 0 else lastIndex
  set(index, Nav3Route.Performance(index = index, generation = generation))
}

internal fun SnapshotStateList<Nav3Route>.replacePerformanceTop(generation: Int) {
  if (isEmpty()) {
    add(Nav3Route.Performance(index = 0, generation = generation))
    return
  }

  set(lastIndex, Nav3Route.Performance(index = lastIndex, generation = generation))
}

internal sealed interface Nav3Route {
  val routeName: String
  val arguments: Map<String, Any?>
    get() = emptyMap()

  val previewName: String
    get() = routeName

  val performanceSeed: Int
    get() = hashCode()

  data object Landing : Nav3Route {
    override val routeName: String = RouteNames.LANDING
  }

  data object SingleStack : Nav3Route {
    override val routeName: String = RouteNames.HOME
  }

  data object Custom : Nav3Route {
    override val routeName: String = "Custom"
  }

  data object DeepLink : Nav3Route {
    override val routeName: String = RouteNames.DEEP_LINK
  }

  data object ProductList : Nav3Route {
    override val routeName: String = RouteNames.PRODUCT_LIST
  }

  data class ProductDetail(
    val productId: String,
    val source: String,
    val campaign: String? = null,
  ) : Nav3Route {
    override val routeName: String = RouteNames.PRODUCT_DETAIL
    override val arguments: Map<String, Any?> =
      mapOf(
          NavArgs.PRODUCT_ID to productId,
          NavArgs.SOURCE to source,
          NavArgs.CAMPAIGN to campaign,
        )
        .filterValues { it != null }
    override val previewName: String = "ProductDetail($productId)"
  }

  data class Checkout(val productId: String) : Nav3Route {
    override val routeName: String = RouteNames.CHECKOUT
    override val arguments: Map<String, Any?> = mapOf(NavArgs.PRODUCT_ID to productId)
    override val previewName: String = "Checkout($productId)"
  }

  data class Confirmation(val orderId: String) : Nav3Route {
    override val routeName: String = RouteNames.CONFIRMATION
    override val arguments: Map<String, Any?> = mapOf(NavArgs.ORDER_ID to orderId)
    override val previewName: String = "Confirmation($orderId)"
  }

  data class PromoDialog(val promoId: String) : Nav3Route {
    override val routeName: String = RouteNames.PROMO_DIALOG
    override val arguments: Map<String, Any?> = mapOf(NavArgs.PROMO_ID to promoId)
    override val previewName: String = "PromoDialog($promoId)"
  }

  data class ShareSheet(val productId: String) : Nav3Route {
    override val routeName: String = RouteNames.SHARE_SHEET
    override val arguments: Map<String, Any?> = mapOf(NavArgs.PRODUCT_ID to productId)
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

  data class Performance(val index: Int, val generation: Int) : Nav3Route {
    override val routeName: String = "Performance"
    override val arguments: Map<String, Any?> = mapOf("index" to index, "generation" to generation)
    override val previewName: String = "Performance($index:$generation)"
    override val performanceSeed: Int = 31 * index + generation
  }
}

internal enum class Nav3Scenario(val label: String, val showTab: Boolean = true) {
  LANDING(RouteNames.LANDING, showTab = false),
  SINGLE_STACK("Single Stack"),
  CUSTOM("Custom"),
  DEEP_LINK("Deep Link"),
  MULTIPANE("Multipane"),
  MULTIPLE_STACKS("Multistack"),
  PERFORMANCE("Performance"),
}

internal val Nav3Scenario.initialRoute: Nav3Route
  get() =
    when (this) {
      Nav3Scenario.LANDING -> Nav3Route.Landing
      Nav3Scenario.SINGLE_STACK -> Nav3Route.SingleStack
      Nav3Scenario.CUSTOM -> Nav3Route.Custom
      Nav3Scenario.DEEP_LINK -> Nav3Route.DeepLink
      Nav3Scenario.MULTIPANE -> Nav3Route.Multipane
      Nav3Scenario.MULTIPLE_STACKS -> Nav3Route.Multistack
      Nav3Scenario.PERFORMANCE -> Nav3Route.Performance(index = 0, generation = 0)
    }
