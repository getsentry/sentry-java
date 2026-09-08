package io.sentry.samples.android.navigation

import android.os.Bundle
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.navigation.NavDestination
import io.sentry.Sentry
import io.sentry.protocol.SentryTransaction
import io.sentry.samples.android.R
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

internal object Nav2RouteNames {

  const val LANDING = "Landing"
  const val HOME = "Home"
  const val PRODUCT_LIST = "ProductList"
  const val DEEP_LINK = "DeepLink"
  const val PRODUCT_DETAIL = "ProductDetail"
  const val CHECKOUT = "Checkout"
  const val CONFIRMATION = "Confirmation"
  const val PROMO_DIALOG = "PromoDialog"
  const val SHARE_SHEET = "ShareSheet"
}

internal object Nav2Args {

  const val ROUTE_NAME = "route_name"
  const val PRODUCT_ID = "product_id"
  const val SOURCE = "source"
  const val CAMPAIGN = "campaign"
  const val ORDER_ID = "order_id"
  const val PROMO_ID = "promo_id"
  const val SCENARIO = "scenario"
}

internal data class Nav2DisplayedArgument(val key: String, val label: String = key)

internal data class Nav2RouteSpec(
  val routeName: String,
  val title: String,
  val description: String? = null,
  val displayedArguments: List<Nav2DisplayedArgument> = emptyList(),
)

internal object Nav2RouteSpecs {
  val landing =
    Nav2RouteSpec(
      routeName = Nav2RouteNames.LANDING,
      title = "Landing",
      description =
        "Activity ui.load transactions are configured when the Sentry SDK initializes, so this " +
          "sample cannot truly disable them at launch time. Instead, we cancel and clear the " +
          "current ui.load transaction when you land here.",
    )

  val home =
    Nav2RouteSpec(
      routeName = Nav2RouteNames.HOME,
      title = "Home",
      description =
        "Start a product flow, then use the Sentry UI to inspect route " +
          "transactions, breadcrumbs, and screen tracking.",
    )

  val deepLink =
    Nav2RouteSpec(
      routeName = Nav2RouteNames.DEEP_LINK,
      title = "Deep Link (Fragments)",
      description =
        "Simulates opening a fragment deep link that builds a synthetic backstack before landing " +
          "on a detail destination.",
    )

  val productList =
    Nav2RouteSpec(
      routeName = Nav2RouteNames.PRODUCT_LIST,
      title = "Product List",
      description = "This route starts the product journey.",
    )

  val productDetail =
    Nav2RouteSpec(
      routeName = Nav2RouteNames.PRODUCT_DETAIL,
      title = "Product Detail",
      description = "",
      displayedArguments =
        listOf(
          Nav2DisplayedArgument(Nav2Args.PRODUCT_ID, "productId"),
          Nav2DisplayedArgument(Nav2Args.SOURCE),
          Nav2DisplayedArgument(Nav2Args.CAMPAIGN),
        ),
    )

  val checkout =
    Nav2RouteSpec(
      routeName = Nav2RouteNames.CHECKOUT,
      title = "Checkout",
      description = "",
      displayedArguments = listOf(Nav2DisplayedArgument(Nav2Args.PRODUCT_ID, "productId")),
    )

  val confirmation =
    Nav2RouteSpec(
      routeName = Nav2RouteNames.CONFIRMATION,
      title = "Confirmation",
      description = "End of the product flow.",
      displayedArguments = listOf(Nav2DisplayedArgument(Nav2Args.ORDER_ID, "orderId")),
    )

  val promoDialog =
    Nav2RouteSpec(
      routeName = Nav2RouteNames.PROMO_DIALOG,
      title = "Promo Dialog",
      description =
        "This modal is a real Nav destination, so its breadcrumbs and route transaction should " +
          "stand on their own.",
      displayedArguments = listOf(Nav2DisplayedArgument(Nav2Args.PROMO_ID, "promoId")),
    )

  val shareSheet =
    Nav2RouteSpec(
      routeName = Nav2RouteNames.SHARE_SHEET,
      title = "Share Sheet",
      description =
        "This sheet stays attached to the current route so you can compare an overlay against a " +
          "real destination.",
      displayedArguments = listOf(Nav2DisplayedArgument(Nav2Args.PRODUCT_ID, "productId")),
    )

  fun get(routeName: String): Nav2RouteSpec =
    when (routeName) {
      Nav2RouteNames.LANDING -> landing
      Nav2RouteNames.HOME -> home
      Nav2RouteNames.DEEP_LINK -> deepLink
      Nav2RouteNames.PRODUCT_LIST -> productList
      Nav2RouteNames.PRODUCT_DETAIL -> productDetail
      Nav2RouteNames.CHECKOUT -> checkout
      Nav2RouteNames.CONFIRMATION -> confirmation
      Nav2RouteNames.PROMO_DIALOG -> promoDialog
      Nav2RouteNames.SHARE_SHEET -> shareSheet
      else -> Nav2RouteSpec(routeName = routeName, title = routeName)
    }
}

internal sealed class Nav2Destination(
  val id: Int,
  val routeName: String,
  val arguments: Bundle = Bundle.EMPTY,
) {

  data object Landing : Nav2Destination(R.id.nav2_landing, Nav2RouteNames.LANDING)

  data object Home : Nav2Destination(R.id.nav2_home, Nav2RouteNames.HOME)

  data object ProductList : Nav2Destination(R.id.nav2_product_list, Nav2RouteNames.PRODUCT_LIST)

  data object DeepLink : Nav2Destination(R.id.nav2_deep_link, Nav2RouteNames.DEEP_LINK)

  data class ProductDetail(
    val productId: String,
    val source: String,
    val campaign: String = "",
  ) :
    Nav2Destination(
      R.id.nav2_product_detail,
      Nav2RouteNames.PRODUCT_DETAIL,
      bundleOf(
        Nav2Args.PRODUCT_ID to productId,
        Nav2Args.SOURCE to source,
        Nav2Args.CAMPAIGN to campaign,
      ),
    )

  data class Checkout(val productId: String) :
    Nav2Destination(
      R.id.nav2_checkout,
      Nav2RouteNames.CHECKOUT,
      bundleOf(Nav2Args.PRODUCT_ID to productId),
    )

  data class Confirmation(val orderId: String) :
    Nav2Destination(
      R.id.nav2_confirmation,
      Nav2RouteNames.CONFIRMATION,
      bundleOf(Nav2Args.ORDER_ID to orderId),
    )

  data class PromoDialog(val promoId: String, val scenario: Nav2Scenario) :
    Nav2Destination(
      R.id.nav2_promo_dialog,
      Nav2RouteNames.PROMO_DIALOG,
      bundleOf(Nav2Args.PROMO_ID to promoId, Nav2Args.SCENARIO to scenario.name),
    )

  data class ShareSheet(val productId: String, val scenario: Nav2Scenario) :
    Nav2Destination(
      R.id.nav2_share_sheet,
      Nav2RouteNames.SHARE_SHEET,
      bundleOf(Nav2Args.PRODUCT_ID to productId, Nav2Args.SCENARIO to scenario.name),
    )
}

internal enum class Nav2Scenario(val label: String, val showTab: Boolean = true) {
  LANDING(Nav2RouteNames.LANDING, showTab = false),
  COMPOSE("Compose"),
  FRAGMENTS("Fragments"),
  DEEP_LINK("Deep Link (Fragments)"),
  PERFORMANCE("Performance"),
}

/**
 * Optional work the Nav2 sample app can perform when a route becomes active / when navigating to a
 * new destination.
 */
internal enum class RouteWorkOption(val label: String, val tagName: String) {

  /**
   * Executes an HTTP request in the new nav destination.
   *
   * For composables, the request is executed in a composable *Effect.
   */
  HTTP_REQUEST("HTTP request", "http_request"),

  /**
   * Generates a child span in the new nav destination.
   *
   * For composables, the span is generated directly in the composable body (i.e., during
   * (re)composition), rather than via an *Effect. That's bad practice generally, but it lets us
   * test whether our nav transactions can pick up work done in the destination during composition.
   */
  MANUAL_CHILD_SPAN("Manual child span", "manual_child_span"),
}

internal fun Nav2Destination.routeSpec(): Nav2RouteSpec = Nav2RouteSpecs.get(routeName)

internal fun Nav2Destination.displayRoute(): String = routeSpec().displayRoute(arguments)

internal fun Nav2RouteSpec.displayArguments(arguments: Bundle?): List<Pair<String, String>> =
  displayedArguments.mapNotNull { argument ->
    arguments
      ?.getString(argument.key)
      ?.takeIf { value -> value.isNotEmpty() }
      ?.let { value -> argument.label to value }
  }

internal fun Nav2RouteSpec.displayArguments(
  arguments: Map<String, Any?>
): List<Pair<String, String>> = displayedArguments.mapNotNull { argument ->
  arguments[argument.key]
    ?.toString()
    ?.takeIf { value -> value.isNotEmpty() }
    ?.let { value -> argument.label to value }
}

internal fun Nav2RouteSpec.displayRoute(arguments: Bundle?): String =
  displayRoute(displayArguments(arguments))

internal fun Nav2RouteSpec.displayRoute(arguments: Map<String, Any?>): String =
  displayRoute(displayArguments(arguments))

private fun Nav2RouteSpec.displayRoute(displayArguments: List<Pair<String, String>>): String =
  if (displayArguments.isEmpty()) {
    "/$routeName"
  } else {
    "/$routeName { ${displayArguments.toDisplayString()} }"
  }

internal fun List<Pair<String, String>>.toDisplayString(): String =
  joinToString(", ") { (label, value) -> "$label=$value" }

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
      arguments?.getString(Nav2Args.PRODUCT_ID) == productId &&
        arguments.getString(Nav2Args.SOURCE) == source &&
        arguments.getString(Nav2Args.CAMPAIGN).orEmpty() == campaign
    is Nav2Destination.Checkout -> arguments?.getString(Nav2Args.PRODUCT_ID) == productId
    is Nav2Destination.Confirmation -> arguments?.getString(Nav2Args.ORDER_ID) == orderId
    is Nav2Destination.PromoDialog -> arguments?.getString(Nav2Args.PROMO_ID) == promoId
    is Nav2Destination.ShareSheet -> arguments?.getString(Nav2Args.PRODUCT_ID) == productId
  }

internal fun NavDestination.toNav2Destination(arguments: Bundle?): Nav2Destination? =
  when (id) {
    R.id.nav2_landing -> Nav2Destination.Landing
    R.id.nav2_home -> Nav2Destination.Home
    R.id.nav2_product_list -> Nav2Destination.ProductList
    R.id.nav2_deep_link -> Nav2Destination.DeepLink
    R.id.nav2_product_detail ->
      Nav2Destination.ProductDetail(
        productId = arguments?.getString(Nav2Args.PRODUCT_ID).orEmpty(),
        source = arguments?.getString(Nav2Args.SOURCE).orEmpty(),
        campaign = arguments?.getString(Nav2Args.CAMPAIGN).orEmpty(),
      )
    R.id.nav2_checkout ->
      Nav2Destination.Checkout(arguments?.getString(Nav2Args.PRODUCT_ID).orEmpty())
    R.id.nav2_confirmation ->
      Nav2Destination.Confirmation(arguments?.getString(Nav2Args.ORDER_ID).orEmpty())
    R.id.nav2_promo_dialog ->
      Nav2Destination.PromoDialog(
        promoId = arguments?.getString(Nav2Args.PROMO_ID).orEmpty(),
        scenario =
          arguments?.getString(Nav2Args.SCENARIO).orEmpty().toNav2Scenario()
            ?: Nav2Scenario.FRAGMENTS,
      )
    R.id.nav2_share_sheet ->
      Nav2Destination.ShareSheet(
        productId = arguments?.getString(Nav2Args.PRODUCT_ID).orEmpty(),
        scenario =
          arguments?.getString(Nav2Args.SCENARIO).orEmpty().toNav2Scenario()
            ?: Nav2Scenario.FRAGMENTS,
      )
    else -> null
  }

internal fun NavDestination.routeName(): String = routeNameOrNull() ?: Nav2RouteNames.HOME

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
  Sentry.getSpan()?.setTag(NAV2_SCENARIO_TAG, scenario.label)
}

internal fun SentryTransaction.nav2ScenarioLabel(): String =
  getTag(NAV2_SCENARIO_TAG) ?: UNKNOWN_NAV2_SCENARIO_LABEL

internal suspend fun recordSimulatedBackgroundSpan(routeName: String) {
  val parentSpan = Sentry.getSpan()
  suspendCancellableCoroutine { continuation ->
    val worker = Thread {
      val span =
        parentSpan?.startChild(
          "test.navigation.background_work",
          "Nav2 /$routeName background work",
        )
      span?.setData("sample.background_work", true)
      try {
        Thread.sleep(BACKGROUND_WORK_MILLIS)
      } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
      } finally {
        span?.finish()
        if (continuation.isActive) {
          continuation.resume(Unit)
        }
      }
    }
    continuation.invokeOnCancellation { worker.interrupt() }
    worker.start()
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

internal const val SENTRY_FLUSH_TIMEOUT_MILLIS = 5000L
internal const val BACKGROUND_WORK_MILLIS = 1000L
internal const val NAV2_SCENARIO_TAG = "sample_nav2_scenario"
internal const val UNKNOWN_NAV2_SCENARIO_LABEL = "Unknown"

internal const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
internal const val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT
