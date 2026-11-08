package io.sentry.samples.android.navigation.nav3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.sentry.compose.navigation3.BackStackEntryMapper
import io.sentry.compose.navigation3.SentryNavEffect

/**
 * Opens a [NavDisplay][androidx.navigation3.ui.NavDisplay] in a Compose dialog and in a bottom
 * sheet. Each has its own window, but its View tree carries the Activity's lifecycle owner, so the
 * SDK treats it as part of the Activity's nav root: only the Activity's `NavDisplay` should record,
 * and navigating inside the overlays should record nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OverlaysRoute() {
  var showDialog by remember { mutableStateOf(false) }
  var showBottomSheet by remember { mutableStateOf(false) }

  RouteScaffold(
    routeSpec = Nav3Route.Overlays.routeSpec(),
    testTagPrefix = nav3TestTag("route_overlays"),
    cardContent = {
      RouteButton(
        "Open dialog with NavDisplay",
        onClick = { showDialog = true },
        testTag = nav3TestTag("overlays_open_dialog"),
      )
      RouteButton(
        "Open bottom sheet with NavDisplay",
        onClick = { showBottomSheet = true },
        testTag = nav3TestTag("overlays_open_bottom_sheet"),
      )
    },
  )

  if (showDialog) {
    Dialog(onDismissRequest = { showDialog = false }) {
      Surface(shape = MaterialTheme.shapes.large) {
        val backStack = remember { mutableStateListOf<Nav3InnerRoute>(Nav3InnerRoute.Feed) }
        Nav3InnerNavDisplay(
          backStack = backStack,
          modifier = Modifier.fillMaxWidth().height(360.dp).padding(16.dp),
        )
      }
    }
  }

  if (showBottomSheet) {
    ModalBottomSheet(onDismissRequest = { showBottomSheet = false }) {
      val backStack = remember { mutableStateListOf<Nav3InnerRoute>(Nav3InnerRoute.Feed) }
      Nav3InnerNavDisplay(
        backStack = backStack,
        modifier = Modifier.fillMaxWidth().height(360.dp).padding(16.dp),
      )
    }
  }
}

@Composable
internal fun Nav2HostRoute(onOpen: () -> Unit) {
  RouteScaffold(
    routeSpec = Nav3Route.Nav2Host.routeSpec(),
    testTagPrefix = nav3TestTag("route_nav2_host"),
    cardContent = {
      RouteButton(
        "Open Nav3-in-Nav2 Activity",
        onClick = onOpen,
        testTag = nav3TestTag("nav2_host_open"),
      )
    },
  )
}

/**
 * Hosts a Nav3 [NavDisplay][androidx.navigation3.ui.NavDisplay] inside a Navigation 2 destination,
 * like an app migrating from Nav2 to Nav3 screen by screen.
 *
 * The destination's `LocalLifecycleOwner` is its Nav2 back stack entry, which drops to STARTED
 * during Nav2 transitions. The Nav3 `NavDisplay` is the outermost one in this Activity, so it
 * should record its navigation, and Nav2 transitions shouldn't remove its data or start extra
 * transactions. Auto-instrumentation uses the Activity as the nav root, so it handles this. A
 * manual `SentryNavEffect` here uses the Nav2 entry's lifecycle, which the SDK's supported setups
 * exclude.
 */
class Nav3InNav2Activity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val effectMode = intent.nav3SentryNavEffectMode()
    NavigationTransactionHistory.install()
    setContent { Nav3SampleTheme { Nav3InNav2(effectMode) } }
  }
}

@Composable
private fun Nav3InNav2(effectMode: Nav3SentryNavEffectMode) {
  val navController = rememberNavController()

  Column(
    modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("Nav3 in a Nav2 destination", style = MaterialTheme.typography.titleLarge)
    Text(
      Nav3AutoInstrumentationStatus.summary(effectMode),
      style = MaterialTheme.typography.bodySmall,
    )
    NavHost(navController = navController, startDestination = NAV2_HOME) {
      composable(NAV2_HOME) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Nav2 home destination", style = MaterialTheme.typography.titleMedium)
          RouteButton(
            "Open Nav2 destination with NavDisplay",
            onClick = { navController.navigate(NAV2_WITH_NAV3) },
            testTag = nav3TestTag("nav2_open_nav3_destination"),
          )
        }
      }
      composable(NAV2_WITH_NAV3) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          RouteButton(
            "Back to Nav2 home",
            onClick = { navController.popBackStack() },
            testTag = nav3TestTag("nav2_back_home"),
          )
          Nav3InNav2Destination(effectMode)
        }
      }
    }
  }
}

@Composable
private fun Nav3InNav2Destination(effectMode: Nav3SentryNavEffectMode) {
  val backStack = remember { mutableStateListOf<Nav3InnerRoute>(Nav3InnerRoute.Feed) }
  if (effectMode.callsSentryNavEffectBeforeNavDisplay) {
    SentryNavEffect(backStack, BackStackEntryMapper.serialName())
  }
  Nav3InnerNavDisplay(backStack = backStack, modifier = Modifier.fillMaxWidth().height(360.dp))
  if (effectMode.callsSentryNavEffectAfterNavDisplay) {
    SentryNavEffect(backStack, BackStackEntryMapper.serialName())
  }
}

private const val NAV2_HOME = "nav2_home"
private const val NAV2_WITH_NAV3 = "nav2_with_nav3"
