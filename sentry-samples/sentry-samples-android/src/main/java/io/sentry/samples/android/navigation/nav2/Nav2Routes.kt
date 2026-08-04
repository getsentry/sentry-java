package io.sentry.samples.android.navigation.nav2

import android.os.Bundle
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.navigation.NavDestination
import io.sentry.Sentry
import io.sentry.samples.android.R
import io.sentry.samples.android.navigation.common.DisplayedArgument
import io.sentry.samples.android.navigation.common.NavArgs
import io.sentry.samples.android.navigation.common.RouteNames
import io.sentry.samples.android.navigation.common.RouteSpec
import io.sentry.samples.android.navigation.common.RouteSpecs
import io.sentry.samples.android.navigation.common.displayRoute
import io.sentry.samples.android.navigation.common.tagCurrentNavigationSampleScenario

internal object Nav2RouteNames {
  const val LANDING = RouteNames.LANDING
  const val HOME = RouteNames.HOME
  const val PRODUCT_LIST = RouteNames.PRODUCT_LIST
  const val DEEP_LINK = RouteNames.DEEP_LINK
  const val PRODUCT_DETAIL = RouteNames.PRODUCT_DETAIL
  const val CHECKOUT = RouteNames.CHECKOUT
  const val CONFIRMATION = RouteNames.CONFIRMATION
  const val PROMO_DIALOG = RouteNames.PROMO_DIALOG
  const val SHARE_SHEET = RouteNames.SHARE_SHEET
}

internal object Nav2Args {
  const val ROUTE_NAME = NavArgs.ROUTE_NAME
  const val PRODUCT_ID = NavArgs.PRODUCT_ID
  const val SOURCE = NavArgs.SOURCE
  const val CAMPAIGN = NavArgs.CAMPAIGN
  const val ORDER_ID = NavArgs.ORDER_ID
  const val PROMO_ID = NavArgs.PROMO_ID
  const val SCENARIO = NavArgs.SCENARIO
}

internal typealias Nav2DisplayedArgument = DisplayedArgument

internal typealias Nav2RouteSpec = RouteSpec

internal object Nav2RouteSpecs {
  val landing: RouteSpec
    get() = RouteSpecs.landing

  val home: RouteSpec
    get() = RouteSpecs.home

  val deepLink: RouteSpec
    get() = RouteSpecs.deepLink

  val productList: RouteSpec
    get() = RouteSpecs.productList

  val productDetail: RouteSpec
    get() = RouteSpecs.productDetail

  val checkout: RouteSpec
    get() = RouteSpecs.checkout

  val confirmation: RouteSpec
    get() = RouteSpecs.confirmation

  val promoDialog: RouteSpec
    get() = RouteSpecs.promoDialog

  val shareSheet: RouteSpec
    get() = RouteSpecs.shareSheet

  fun get(routeName: String): RouteSpec = RouteSpecs.get(routeName)
}

internal sealed class Nav2Destination(
  val id: Int,
  val routeName: String,
  val arguments: Bundle = Bundle.EMPTY,
) {

  data object Landing : Nav2Destination(R.id.nav2_landing, RouteNames.LANDING)

  data object Home : Nav2Destination(R.id.nav2_home, RouteNames.HOME)

  data object ProductList : Nav2Destination(R.id.nav2_product_list, RouteNames.PRODUCT_LIST)

  data object DeepLink : Nav2Destination(R.id.nav2_deep_link, RouteNames.DEEP_LINK)

  data class ProductDetail(
    val productId: String,
    val source: String,
    val campaign: String = "",
  ) :
    Nav2Destination(
      R.id.nav2_product_detail,
      RouteNames.PRODUCT_DETAIL,
      bundleOf(
        NavArgs.PRODUCT_ID to productId,
        NavArgs.SOURCE to source,
        NavArgs.CAMPAIGN to campaign,
      ),
    )

  data class Checkout(val productId: String) :
    Nav2Destination(
      R.id.nav2_checkout,
      RouteNames.CHECKOUT,
      bundleOf(NavArgs.PRODUCT_ID to productId),
    )

  data class Confirmation(val orderId: String) :
    Nav2Destination(
      R.id.nav2_confirmation,
      RouteNames.CONFIRMATION,
      bundleOf(NavArgs.ORDER_ID to orderId),
    )

  data class PromoDialog(val promoId: String, val scenario: Nav2Scenario) :
    Nav2Destination(
      R.id.nav2_promo_dialog,
      RouteNames.PROMO_DIALOG,
      bundleOf(NavArgs.PROMO_ID to promoId, NavArgs.SCENARIO to scenario.name),
    )

  data class ShareSheet(val productId: String, val scenario: Nav2Scenario) :
    Nav2Destination(
      R.id.nav2_share_sheet,
      RouteNames.SHARE_SHEET,
      bundleOf(NavArgs.PRODUCT_ID to productId, NavArgs.SCENARIO to scenario.name),
    )
}

internal enum class Nav2Scenario(val label: String, val showTab: Boolean = true) {
  LANDING(RouteNames.LANDING, showTab = false),
  COMPOSE("Compose"),
  FRAGMENTS("Fragments"),
  DEEP_LINK("Deep Link (Fragments)"),
  NAV3_INTEROP("Nav3 Interop"),
  PERFORMANCE("Performance"),
}

internal fun Nav2Destination.routeSpec(): RouteSpec = RouteSpecs.get(routeName)

internal fun Nav2Destination.displayRoute(): String = routeSpec().displayRoute(arguments)

internal fun MutableList<Nav2Destination>.resetTo(destination: Nav2Destination) {
  clear()
  add(destination)
}

internal fun Nav2Destination.matches(destination: NavDestination, arguments: Bundle?): Boolean =
  id == destination.id && argumentsMatch(arguments)

private fun Nav2Destination.argumentsMatch(arguments: Bundle?): Boolean =
  when (this) {
    Nav2Destination.Home,
    Nav2Destination.Landing,
    Nav2Destination.ProductList,
    Nav2Destination.DeepLink -> true
    is Nav2Destination.ProductDetail ->
      arguments?.getString(NavArgs.PRODUCT_ID) == productId &&
        arguments.getString(NavArgs.SOURCE) == source &&
        arguments.getString(NavArgs.CAMPAIGN).orEmpty() == campaign
    is Nav2Destination.Checkout -> arguments?.getString(NavArgs.PRODUCT_ID) == productId
    is Nav2Destination.Confirmation -> arguments?.getString(NavArgs.ORDER_ID) == orderId
    is Nav2Destination.PromoDialog -> arguments?.getString(NavArgs.PROMO_ID) == promoId
    is Nav2Destination.ShareSheet -> arguments?.getString(NavArgs.PRODUCT_ID) == productId
  }

internal fun NavDestination.toNav2Destination(arguments: Bundle?): Nav2Destination? =
  when (id) {
    R.id.nav2_landing -> Nav2Destination.Landing
    R.id.nav2_home -> Nav2Destination.Home
    R.id.nav2_product_list -> Nav2Destination.ProductList
    R.id.nav2_deep_link -> Nav2Destination.DeepLink
    R.id.nav2_product_detail ->
      Nav2Destination.ProductDetail(
        productId = arguments?.getString(NavArgs.PRODUCT_ID).orEmpty(),
        source = arguments?.getString(NavArgs.SOURCE).orEmpty(),
        campaign = arguments?.getString(NavArgs.CAMPAIGN).orEmpty(),
      )
    R.id.nav2_checkout ->
      Nav2Destination.Checkout(arguments?.getString(NavArgs.PRODUCT_ID).orEmpty())
    R.id.nav2_confirmation ->
      Nav2Destination.Confirmation(arguments?.getString(NavArgs.ORDER_ID).orEmpty())
    R.id.nav2_promo_dialog ->
      Nav2Destination.PromoDialog(
        promoId = arguments?.getString(NavArgs.PROMO_ID).orEmpty(),
        scenario =
          arguments?.getString(NavArgs.SCENARIO).orEmpty().toNav2Scenario()
            ?: Nav2Scenario.FRAGMENTS,
      )
    R.id.nav2_share_sheet ->
      Nav2Destination.ShareSheet(
        productId = arguments?.getString(NavArgs.PRODUCT_ID).orEmpty(),
        scenario =
          arguments?.getString(NavArgs.SCENARIO).orEmpty().toNav2Scenario()
            ?: Nav2Scenario.FRAGMENTS,
      )
    else -> null
  }

internal fun NavDestination.routeName(): String = routeNameOrNull() ?: RouteNames.HOME

private fun NavDestination.routeNameOrNull(): String? = route ?: label?.toString()?.replace(" ", "")

private fun String.toNav2Scenario(): Nav2Scenario? =
  Nav2Scenario.entries.firstOrNull { scenario -> scenario.name == this }

internal fun recordManualChildSpan(routeName: String) {
  val span =
    Sentry.getSpan()
      ?.startChild(
        "test.navigation.manual_span",
        "Nav2 /$routeName manual span",
      )
  span?.setData("sample.manual_span", true)
  span?.finish()
}

internal fun tagCurrentNav2Scenario(scenario: Nav2Scenario) {
  Sentry.getSpan()?.let { span ->
    tagCurrentNavigationSampleScenario(scenario.label)
    span.setTag(NAV2_SCENARIO_TAG, scenario.label)
  }
  Sentry.configureScope { scope ->
    scope.withTransaction { transaction -> transaction?.setTag(NAV2_SCENARIO_TAG, scenario.label) }
  }
}

internal fun tagNav2SampleAction(action: String, route: String) {
  val span = Sentry.getSpan() ?: return
  span.setTag("sample_action", "nav2_$action")
  span.setTag("sample_nav2_route", route)
}

internal fun <T> MutableList<T>.popTrackedBackStack(popBackStack: () -> Boolean): Boolean {
  val popped = popBackStack()
  if (popped && size > 1) {
    removeAt(lastIndex)
  }
  return popped
}

internal const val NAV2_SCENARIO_TAG = "sample_nav2_scenario"

internal const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
internal const val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT
