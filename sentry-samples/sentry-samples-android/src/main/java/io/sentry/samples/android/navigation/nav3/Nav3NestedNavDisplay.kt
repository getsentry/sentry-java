package io.sentry.samples.android.navigation.nav3

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Keys of the back stacks shown by [Nav3InnerNavDisplay]. */
@Serializable
internal sealed interface Nav3InnerRoute {
  @Serializable @SerialName("InnerFeed") data object Feed : Nav3InnerRoute

  @Serializable @SerialName("InnerItem") data class Item(val id: Int) : Nav3InnerRoute
}

/**
 * Hosts a second [NavDisplay] inside the Activity's, like an app whose screen has its own nested
 * navigation (e.g., a tab with its own back stack).
 *
 * Only the outer `NavDisplay` should record. With auto-instrumentation, both are instrumented; the
 * inner one shares the Activity's nav root, so the SDK keeps it dormant. Navigating inside it
 * should record nothing, and hiding and showing it shouldn't affect what the outer one recorded.
 */
@Composable
internal fun NestedNavDisplayRoute() {
  val innerBackStack = remember { mutableStateListOf<Nav3InnerRoute>(Nav3InnerRoute.Feed) }
  var showInner by remember { mutableStateOf(true) }

  RouteScaffold(
    routeSpec = Nav3Route.Nested.routeSpec(),
    testTagPrefix = nav3TestTag("route_nested"),
    cardContent = {
      RouteInfo("Inner back stack", innerBackStack.joinToString(" -> ") { "/$it" })
      RouteButton(
        if (showInner) "Hide inner NavDisplay" else "Show inner NavDisplay",
        onClick = { showInner = !showInner },
        testTag = nav3TestTag("nested_toggle_inner"),
      )
    },
    content = {
      if (showInner) {
        Nav3InnerNavDisplay(
          backStack = innerBackStack,
          modifier =
            Modifier.fillMaxWidth()
              .height(320.dp)
              .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
              .padding(12.dp),
        )
      }
    },
  )
}

/**
 * A [NavDisplay] over [backStack] with a small feed/item flow, for the scenarios that put a
 * `NavDisplay` somewhere other than the Activity's top level.
 */
@Composable
internal fun Nav3InnerNavDisplay(
  backStack: MutableList<Nav3InnerRoute>,
  modifier: Modifier = Modifier,
) {
  NavDisplay(
    backStack = backStack,
    modifier = modifier,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Nav3InnerRoute.Feed> {
          InnerScreen("Inner feed") {
            RouteButton(
              "Open inner item 1",
              onClick = { backStack.add(Nav3InnerRoute.Item(1)) },
              testTag = nav3TestTag("inner_open_item_1"),
            )
            RouteButton(
              "Open inner item 2",
              onClick = { backStack.add(Nav3InnerRoute.Item(2)) },
              testTag = nav3TestTag("inner_open_item_2"),
            )
          }
        }
        entry<Nav3InnerRoute.Item> { key ->
          InnerScreen("Inner item ${key.id}") {
            RouteButton(
              "Back to inner feed",
              onClick = { backStack.removeLastOrNull() },
              testTag = nav3TestTag("inner_back"),
            )
          }
        }
      },
  )
}

@Composable
private fun InnerScreen(title: String, content: @Composable () -> Unit) {
  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    content()
  }
}
