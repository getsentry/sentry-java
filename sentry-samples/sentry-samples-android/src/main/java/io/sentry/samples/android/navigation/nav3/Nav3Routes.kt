package io.sentry.samples.android.navigation.nav3

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.EntryProviderScope
import io.sentry.samples.android.navigation.common.NavArgs
import io.sentry.samples.android.navigation.common.RouteNames
import io.sentry.samples.android.navigation.common.RouteSpec
import io.sentry.samples.android.navigation.common.RouteSpecs
import io.sentry.samples.android.navigation.common.displayRoute
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    Nav3Route.MultiActivity ->
      RouteSpec(
        routeName = Nav3Route.MultiActivity.routeName,
        title = "Multi-Activity",
        description =
          "Tests how SentryNavEffect uses NavScopes to coordinate state updates between " +
            "SentryNavEffect instances. Each Activity owns its own SentryNavEffect and NavDisplay.",
      )
    Nav3Route.MultiFragment ->
      RouteSpec(
        routeName = Nav3Route.MultiFragment.routeName,
        title = "Multi-Fragment",
        description =
          "Replace a Products Fragment with a Checkout Fragment. Each Fragment owns its own " +
            "SentryNavEffect and NavDisplay. Try a slow transition and press Back during it.",
      )
    Nav3Route.FragmentOverlay ->
      RouteSpec(
        routeName = Nav3Route.FragmentOverlay.routeName,
        title = "Fragment: Overlay",
        description =
          "Open a full-screen Checkout DialogFragment over a Products Fragment. Both own a " +
            "SentryNavEffect and NavDisplay, and Products stays resumed underneath. Back dismisses " +
            "the dialog and reveals Products.",
      )
    Nav3Route.ViewPager2 ->
      RouteSpec(
        routeName = Nav3Route.ViewPager2.routeName,
        title = "ViewPager2",
        description =
          "Each page is a Fragment with its own SentryNavEffect and NavDisplay. Neighboring " +
            "pages are preloaded at STARTED. Back returns to the Products Fragment underneath.",
      )
    Nav3Route.DeepLink ->
      RouteSpec(
        routeName = Nav3Route.DeepLink.routeName,
        title = "Deep Link",
        description =
          "Simulates opening a deep link that builds a synthetic backstack before landing on a " +
            "detail destination.",
      )
    Nav3Route.Nested ->
      RouteSpec(
        routeName = Nav3Route.Nested.routeName,
        title = "Nested NavDisplay",
        description =
          "A NavDisplay inside this route, with its own back stack. Only the outer NavDisplay should record: in Manual mode, the inner one has no SentryNavEffect; with auto-instrumentation, both are instrumented and the inner one stays dormant.",
      )
    Nav3Route.Overlays ->
      RouteSpec(
        routeName = Nav3Route.Overlays.routeName,
        title = "NavDisplay in Overlays",
        description =
          "Opens a NavDisplay inside a Compose dialog and inside a bottom sheet. Both have their own window, but should belong to this Activity's nav root: only the Activity's NavDisplay should record.",
      )
    Nav3Route.Nav2Host ->
      RouteSpec(
        routeName = Nav3Route.Nav2Host.routeName,
        title = "Nav3 in a Nav2 Destination",
        description =
          "Opens an Activity whose Navigation 2 NavHost has a destination containing a NavDisplay. That destination's lifecycle owner pauses during Nav2 transitions, which shouldn't affect what the NavDisplay records. With the plugin's Compose option, Nav2 records its destinations too.",
      )
    Nav3Route.Uncovered ->
      RouteSpec(
        routeName = Nav3Route.Uncovered.routeName,
        title = "Setups Auto-Instrumentation Doesn't Cover",
        description =
          "Opens an Activity with setups that auto-instrumentation doesn't handle on its own: NavDisplay(entries), NavDisplays side by side in a pager, and NavDisplays in both an Activity and its Fragment.",
      )
    Nav3Route.RouteKeys ->
      RouteSpec(
        routeName = Nav3Route.RouteKeys.routeName,
        title = "Route Key Naming",
        description =
          "Pushes keys that BackStackEntryMapper.serialName(), which auto-instrumentation uses, names differently from the sample's own mapper. Compare the recorded names in Manual and Auto-instrumentation modes, ideally in a minified release build.",
      )
    Nav3Route.KeyDefaultSerialName,
    Nav3Route.KeyNotSerializable,
    is Nav3Route.KeyGeneric<*>,
    PrivateObjectRouteKey -> RouteSpec(routeName = routeName, title = routeName)
    Nav3Route.ProductList -> RouteSpecs.productList
    is Nav3Route.ProductDetail -> RouteSpecs.productDetail
    is Nav3Route.Checkout -> RouteSpecs.checkout
    is Nav3Route.Confirmation -> RouteSpecs.confirmation
    is Nav3Route.PromoDialog -> RouteSpecs.promoDialog
    is Nav3Route.ShareSheet -> RouteSpecs.shareSheet
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
      Nav3Route.MultiActivity -> putString("type", "multi_activity")
      Nav3Route.MultiFragment -> putString("type", "multi_fragment")
      Nav3Route.FragmentOverlay -> putString("type", "fragment_overlay")
      Nav3Route.ViewPager2 -> putString("type", "view_pager2")
      Nav3Route.DeepLink -> putString("type", "deep_link")
      Nav3Route.KeyDefaultSerialName -> putString("type", "key_default_serial_name")
      Nav3Route.KeyNotSerializable -> putString("type", "key_not_serializable")
      is Nav3Route.KeyGeneric<*> -> putString("type", "key_generic")
      PrivateObjectRouteKey -> putString("type", "key_private_object")
      Nav3Route.RouteKeys -> putString("type", "route_keys")
      Nav3Route.Uncovered -> putString("type", "uncovered")
      Nav3Route.Nav2Host -> putString("type", "nav2_host")
      Nav3Route.Overlays -> putString("type", "overlays")
      Nav3Route.Nested -> putString("type", "nested")
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
    "multi_activity" -> Nav3Route.MultiActivity
    "multi_fragment",
    "fragment_replace" -> Nav3Route.MultiFragment
    "dialog_overlay",
    "fragment_overlay" -> Nav3Route.FragmentOverlay
    "view_pager_pages",
    "view_pager2" -> Nav3Route.ViewPager2
    "deep_link" -> Nav3Route.DeepLink
    "key_default_serial_name" -> Nav3Route.KeyDefaultSerialName
    "key_not_serializable" -> Nav3Route.KeyNotSerializable
    "key_generic" -> Nav3Route.KeyGeneric(value = 1)
    "key_private_object" -> PrivateObjectRouteKey
    "route_keys" -> Nav3Route.RouteKeys
    "uncovered" -> Nav3Route.Uncovered
    "nav2_host" -> Nav3Route.Nav2Host
    "overlays" -> Nav3Route.Overlays
    "nested" -> Nav3Route.Nested
    "product_list" -> Nav3Route.ProductList
    "product_detail" -> {
      val productId = getString("product_id") ?: return Nav3Route.SingleStack
      val source = getString("source") ?: return Nav3Route.SingleStack
      Nav3Route.ProductDetail(
        productId = productId,
        source = source,
        campaign = getString("campaign"),
      )
    }
    "checkout" ->
      Nav3Route.Checkout(productId = getString("product_id") ?: return Nav3Route.SingleStack)
    "confirmation" ->
      Nav3Route.Confirmation(orderId = getString("order_id") ?: return Nav3Route.SingleStack)
    "promo_dialog" ->
      Nav3Route.PromoDialog(promoId = getString("promo_id") ?: return Nav3Route.SingleStack)
    "share_sheet" ->
      Nav3Route.ShareSheet(productId = getString("product_id") ?: return Nav3Route.SingleStack)
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
    Nav3Scenario.MULTI_ACTIVITY -> resetTo(Nav3Route.MultiActivity)
    Nav3Scenario.MULTI_FRAGMENT -> resetTo(Nav3Route.MultiFragment)
    Nav3Scenario.FRAGMENT_OVERLAY -> resetTo(Nav3Route.FragmentOverlay)
    Nav3Scenario.VIEW_PAGER2 -> resetTo(Nav3Route.ViewPager2)
    Nav3Scenario.DEEP_LINK -> resetTo(Nav3Route.DeepLink)
    Nav3Scenario.ROUTE_KEYS -> resetTo(Nav3Route.RouteKeys)
    Nav3Scenario.UNCOVERED -> resetTo(Nav3Route.Uncovered)
    Nav3Scenario.NAV2_HOST -> resetTo(Nav3Route.Nav2Host)
    Nav3Scenario.OVERLAYS -> resetTo(Nav3Route.Overlays)
    Nav3Scenario.NESTED -> resetTo(Nav3Route.Nested)
    Nav3Scenario.CUSTOM -> resetTo(Nav3Route.Custom)
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

/**
 * Back stack keys of the Nav3 sample.
 *
 * Each key is `@Serializable` with a `@SerialName` equal to its [routeName], so
 * `BackStackEntryMapper.serialName()`, which auto-instrumentation uses, names routes the same way
 * as the sample's own mapper. Properties are getters so they aren't serialized.
 */
internal sealed interface Nav3Route {
  val routeName: String
  val arguments: Map<String, Any?>
    get() = emptyMap()

  val previewName: String
    get() = routeName

  val performanceSeed: Int
    get() = hashCode()

  @Serializable
  @SerialName(RouteNames.LANDING)
  data object Landing : Nav3Route {
    override val routeName: String
      get() = RouteNames.LANDING
  }

  @Serializable
  @SerialName(RouteNames.HOME)
  data object SingleStack : Nav3Route {
    override val routeName: String
      get() = RouteNames.HOME
  }

  @Serializable
  @SerialName("Custom")
  data object Custom : Nav3Route {
    override val routeName: String
      get() = "Custom"
  }

  @Serializable
  @SerialName("MultiActivity")
  data object MultiActivity : Nav3Route {
    override val routeName: String
      get() = "MultiActivity"
  }

  @Serializable
  @SerialName("MultiFragment")
  data object MultiFragment : Nav3Route {
    override val routeName: String
      get() = "MultiFragment"
  }

  @Serializable
  @SerialName("FragmentOverlay")
  data object FragmentOverlay : Nav3Route {
    override val routeName: String
      get() = "FragmentOverlay"
  }

  @Serializable
  @SerialName("ViewPager2")
  data object ViewPager2 : Nav3Route {
    override val routeName: String
      get() = "ViewPager2"
  }

  @Serializable
  @SerialName(RouteNames.DEEP_LINK)
  data object DeepLink : Nav3Route {
    override val routeName: String
      get() = RouteNames.DEEP_LINK
  }

  @Serializable
  @SerialName("Nested")
  data object Nested : Nav3Route {
    override val routeName: String
      get() = "Nested"
  }

  @Serializable
  @SerialName("Overlays")
  data object Overlays : Nav3Route {
    override val routeName: String
      get() = "Overlays"
  }

  @Serializable
  @SerialName("Nav2Host")
  data object Nav2Host : Nav3Route {
    override val routeName: String
      get() = "Nav2Host"
  }

  @Serializable
  @SerialName("Uncovered")
  data object Uncovered : Nav3Route {
    override val routeName: String
      get() = "Uncovered"
  }

  @Serializable
  @SerialName("RouteKeys")
  data object RouteKeys : Nav3Route {
    override val routeName: String
      get() = "RouteKeys"
  }

  /** No `@SerialName`: auto-instrumentation records its fully qualified class name. */
  @Serializable
  data object KeyDefaultSerialName : Nav3Route {
    override val routeName: String
      get() = "KeyDefaultSerialName"
  }

  /** Not `@Serializable`: auto-instrumentation records it as /unknown. */
  data object KeyNotSerializable : Nav3Route {
    override val routeName: String
      get() = "KeyNotSerializable"
  }

  /**
   * Generic: kotlinx.serialization can't look up its serializer without type arguments, so
   * auto-instrumentation records it as /unknown.
   */
  @Serializable
  @SerialName("KeyGeneric")
  data class KeyGeneric<T : Any>(val value: T) : Nav3Route {
    override val routeName: String
      get() = "KeyGeneric"
  }

  @Serializable
  @SerialName(RouteNames.PRODUCT_LIST)
  data object ProductList : Nav3Route {
    override val routeName: String
      get() = RouteNames.PRODUCT_LIST
  }

  @Serializable
  @SerialName(RouteNames.PRODUCT_DETAIL)
  data class ProductDetail(
    val productId: String,
    val source: String,
    val campaign: String? = null,
  ) : Nav3Route {
    override val routeName: String
      get() = RouteNames.PRODUCT_DETAIL

    override val arguments: Map<String, Any?>
      get() =
        mapOf(
            NavArgs.PRODUCT_ID to productId,
            NavArgs.SOURCE to source,
            NavArgs.CAMPAIGN to campaign,
          )
          .filterValues { it != null }

    override val previewName: String
      get() = "ProductDetail($productId)"
  }

  @Serializable
  @SerialName(RouteNames.CHECKOUT)
  data class Checkout(val productId: String) : Nav3Route {
    override val routeName: String
      get() = RouteNames.CHECKOUT

    override val arguments: Map<String, Any?>
      get() = mapOf(NavArgs.PRODUCT_ID to productId)

    override val previewName: String
      get() = "Checkout($productId)"
  }

  @Serializable
  @SerialName(RouteNames.CONFIRMATION)
  data class Confirmation(val orderId: String) : Nav3Route {
    override val routeName: String
      get() = RouteNames.CONFIRMATION

    override val arguments: Map<String, Any?>
      get() = mapOf(NavArgs.ORDER_ID to orderId)

    override val previewName: String
      get() = "Confirmation($orderId)"
  }

  @Serializable
  @SerialName(RouteNames.PROMO_DIALOG)
  data class PromoDialog(val promoId: String) : Nav3Route {
    override val routeName: String
      get() = RouteNames.PROMO_DIALOG

    override val arguments: Map<String, Any?>
      get() = mapOf(NavArgs.PROMO_ID to promoId)

    override val previewName: String
      get() = "PromoDialog($promoId)"
  }

  @Serializable
  @SerialName(RouteNames.SHARE_SHEET)
  data class ShareSheet(val productId: String) : Nav3Route {
    override val routeName: String
      get() = RouteNames.SHARE_SHEET

    override val arguments: Map<String, Any?>
      get() = mapOf(NavArgs.PRODUCT_ID to productId)

    override val previewName: String
      get() = "ShareSheet($productId)"
  }

  @Serializable
  @SerialName("Performance")
  data class Performance(val index: Int, val generation: Int) : Nav3Route {
    override val routeName: String
      get() = "Performance"

    override val arguments: Map<String, Any?>
      get() = mapOf("index" to index, "generation" to generation)

    override val previewName: String
      get() = "Performance($index:$generation)"

    override val performanceSeed: Int
      get() = 31 * index + generation
  }
}

internal enum class Nav3Scenario(val label: String, val showTab: Boolean = true) {
  LANDING(RouteNames.LANDING, showTab = false),
  SINGLE_STACK("Single Stack"),
  DEEP_LINK("Deep Link"),
  MULTI_ACTIVITY("Multi-Activity"),
  MULTI_FRAGMENT("Multi-Fragment"),
  FRAGMENT_OVERLAY("Fragment: Overlay"),
  VIEW_PAGER2("ViewPager2"),
  NESTED("Nested"),
  OVERLAYS("Overlays"),
  NAV2_HOST("Nav3 in Nav2"),
  UNCOVERED("Not Covered"),
  ROUTE_KEYS("Route Keys"),
  CUSTOM("Custom"),
  PERFORMANCE("Performance"),
}

internal val Nav3Scenario.initialRoute: Nav3Route
  get() =
    when (this) {
      Nav3Scenario.LANDING -> Nav3Route.Landing
      Nav3Scenario.SINGLE_STACK -> Nav3Route.SingleStack
      Nav3Scenario.MULTI_ACTIVITY -> Nav3Route.MultiActivity
      Nav3Scenario.MULTI_FRAGMENT -> Nav3Route.MultiFragment
      Nav3Scenario.FRAGMENT_OVERLAY -> Nav3Route.FragmentOverlay
      Nav3Scenario.VIEW_PAGER2 -> Nav3Route.ViewPager2
      Nav3Scenario.DEEP_LINK -> Nav3Route.DeepLink
      Nav3Scenario.ROUTE_KEYS -> Nav3Route.RouteKeys
      Nav3Scenario.UNCOVERED -> Nav3Route.Uncovered
      Nav3Scenario.NAV2_HOST -> Nav3Route.Nav2Host
      Nav3Scenario.OVERLAYS -> Nav3Route.Overlays
      Nav3Scenario.NESTED -> Nav3Route.Nested
      Nav3Scenario.CUSTOM -> Nav3Route.Custom
      Nav3Scenario.PERFORMANCE -> Nav3Route.Performance(index = 0, generation = 0)
    }

/**
 * A private `@Serializable` object, whose serializer the SDK looks up through the members the
 * serialization plugin generates. Auto-instrumentation records its fully qualified class name.
 */
@Serializable
private data object PrivateObjectRouteKey : Nav3Route {
  override val routeName: String
    get() = "PrivateObjectRouteKey"
}

/** The keys [Nav3Route.RouteKeys] pushes, with the names each mapper should record. */
internal val routeKeyExamples: List<RouteKeyExample> =
  listOf(
    RouteKeyExample(
      key = Nav3Route.KeyDefaultSerialName,
      autoName = "/io.sentry.samples.android.navigation.nav3.Nav3Route.KeyDefaultSerialName",
    ),
    RouteKeyExample(key = Nav3Route.KeyNotSerializable, autoName = "/unknown"),
    RouteKeyExample(key = Nav3Route.KeyGeneric(value = 1), autoName = "/unknown"),
    RouteKeyExample(
      key = PrivateObjectRouteKey,
      autoName = "/io.sentry.samples.android.navigation.nav3.PrivateObjectRouteKey",
    ),
  )

internal class RouteKeyExample(val key: Nav3Route, val autoName: String) {
  val manualName: String
    get() = "/${key.routeName}"
}

/**
 * Adds an entry for each of [routeKeyExamples]' key types, showing [content]. Lives in this file
 * because [PrivateObjectRouteKey] is private to it.
 */
internal fun EntryProviderScope<Nav3Route>.routeKeyEntries(
  content: @Composable (Nav3Route) -> Unit
) {
  entry<Nav3Route.KeyDefaultSerialName> { route -> content(route) }
  entry<Nav3Route.KeyNotSerializable> { route -> content(route) }
  entry<Nav3Route.KeyGeneric<*>> { route -> content(route) }
  entry<PrivateObjectRouteKey> { route -> content(route) }
}
