package io.sentry.samples.android.navigation

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.setPadding
import io.sentry.samples.android.R

/**
 * A top bar consisting of nav info above tabs for selecting among a variety of [Nav2Scenario]s.
 *
 * Displays info about the user's current route and back stack state, and lets the user
 * enable/disable [RouteWorkOption]s.
 */
internal class Nav2TopBar(
  private val context: Context,
  private val onTransactionHistoryClick: () -> Unit,
  private val onRouteWorkSettingsClick: () -> Unit,
  private val onScenarioClick: (Nav2Scenario) -> Unit,
) {

  private val topBarStates =
    mutableMapOf(
      Nav2Scenario.LANDING to
        Nav2TopBarState(
          currentRoute = "/${Nav2RouteNames.LANDING}",
          backStack = "/${Nav2RouteNames.LANDING}",
        ),
      Nav2Scenario.COMPOSE to
        Nav2TopBarState(
          currentRoute = "/${Nav2RouteNames.HOME}",
          backStack = "/${Nav2RouteNames.HOME}",
        ),
      Nav2Scenario.FRAGMENTS to
        Nav2TopBarState(
          currentRoute = "/${Nav2RouteNames.HOME}",
          backStack = "/${Nav2RouteNames.HOME}",
        ),
      Nav2Scenario.DEEP_LINK to
        Nav2TopBarState(
          currentRoute = "/${Nav2RouteNames.DEEP_LINK}",
          backStack = "/${Nav2RouteNames.DEEP_LINK}",
        ),
      Nav2Scenario.PERFORMANCE to
        Nav2TopBarState(
          currentRoute = "/${Nav2RouteNames.HOME}",
          backStack = "/${Nav2RouteNames.HOME}",
        ),
    )
  private val tabViews = mutableMapOf<Nav2Scenario, Nav2TabView>()
  private val currentRouteText = bodyText()
  private val navControllerBackStackText = bodyText()
  private val currentRouteScroll = horizontalTextContainer(currentRouteText)
  private val navControllerBackStackScroll = horizontalTextContainer(navControllerBackStackText)
  private var selectedScenario = Nav2Scenario.COMPOSE

  val view: View =
    LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      addView(createHeader())
      addView(createTabs())
    }

  fun update(scenario: Nav2Scenario, currentRoute: String, backStack: String) {
    topBarStates[scenario] = Nav2TopBarState(currentRoute = currentRoute, backStack = backStack)
    if (scenario == selectedScenario) {
      render(scenario)
    }
  }

  fun render(scenario: Nav2Scenario) {
    val topBarState = topBarStates[scenario] ?: return
    currentRouteText.text = "Current route: ${topBarState.currentRoute}"
    navControllerBackStackText.text = "NavController back stack: ${topBarState.backStack}"
    currentRouteScroll.post { currentRouteScroll.scrollTo(0, 0) }
    navControllerBackStackScroll.post { navControllerBackStackScroll.scrollTo(0, 0) }
  }

  fun select(scenario: Nav2Scenario) {
    selectedScenario = scenario
    render(scenario)
    tabViews.forEach { (tabScenario, tabView) ->
      val selected = tabScenario == scenario
      tabView.label.setTextColor(
        color(if (selected) R.color.colorPrimary else android.R.color.black)
      )
      tabView.label.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
      tabView.indicator.setBackgroundColor(
        color(if (selected) R.color.colorPrimary else android.R.color.transparent)
      )
    }
  }

  private fun createHeader(): View {
    return LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(24.dp, 18.dp, 24.dp, 12.dp)
      addView(
        LinearLayout(context).apply {
          orientation = LinearLayout.HORIZONTAL
          gravity = Gravity.CENTER_VERTICAL
          addView(
            TextView(context).apply {
              text = "Navigation 2"
              textSize = 20f
              setTextColor(color(android.R.color.black))
              layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            }
          )
          addView(transactionHistoryIcon())
          addView(routeWorkSettingsIcon())
        }
      )
      addView(View(context), LinearLayout.LayoutParams(MATCH_PARENT, 12.dp))
      addView(currentRouteScroll)
      addView(navControllerBackStackScroll)
    }
  }

  private fun createTabs(): View {
    val tabRow =
      LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(24.dp, 0, 24.dp, 0)
      }

    Nav2Scenario.entries
      .filter { it.showTab }
      .forEach { scenario ->
        val tabContainer =
          LinearLayout(context).apply {
            id = scenario.tabViewId
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumWidth = 120.dp
            setOnClickListener { onScenarioClick(scenario) }
          }
        val textView =
          TextView(context).apply {
            text = scenario.label
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(18.dp, 14.dp, 18.dp, 10.dp)
          }
        val indicator =
          View(context).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 3.dp)
            setBackgroundColor(color(android.R.color.transparent))
          }
        tabContainer.addView(textView)
        tabContainer.addView(indicator)
        tabViews[scenario] = Nav2TabView(textView, indicator)
        tabRow.addView(tabContainer)
      }

    return HorizontalScrollView(context).apply {
      isHorizontalScrollBarEnabled = false
      addView(tabRow)
    }
  }

  private val Nav2Scenario.tabViewId: Int
    get() =
      when (this) {
        Nav2Scenario.LANDING -> R.id.nav2_landing
        Nav2Scenario.COMPOSE -> R.id.nav2_tab_compose
        Nav2Scenario.FRAGMENTS -> R.id.nav2_tab_fragments
        Nav2Scenario.DEEP_LINK -> R.id.nav2_tab_deep_link
        Nav2Scenario.PERFORMANCE -> R.id.nav2_tab_performance
      }

  private fun bodyText(): TextView =
    TextView(context).apply {
      textSize = 12f
      setTextColor(color(android.R.color.black))
      maxLines = 1
      setHorizontallyScrolling(true)
    }

  private fun horizontalTextContainer(textView: TextView): HorizontalScrollView =
    HorizontalScrollView(context).apply {
      isHorizontalScrollBarEnabled = false
      layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
      addView(textView, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
    }

  private fun routeWorkSettingsIcon(): View =
    composeIconButton(
      id = R.id.nav2_route_work_settings,
      imageVector = Icons.Filled.Settings,
      contentDescription = "Route work settings",
      onClick = onRouteWorkSettingsClick,
    )

  private fun transactionHistoryIcon(): View =
    composeIconButton(
      id = R.id.nav2_recent_transactions,
      imageVector = Icons.Filled.AccountTree,
      contentDescription = "Recent transactions",
      onClick = onTransactionHistoryClick,
    )

  private fun composeIconButton(
    id: Int,
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
  ): View =
    ComposeView(context).apply {
      this.id = id
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        MaterialTheme {
          IconButton(onClick = onClick) {
            Icon(
              imageVector = imageVector,
              contentDescription = contentDescription,
              tint = Color.Black,
            )
          }
        }
      }
    }

  private val Int.dp: Int
    get() = (this * context.resources.displayMetrics.density).toInt()

  private fun color(id: Int): Int = context.getColor(id)
}

private data class Nav2TopBarState(val currentRoute: String, val backStack: String)

private data class Nav2TabView(val label: TextView, val indicator: View)
