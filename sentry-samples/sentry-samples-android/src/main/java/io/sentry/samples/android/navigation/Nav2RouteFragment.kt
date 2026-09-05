package io.sentry.samples.android.navigation

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.doOnPreDraw
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.sentry.ISpan
import io.sentry.Sentry
import io.sentry.samples.android.R
import kotlinx.coroutines.launch

class Nav2RouteFragment : Fragment() {

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    val activity = requireActivity() as Nav2Activity
    val routeName = requireArguments().getString(Nav2Args.ROUTE_NAME).orEmpty()
    val routeSpec = Nav2RouteSpecs.get(routeName)

    return when (routeName) {
      Nav2RouteNames.LANDING -> routeLayout(routeSpec)

      Nav2RouteNames.HOME ->
        routeLayout(
          routeSpec,
          buttons =
            listOf(
              RouteButton(R.id.nav2_fragment_browse_products, "Browse Products") {
                activity.navigateTo(Nav2Destination.ProductList)
              }
            ),
        )

      Nav2RouteNames.DEEP_LINK ->
        routeLayout(
          routeSpec,
          buttons =
            listOf(
              RouteButton(R.id.nav2_fragment_open_deep_link, "Go to deep link destination") {
                activity.openSyntheticProductDeepLink()
              }
            ),
        )

      Nav2RouteNames.PRODUCT_LIST ->
        routeLayout(
          routeSpec,
          buttons =
            listOf(
              RouteButton(R.id.nav2_fragment_open_product_42, "Open Product 42") {
                activity.navigateTo(
                  Nav2Destination.ProductDetail("42", "product-list", "summer-sale")
                )
              },
              RouteButton(R.id.nav2_fragment_open_product_7, "Open Product 7") {
                activity.navigateTo(Nav2Destination.ProductDetail("7", "product-list"))
              },
            ),
        )

      Nav2RouteNames.PRODUCT_DETAIL -> productDetailLayout(activity)

      Nav2RouteNames.CHECKOUT -> checkoutLayout(activity)

      Nav2RouteNames.CONFIRMATION -> confirmationLayout(activity)

      else ->
        routeLayout(
          Nav2RouteSpec(routeName = routeName, title = "Unknown Route", description = routeName)
        )
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val activity = requireActivity() as Nav2Activity
    val routeName = requireArguments().getString(Nav2Args.ROUTE_NAME).orEmpty()

    activity.tagCurrentScenarioOnTransaction()

    if (routeName == Nav2RouteNames.LANDING) {
      view.post { activity.cancelCurrentUiLoadTransaction() }
      return
    }

    if (routeName == Nav2RouteNames.PRODUCT_DETAIL) {
      viewLifecycleOwner.lifecycleScope.launch {
        recordSimulatedBackgroundSpan(routeName)
      }
    }

    activity.runRouteWorkAction(routeName)
  }

  private fun productDetailLayout(activity: Nav2Activity): View {
    val arguments = requireArguments()
    val productId = arguments.getString(Nav2Args.PRODUCT_ID).orEmpty()
    val source = arguments.getString(Nav2Args.SOURCE).orEmpty()
    val scenario =
      if (source == "deep-link") {
        Nav2Scenario.DEEP_LINK
      } else {
        Nav2Scenario.FRAGMENTS
      }

    val routeSpec = Nav2RouteSpecs.productDetail
    return routeLayout(
      routeSpec,
      arguments = arguments,
      buttons =
        listOf(
          RouteButton(R.id.nav2_fragment_show_promo_dialog, "Show Promo Dialog") {
            activity.navigateTo(Nav2Destination.PromoDialog("detail-$productId", scenario))
          },
          RouteButton(R.id.nav2_fragment_open_share_sheet, "Open Share Sheet") {
            activity.navigateTo(Nav2Destination.ShareSheet(productId, scenario))
          },
          RouteButton(R.id.nav2_fragment_go_to_checkout, "Go to Checkout") {
            activity.navigateTo(Nav2Destination.Checkout(productId))
          },
        ),
      trailingContent = { addProductDetailItemsToggle(productId) },
    )
  }

  private fun checkoutLayout(activity: Nav2Activity): View {
    val productId = requireArguments().getString(Nav2Args.PRODUCT_ID).orEmpty()
    val routeSpec = Nav2RouteSpecs.checkout
    return routeLayout(
      routeSpec,
      arguments = requireArguments(),
      buttons =
        listOf(
          RouteButton(R.id.nav2_fragment_complete_order, "Complete Order") {
            activity.navigateTo(Nav2Destination.Confirmation("order-$productId"))
          }
        ),
    )
  }

  private fun confirmationLayout(activity: Nav2Activity): View {
    val routeSpec = Nav2RouteSpecs.confirmation
    return routeLayout(
      routeSpec,
      arguments = requireArguments(),
      buttons =
        listOf(
          RouteButton(R.id.nav2_fragment_reset_backstack, "Reset Backstack") {
            activity.resetToHome()
          }
        ),
    )
  }

  private fun routeLayout(
    routeSpec: Nav2RouteSpec,
    arguments: Bundle? = null,
    buttons: List<RouteButton> = emptyList(),
    trailingContent: (LinearLayout.() -> Unit)? = null,
  ): View {
    val info = routeSpec.displayArguments(arguments)
    return LinearLayout(requireContext()).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(16.dp)
      addView(titleText(routeSpec.title))
      routeSpec.description?.let { addView(bodyText(it)) }

      if (info.isNotEmpty() || buttons.isNotEmpty()) {
        addView(
          LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp)
            setBackgroundColor(color(android.R.color.darker_gray))
            info.forEach { (label, value) -> addView(infoRow(label, value)) }
            buttons.forEach { button -> addView(routeButton(button)) }
          }
        )
      }
      trailingContent?.invoke(this)
    }
  }

  private fun LinearLayout.addProductDetailItemsToggle(productId: String) {
    var itemsCreated = false
    var itemsVisible = false
    val listContainer =
      LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 8.dp, 0, 0)
      }
    val scrollView =
      ScrollView(context).apply {
        visibility = View.GONE
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 280.dp)
        addView(listContainer, ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
      }

    addView(
      Button(context).apply {
        text = "Show Product Items"
        isAllCaps = false
        setOnClickListener {
          itemsVisible = !itemsVisible
          text = if (itemsVisible) "Hide Product Items" else "Show Product Items"
          if (itemsVisible && !itemsCreated) {
            populateProductDetailItems(listContainer, productId)
            itemsCreated = true
          }
          scrollView.visibility = if (itemsVisible) View.VISIBLE else View.GONE
        }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
      }
    )
    addView(scrollView)
  }

  private fun populateProductDetailItems(listContainer: LinearLayout, productId: String) {
    val ownerSpan = Sentry.getSpan()
    val compositionParent =
      ownerSpan?.startChild(
        OP_PARENT_COMPOSITION,
        "Fragment Product Detail Item List Composition",
      )
    try {
      recordManualUiSpan(compositionParent, OP_COMPOSE, "fragment_product_detail_items") {
        repeat(PRODUCT_DETAIL_ITEM_COUNT) { index ->
          val itemNumber = index + 1
          recordManualUiSpan(
            compositionParent,
            OP_COMPOSE,
            "fragment_product_detail_item_$itemNumber",
          ) {
            listContainer.addView(productDetailItemRow(productId, itemNumber))
          }
        }
      }
    } finally {
      compositionParent?.finish()
    }

    listContainer.doOnPreDraw {
      val renderParent =
        ownerSpan?.startChild(
          OP_PARENT_RENDER,
          "Fragment Product Detail Item List Render",
        )
      try {
        recordManualUiSpan(renderParent, OP_RENDER, "fragment_product_detail_items")
        repeat(PRODUCT_DETAIL_ITEM_COUNT) { index ->
          recordManualUiSpan(renderParent, OP_RENDER, "fragment_product_detail_item_${index + 1}")
        }
      } finally {
        renderParent?.finish()
      }
    }
  }

  private fun recordManualUiSpan(
    parentSpan: ISpan?,
    operation: String,
    description: String,
    block: () -> Unit = {},
  ) {
    val span = parentSpan?.startChild(operation, description)
    span?.setData("sample.nav2_fragment_manual_ui_span", true)
    try {
      block()
    } finally {
      span?.finish()
    }
  }

  private fun productDetailItemRow(productId: String, itemNumber: Int): View =
    LinearLayout(requireContext()).apply {
      orientation = LinearLayout.HORIZONTAL
      setPadding(12.dp)
      setBackgroundColor(color(android.R.color.white))
      addView(
        TextView(context).apply {
          text = "Product $productId item #$itemNumber"
          textSize = 14f
          setTypeface(null, Typeface.BOLD)
          layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
      )
      addView(
        TextView(context).apply {
          text = "SKU-$productId-$itemNumber"
          textSize = 14f
        }
      )
    }

  private fun titleText(textValue: String): TextView =
    TextView(requireContext()).apply {
      text = textValue
      textSize = 26f
      setTypeface(null, Typeface.BOLD)
      setTextColor(color(android.R.color.black))
      setPadding(0, 0, 0, 12.dp)
    }

  private fun bodyText(textValue: String): TextView =
    TextView(requireContext()).apply {
      text = textValue
      textSize = 15f
      setTextColor(color(android.R.color.black))
      setPadding(0, 0, 0, 16.dp)
    }

  private fun infoRow(label: String, value: String): View =
    TextView(requireContext()).apply {
      text = "$label: $value"
      textSize = 14f
      setPadding(8.dp)
    }

  private fun routeButton(button: RouteButton): Button =
    Button(requireContext()).apply {
      id = button.id
      text = button.label
      isAllCaps = false
      setOnClickListener { button.onClick() }
      layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    }

  private data class RouteButton(val id: Int, val label: String, val onClick: () -> Unit)

  private val Int.dp: Int
    get() = (this * resources.displayMetrics.density).toInt()

  private fun color(id: Int): Int = requireContext().getColor(id)

  private companion object {
    private const val PRODUCT_DETAIL_ITEM_COUNT = 20
    private const val OP_PARENT_COMPOSITION = "ui.compose.composition"
    private const val OP_COMPOSE = "ui.compose"
    private const val OP_PARENT_RENDER = "ui.compose.rendering"
    private const val OP_RENDER = "ui.render"
  }
}
