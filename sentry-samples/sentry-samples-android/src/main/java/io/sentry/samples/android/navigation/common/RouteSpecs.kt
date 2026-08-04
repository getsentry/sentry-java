package io.sentry.samples.android.navigation.common

internal object RouteNames {

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

internal object NavArgs {

  const val ROUTE_NAME = "route_name"
  const val PRODUCT_ID = "product_id"
  const val SOURCE = "source"
  const val CAMPAIGN = "campaign"
  const val ORDER_ID = "order_id"
  const val PROMO_ID = "promo_id"
  const val SCENARIO = "scenario"
}

internal data class DisplayedArgument(val key: String, val label: String = key)

internal data class RouteSpec(
  val routeName: String,
  val title: String,
  val description: String? = null,
  val displayedArguments: List<DisplayedArgument> = emptyList(),
)

internal object RouteSpecs {
  val landing =
    RouteSpec(
      routeName = RouteNames.LANDING,
      title = "Landing",
      description =
        "Activity ui.load transactions are configured when the Sentry SDK initializes, so this " +
          "sample cannot truly disable them at launch time. Instead, we cancel and clear the " +
          "current ui.load transaction when you land here.",
    )

  val home =
    RouteSpec(
      routeName = RouteNames.HOME,
      title = "Home",
      description =
        "Start a product flow, then use the Sentry UI to inspect route " +
          "transactions, breadcrumbs, and screen tracking.",
    )

  val deepLink =
    RouteSpec(
      routeName = RouteNames.DEEP_LINK,
      title = "Deep Link (Fragments)",
      description =
        "Simulates opening a fragment deep link that builds a synthetic backstack before landing " +
          "on a detail destination.",
    )

  val productList =
    RouteSpec(
      routeName = RouteNames.PRODUCT_LIST,
      title = "Product List",
      description = "This route starts the product journey.",
    )

  val productDetail =
    RouteSpec(
      routeName = RouteNames.PRODUCT_DETAIL,
      title = "Product Detail",
      description = "",
      displayedArguments =
        listOf(
          DisplayedArgument(NavArgs.PRODUCT_ID, "productId"),
          DisplayedArgument(NavArgs.SOURCE),
          DisplayedArgument(NavArgs.CAMPAIGN),
        ),
    )

  val checkout =
    RouteSpec(
      routeName = RouteNames.CHECKOUT,
      title = "Checkout",
      description = "",
      displayedArguments = listOf(DisplayedArgument(NavArgs.PRODUCT_ID, "productId")),
    )

  val confirmation =
    RouteSpec(
      routeName = RouteNames.CONFIRMATION,
      title = "Confirmation",
      description = "End of the product flow.",
      displayedArguments = listOf(DisplayedArgument(NavArgs.ORDER_ID, "orderId")),
    )

  val promoDialog =
    RouteSpec(
      routeName = RouteNames.PROMO_DIALOG,
      title = "Promo Dialog",
      description =
        "This modal is a real Nav destination, so its breadcrumbs and route transaction should " +
          "stand on their own.",
      displayedArguments = listOf(DisplayedArgument(NavArgs.PROMO_ID, "promoId")),
    )

  val shareSheet =
    RouteSpec(
      routeName = RouteNames.SHARE_SHEET,
      title = "Share Sheet",
      description =
        "This sheet stays attached to the current route so you can compare an overlay against a " +
          "real destination.",
      displayedArguments = listOf(DisplayedArgument(NavArgs.PRODUCT_ID, "productId")),
    )

  fun get(routeName: String): RouteSpec =
    when (routeName) {
      RouteNames.LANDING -> landing
      RouteNames.HOME -> home
      RouteNames.DEEP_LINK -> deepLink
      RouteNames.PRODUCT_LIST -> productList
      RouteNames.PRODUCT_DETAIL -> productDetail
      RouteNames.CHECKOUT -> checkout
      RouteNames.CONFIRMATION -> confirmation
      RouteNames.PROMO_DIALOG -> promoDialog
      RouteNames.SHARE_SHEET -> shareSheet
      else -> RouteSpec(routeName = routeName, title = routeName)
    }
}
