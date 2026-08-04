package io.sentry.samples.android.navigation.nav2

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.OnBackInvokedDefaultInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import io.sentry.ITransaction
import io.sentry.Sentry
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.compose.SentryTraced
import io.sentry.compose.navigation3.SentryNavEffect
import io.sentry.compose.withSentryObservableEffect
import io.sentry.protocol.TransactionNameSource
import io.sentry.samples.android.GithubAPI
import io.sentry.samples.android.getActivity
import io.sentry.samples.android.navigation.common.RouteWorkOption
import io.sentry.samples.android.navigation.common.SENTRY_FLUSH_TIMEOUT_MILLIS
import io.sentry.samples.android.navigation.common.emitSampleNavigationSpan
import io.sentry.samples.android.navigation.common.tagCurrentNavigationSampleScenario
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

@Composable
internal fun Nav2InteropLab(
  routeWorkOptions: Set<RouteWorkOption>,
  onRouteChanged: (currentRoute: String, backStack: String) -> Unit,
) {
  tagCurrentNavigationSampleScenario("Nav3 Interop")

  var selectedMode by rememberSaveable { mutableStateOf<InteropMode?>(null) }
  var showHud by rememberSaveable { mutableStateOf(false) }
  var summary by remember { mutableStateOf(InteropSummary.empty()) }
  val sentrySnapshot = rememberSentryScopeSnapshot(summary)

  SideEffect {
    onRouteChanged(summary.currentRoute, summary.backStackSummary)
  }

  ProvideInteropNavigationEventDispatcher {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(
              color = MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(16.dp),
            )
            .padding(12.dp)
      ) {
        when (selectedMode) {
          null ->
            Nav3InteropLandingScreen(
              onSelectMode = { mode ->
                showHud = false
                selectedMode = mode
              }
            )

          InteropMode.NAV2_PARENT_NAV3_CHILD ->
            Nav2ParentNav3ChildMode(
              onSummaryChanged = { summary = it },
              routeWorkOptions = routeWorkOptions,
              showHud = showHud,
              onToggleHud = { showHud = !showHud },
              onBackToModePicker = {
                showHud = false
                selectedMode = null
                summary = InteropSummary.empty()
              },
              sentrySnapshot = sentrySnapshot,
            )

          InteropMode.NAV3_PARENT_NAV2_CHILD ->
            Nav3ParentNav2ChildMode(
              onSummaryChanged = { summary = it },
              routeWorkOptions = routeWorkOptions,
              showHud = showHud,
              onToggleHud = { showHud = !showHud },
              onBackToModePicker = {
                showHud = false
                selectedMode = null
                summary = InteropSummary.empty()
              },
              sentrySnapshot = sentrySnapshot,
            )

          InteropMode.SIBLING_ROOTS ->
            SiblingRootsMode(
              onSummaryChanged = { summary = it },
              routeWorkOptions = routeWorkOptions,
              showHud = showHud,
              onToggleHud = { showHud = !showHud },
              onBackToModePicker = {
                showHud = false
                selectedMode = null
                summary = InteropSummary.empty()
              },
              sentrySnapshot = sentrySnapshot,
            )
        }
      }
    }
  }
}

@Composable
private fun Nav2ParentNav3ChildMode(
  onSummaryChanged: (InteropSummary) -> Unit,
  routeWorkOptions: Set<RouteWorkOption>,
  showHud: Boolean,
  onToggleHud: () -> Unit,
  onBackToModePicker: () -> Unit,
  sentrySnapshot: SentryScopeSnapshot,
) {
  val parentNavController = rememberNavController().withSentryObservableEffect()
  val childBackStack: SnapshotStateList<Nav3ChildRoute> = remember {
    mutableStateListOf<Nav3ChildRoute>(Nav3ChildRoute.ProductDetail)
  }
  // The Nav2 parent's back stack is the NavController itself. Derive the current route and the
  // stack
  // preview from it instead of shadowing it with a parallel list that can drift out of sync.
  val parentRouteNames = rememberNav2RouteNames(parentNavController)
  val currentParent =
    Nav2ParentRoute.fromNavRoute(parentRouteNames.lastOrNull()) ?: Nav2ParentRoute.Home
  val currentChild = childBackStack.lastOrNull() ?: Nav3ChildRoute.ProductDetail
  val visibleHost = if (currentParent == Nav2ParentRoute.Nav3Host) HostKind.NAV3 else HostKind.NAV2

  fun pushParent(route: Nav2ParentRoute) {
    if (parentNavController.currentDestination?.route == route.navRoute) {
      return
    }
    parentNavController.navigate(route.navRoute)
  }

  fun popParent() {
    parentNavController.popBackStack()
  }

  fun resetChildFlow() {
    childBackStack.resetNav3ChildFlowTo(Nav3ChildRoute.ProductDetail)
  }

  val summary =
    InteropSummary(
      mode = InteropMode.NAV2_PARENT_NAV3_CHILD,
      visibleHost = visibleHost,
      currentRoute =
        "/${if (visibleHost == HostKind.NAV2) currentParent.routeName else currentChild.routeName}",
      nav2Stack = parentRouteNames.toPreviewString { it },
      nav3Stack = childBackStack.toPreviewString { route -> route.routeName },
      notes =
        "Navigate through Nav2 first, then hand the same product flow off to a nested Nav3 child via Continue in Nav3.",
    )
  SideEffect { onSummaryChanged(summary) }
  BackHandler {
    when (currentParent) {
      Nav2ParentRoute.Home -> onBackToModePicker()
      Nav2ParentRoute.Nav3Host -> {
        if (childBackStack.size > 1) {
          childBackStack.removeLastOrNull()
        } else {
          resetChildFlow()
          popParent()
        }
      }
      else -> popParent()
    }
  }

  InteropModeScaffold(
    showHud = showHud,
    onToggleHud = onToggleHud,
    onBackToModePicker = onBackToModePicker,
    hudContent = { InteropSummaryCard(summary = summary, sentrySnapshot = sentrySnapshot) },
  ) {
    NavHost(
      navController = parentNavController,
      startDestination = Nav2ParentRoute.Home.navRoute,
      modifier = Modifier.fillMaxSize(),
    ) {
      composable(Nav2ParentRoute.Home.navRoute) {
        HostRouteScaffold(
          host = HostKind.NAV2,
          roleLabel = "Parent host",
          title = Nav2RouteSpecs.home.title,
          routeName = Nav2ParentRoute.Home.routeName,
          routeWorkOptions = routeWorkOptions,
          description =
            "Start in the familiar Nav2 product flow, then continue into a nested Nav3 child route later in the journey.",
          footerContent = {
            RouteButton(label = "Browse Products") { pushParent(Nav2ParentRoute.ProductList) }
            EmitSpanButton(routeName = Nav2ParentRoute.Home.routeName)
          },
        )
      }

      composable(Nav2ParentRoute.ProductList.navRoute) {
        HostRouteScaffold(
          host = HostKind.NAV2,
          roleLabel = "Parent host",
          title = Nav2RouteSpecs.productList.title,
          routeName = Nav2ParentRoute.ProductList.routeName,
          routeWorkOptions = routeWorkOptions,
          description =
            "Stay in Nav2 for now, then open the same product detail screen you use elsewhere in the sample.",
          footerContent = {
            RouteButton(label = "Open Product 42") { pushParent(Nav2ParentRoute.ProductDetail) }
            EmitSpanButton(routeName = Nav2ParentRoute.ProductList.routeName)
          },
        )
      }

      composable(Nav2ParentRoute.ProductDetail.navRoute) {
        HostRouteScaffold(
          host = HostKind.NAV2,
          roleLabel = "Parent host",
          title = Nav2RouteSpecs.productDetail.title,
          routeName = Nav2ParentRoute.ProductDetail.routeName,
          routeWorkOptions = routeWorkOptions,
          description =
            "This is the handoff point. Continue the same product journey in Nav3 and compare the transaction history before and after the switch.",
          cardContent = {
            InfoRow(label = "productId", value = "42")
            InfoRow(label = "source", value = "nav2-parent")
          },
          footerContent = {
            RouteButton(label = "Continue in Nav3") {
              resetChildFlow()
              pushParent(Nav2ParentRoute.Nav3Host)
            }
            EmitSpanButton(routeName = Nav2ParentRoute.ProductDetail.routeName)
          },
        )
      }

      composable(Nav2ParentRoute.Nav3Host.navRoute) {
        ClearBoundNavigationTransactionEffect(routeName = Nav2ParentRoute.Nav3Host.routeName)
        NestedHostCard(
          host = HostKind.NAV3,
          roleLabel = "Child host",
          title = "Nested Nav3 flow",
          description =
            "The child starts on product detail so the handoff feels like a continuation, not a reset. Use Recent transactions to check whether Nav3 now owns the visible route.",
        ) {
          Nav3ChildFlow(
            backStack = childBackStack,
            routeWorkOptions = routeWorkOptions,
            hostRole = "Child host",
            onReturnToParent = {
              resetChildFlow()
              popParent()
            },
          )
        }
      }
    }
  }
}

@Composable
private fun Nav3ParentNav2ChildMode(
  onSummaryChanged: (InteropSummary) -> Unit,
  routeWorkOptions: Set<RouteWorkOption>,
  showHud: Boolean,
  onToggleHud: () -> Unit,
  onBackToModePicker: () -> Unit,
  sentrySnapshot: SentryScopeSnapshot,
) {
  val parentBackStack: SnapshotStateList<Nav3ParentRoute> = remember {
    mutableStateListOf<Nav3ParentRoute>(Nav3ParentRoute.Home)
  }
  val childNavController = rememberNavController()
  val childTransactions = remember { InteropNav2TransactionController() }
  // The Nav2 child's back stack is the childNavController itself; derive its state from the
  // controller instead of shadowing it with a parallel list that can drift out of sync.
  val childRouteNames = rememberNav2RouteNames(childNavController)
  val currentParent = parentBackStack.lastOrNull() ?: Nav3ParentRoute.Home
  val currentChild =
    Nav2ChildRoute.fromNavRoute(childRouteNames.lastOrNull()) ?: Nav2ChildRoute.ProductDetail
  val visibleHost = if (currentParent == Nav3ParentRoute.Nav2Host) HostKind.NAV2 else HostKind.NAV3

  SentryNavEffect(backStack = parentBackStack, nameExtractor = { route -> route.routeName })
  DisposableEffect(childTransactions) { onDispose { childTransactions.cleanup() } }

  fun pushParent(route: Nav3ParentRoute) {
    parentBackStack.add(route)
  }

  fun popParent() {
    if (parentBackStack.size > 1) {
      parentBackStack.removeLastOrNull()
    }
  }

  fun resetChildFlow() {
    childTransactions.cleanup()
    while (childNavController.previousBackStackEntry != null) {
      childNavController.popBackStack()
    }
  }

  fun popChild() {
    // navRoute == routeName for Nav2 routes, so the revealed destination route doubles as the
    // transaction name.
    val revealedRoute = childNavController.previousBackStackEntry?.destination?.route ?: return
    childNavController.popBackStack()
    childTransactions.activate(revealedRoute)
  }

  val summary =
    InteropSummary(
      mode = InteropMode.NAV3_PARENT_NAV2_CHILD,
      visibleHost = visibleHost,
      currentRoute =
        "/${if (visibleHost == HostKind.NAV3) currentParent.routeName else currentChild.routeName}",
      nav2Stack = childRouteNames.toPreviewString { it },
      nav3Stack = parentBackStack.toPreviewString { route -> route.routeName },
      notes =
        "Navigate through Nav3 first, then hand the same product flow off to a nested Nav2 child via Continue in Nav2.",
    )
  SideEffect { onSummaryChanged(summary) }
  BackHandler {
    when (currentParent) {
      Nav3ParentRoute.Home -> onBackToModePicker()
      // The mode owns back for the nested Nav2 child: unwind the child NavController while it has
      // depth, then return to the Nav3 parent at the child root. The Nav3 parent's own back
      // handling
      // is disabled (see below) so it can't jump-pop the whole child host out from under us.
      Nav3ParentRoute.Nav2Host -> {
        if (childNavController.previousBackStackEntry != null) {
          popChild()
        } else {
          resetChildFlow()
          popParent()
        }
      }
      else -> popParent()
    }
  }

  InteropModeScaffold(
    showHud = showHud,
    onToggleHud = onToggleHud,
    onBackToModePicker = onBackToModePicker,
    hudContent = { InteropSummaryCard(summary = summary, sentrySnapshot = sentrySnapshot) },
  ) {
    // Disable the Nav3 parent's own OnBackInvokedDispatcher handling. Parent-route back is owned by
    // the mode BackHandler above, and the nested Nav2 child owns its own back. Without this, the
    // parent NavDisplay wins back while the child is visible and jump-pops the whole child host
    // instead of unwinding the child stack, so Sentry misses the child's revealed routes.
    Nav3NavigationScope(enabled = false) {
      NavDisplay(
        backStack = parentBackStack,
        modifier = Modifier.fillMaxSize(),
        onBack = { popParent() },
        entryProvider =
          entryProvider {
            entry<Nav3ParentRoute.Home> {
              HostRouteScaffold(
                host = HostKind.NAV3,
                roleLabel = "Parent host",
                title = Nav2RouteSpecs.home.title,
                routeName = Nav3ParentRoute.Home.routeName,
                routeWorkOptions = routeWorkOptions,
                description =
                  "Start in the familiar Nav3 flow, then continue the same product journey in a nested Nav2 child later.",
                footerContent = {
                  RouteButton(label = "Browse Products") { pushParent(Nav3ParentRoute.ProductList) }
                  EmitSpanButton(routeName = Nav3ParentRoute.Home.routeName)
                },
              )
            }

            entry<Nav3ParentRoute.ProductList> {
              HostRouteScaffold(
                host = HostKind.NAV3,
                roleLabel = "Parent host",
                title = Nav2RouteSpecs.productList.title,
                routeName = Nav3ParentRoute.ProductList.routeName,
                routeWorkOptions = routeWorkOptions,
                description =
                  "Stay in Nav3 for now, then open the same product detail screen you use elsewhere in the sample.",
                footerContent = {
                  RouteButton(label = "Open Product 42") {
                    pushParent(Nav3ParentRoute.ProductDetail)
                  }
                  EmitSpanButton(routeName = Nav3ParentRoute.ProductList.routeName)
                },
              )
            }

            entry<Nav3ParentRoute.ProductDetail> {
              HostRouteScaffold(
                host = HostKind.NAV3,
                roleLabel = "Parent host",
                title = Nav2RouteSpecs.productDetail.title,
                routeName = Nav3ParentRoute.ProductDetail.routeName,
                routeWorkOptions = routeWorkOptions,
                description =
                  "This is the reverse handoff point. Continue in Nav2 and compare whether the nested listener takes over the current route cleanly.",
                cardContent = {
                  InfoRow(label = "productId", value = "42")
                  InfoRow(label = "source", value = "nav3-parent")
                },
                footerContent = {
                  RouteButton(label = "Continue in Nav2") {
                    resetChildFlow()
                    childTransactions.activate(Nav2ChildRoute.ProductDetail.routeName)
                    pushParent(Nav3ParentRoute.Nav2Host)
                  }
                  EmitSpanButton(routeName = Nav3ParentRoute.ProductDetail.routeName)
                },
              )
            }

            entry<Nav3ParentRoute.Nav2Host> {
              NestedHostCard(
                host = HostKind.NAV2,
                roleLabel = "Child host",
                title = "Nested Nav2 flow",
                description =
                  "The child starts on product detail so the handoff feels continuous. Inspect Recent transactions to see whether Nav2 now owns the visible route.",
              ) {
                Nav2ChildFlow(
                  navController = childNavController,
                  routeWorkOptions = routeWorkOptions,
                  hostRole = "Child host",
                  transactionController = childTransactions,
                )
              }
            }
          },
      )
    }
  }
}

@Composable
private fun SiblingRootsMode(
  onSummaryChanged: (InteropSummary) -> Unit,
  routeWorkOptions: Set<RouteWorkOption>,
  showHud: Boolean,
  onToggleHud: () -> Unit,
  onBackToModePicker: () -> Unit,
  sentrySnapshot: SentryScopeSnapshot,
) {
  val nav2Controller = rememberNavController().withSentryObservableEffect()
  val nav3BackStack: SnapshotStateList<SiblingNav3Route> = remember {
    mutableStateListOf<SiblingNav3Route>(SiblingNav3Route.Home)
  }
  var visibleHost by rememberSaveable { mutableStateOf(HostKind.NAV2) }
  // The Nav2 sibling's back stack is the nav2Controller itself; derive from it instead of shadowing
  // it with a parallel list that can drift out of sync.
  val nav2RouteNames = rememberNav2RouteNames(nav2Controller)
  val currentNav2 =
    SiblingNav2Route.fromNavRoute(nav2RouteNames.lastOrNull()) ?: SiblingNav2Route.Home
  val currentNav3 = nav3BackStack.lastOrNull() ?: SiblingNav3Route.Home

  SentryNavEffect(backStack = nav3BackStack, nameExtractor = { route -> route.routeName })

  fun pushNav2(route: SiblingNav2Route) {
    if (nav2Controller.currentDestination?.route == route.navRoute) {
      return
    }
    nav2Controller.navigate(route.navRoute)
  }

  fun popNav2() {
    nav2Controller.popBackStack()
  }

  fun pushNav3(route: SiblingNav3Route) {
    nav3BackStack.add(route)
  }

  fun popNav3() {
    if (nav3BackStack.size > 1) {
      nav3BackStack.removeLastOrNull()
    }
  }

  fun resetScenario() {
    visibleHost = HostKind.NAV2
    while (nav2Controller.previousBackStackEntry != null) {
      nav2Controller.popBackStack()
    }
    nav3BackStack.resetSiblingNav3FlowTo(SiblingNav3Route.Home)
  }

  fun mutateHiddenNav2() {
    if (visibleHost == HostKind.NAV2) {
      return
    }
    when (currentNav2) {
      SiblingNav2Route.Home -> pushNav2(SiblingNav2Route.ProductList)
      SiblingNav2Route.ProductList -> pushNav2(SiblingNav2Route.ProductDetail)
      SiblingNav2Route.ProductDetail -> pushNav2(SiblingNav2Route.Checkout)
      SiblingNav2Route.Checkout -> pushNav2(SiblingNav2Route.Confirmation)
      SiblingNav2Route.Confirmation -> {
        while (nav2Controller.previousBackStackEntry != null) {
          nav2Controller.popBackStack()
        }
      }
    }
  }

  fun handleBack() {
    when (visibleHost) {
      HostKind.NAV2 -> {
        if (nav2Controller.previousBackStackEntry != null) {
          popNav2()
        } else {
          onBackToModePicker()
        }
      }

      HostKind.NAV3 -> {
        if (nav3BackStack.size > 1) {
          popNav3()
        } else {
          onBackToModePicker()
        }
      }
    }
  }

  fun mutateHiddenNav3() {
    if (visibleHost == HostKind.NAV3) {
      return
    }
    when (currentNav3) {
      SiblingNav3Route.Home -> pushNav3(SiblingNav3Route.ProductList)
      SiblingNav3Route.ProductList -> pushNav3(SiblingNav3Route.ProductDetail)
      SiblingNav3Route.ProductDetail -> pushNav3(SiblingNav3Route.Checkout)
      SiblingNav3Route.Checkout -> pushNav3(SiblingNav3Route.Confirmation)
      SiblingNav3Route.Confirmation -> nav3BackStack.resetSiblingNav3FlowTo(SiblingNav3Route.Home)
    }
  }

  val summary =
    InteropSummary(
      mode = InteropMode.SIBLING_ROOTS,
      visibleHost = visibleHost,
      currentRoute =
        "/${if (visibleHost == HostKind.NAV2) currentNav2.routeName else currentNav3.routeName}",
      nav2Stack = nav2RouteNames.toPreviewString { it },
      nav3Stack = nav3BackStack.toPreviewString { route -> route.routeName },
      notes =
        "Each host keeps its own product flow mounted. Use the hidden-host mutation controls to probe stale ownership and cleanup behavior.",
    )
  SideEffect { onSummaryChanged(summary) }
  BackHandler(onBack = ::handleBack)

  InteropModeScaffold(
    showHud = showHud,
    onToggleHud = onToggleHud,
    onBackToModePicker = onBackToModePicker,
    hudContent = { InteropSummaryCard(summary = summary, sentrySnapshot = sentrySnapshot) },
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      if (visibleHost == HostKind.NAV2) {
        Box(modifier = Modifier.fillMaxSize().alpha(0f)) {
          SiblingNav3Flow(
            backStack = nav3BackStack,
            routeWorkOptions = routeWorkOptions,
            backHandlingEnabled = false,
            onShowNav2 = { visibleHost = HostKind.NAV2 },
            onMutateHiddenNav2 = { mutateHiddenNav2() },
          )
        }
        Box(modifier = Modifier.fillMaxSize()) {
          SiblingNav2Flow(
            navController = nav2Controller,
            routeWorkOptions = routeWorkOptions,
            onShowNav3 = { visibleHost = HostKind.NAV3 },
            onMutateHiddenNav3 = { mutateHiddenNav3() },
          )
        }
      } else {
        Box(modifier = Modifier.fillMaxSize().alpha(0f)) {
          SiblingNav2Flow(
            navController = nav2Controller,
            routeWorkOptions = routeWorkOptions,
            onShowNav3 = { visibleHost = HostKind.NAV3 },
            onMutateHiddenNav3 = { mutateHiddenNav3() },
          )
        }
        Box(modifier = Modifier.fillMaxSize()) {
          SiblingNav3Flow(
            backStack = nav3BackStack,
            routeWorkOptions = routeWorkOptions,
            backHandlingEnabled = true,
            onShowNav2 = { visibleHost = HostKind.NAV2 },
            onMutateHiddenNav2 = { mutateHiddenNav2() },
          )
        }
      }
    }
  }
}

@Composable
private fun Nav3ChildFlow(
  backStack: SnapshotStateList<Nav3ChildRoute>,
  routeWorkOptions: Set<RouteWorkOption>,
  hostRole: String,
  onReturnToParent: () -> Unit,
) {
  SentryNavEffect(backStack = backStack, nameExtractor = { route -> route.routeName })

  // Own back with a single Compose BackHandler, composed inside the nested child so it outranks
  // the Nav2 parent NavHost on the OnBackPressedDispatcher. The Nav3 scope's own
  // OnBackInvokedDispatcher handling is disabled so the two back systems can't compete. Without
  // this, back at the child root is swallowed and Sentry records a duplicate current route or a
  // stray parent route instead of the reverse handoff to Nav2.
  BackHandler {
    if (backStack.size > 1) {
      backStack.removeLastOrNull()
    } else {
      onReturnToParent()
    }
  }

  Nav3NavigationScope(enabled = false) {
    NavDisplay(
      backStack = backStack,
      modifier = Modifier.fillMaxSize(),
      onBack = { onReturnToParent() },
      entryProvider =
        entryProvider {
          entry<Nav3ChildRoute.ProductDetail> {
            HostRouteScaffold(
              host = HostKind.NAV3,
              roleLabel = hostRole,
              title = Nav2RouteSpecs.productDetail.title,
              routeName = Nav3ChildRoute.ProductDetail.routeName,
              routeWorkOptions = routeWorkOptions,
              description =
                "Nav3 picks up the same product at detail view. Continue to checkout, then back out and inspect the transaction history.",
              cardContent = {
                InfoRow(label = "productId", value = "42")
                InfoRow(label = "source", value = "nav3-child")
              },
              footerContent = {
                RouteButton(label = "Go to Checkout") { backStack.add(Nav3ChildRoute.Checkout) }
                EmitSpanButton(routeName = Nav3ChildRoute.ProductDetail.routeName)
              },
            )
          }

          entry<Nav3ChildRoute.Checkout> {
            HostRouteScaffold(
              host = HostKind.NAV3,
              roleLabel = hostRole,
              title = Nav2RouteSpecs.checkout.title,
              routeName = Nav3ChildRoute.Checkout.routeName,
              routeWorkOptions = routeWorkOptions,
              description =
                "The child Nav3 host should now own the visible checkout route. Complete the order or back up within Nav3.",
              cardContent = { InfoRow(label = "productId", value = "42") },
              footerContent = {
                RouteButton(label = "Complete Order") { backStack.add(Nav3ChildRoute.Confirmation) }
                EmitSpanButton(routeName = Nav3ChildRoute.Checkout.routeName)
              },
            )
          }

          entry<Nav3ChildRoute.Confirmation> {
            HostRouteScaffold(
              host = HostKind.NAV3,
              roleLabel = hostRole,
              title = Nav2RouteSpecs.confirmation.title,
              routeName = Nav3ChildRoute.Confirmation.routeName,
              routeWorkOptions = routeWorkOptions,
              description =
                "Finish the child flow here, then return to the Nav2 parent and confirm ownership shifts back cleanly.",
              cardContent = { InfoRow(label = "orderId", value = "order-42") },
              footerContent = {
                EmitSpanButton(routeName = Nav3ChildRoute.Confirmation.routeName)
              },
            )
          }
        },
    )
  }
}

@Composable
private fun Nav2ChildFlow(
  navController: androidx.navigation.NavHostController,
  routeWorkOptions: Set<RouteWorkOption>,
  hostRole: String,
  transactionController: InteropNav2TransactionController? = null,
) {
  // Forward navigation only. Back for this nested Nav2 child is owned by the parent mode's
  // BackHandler, which unwinds this NavController and reactivates transactions as a single
  // authority.
  fun push(route: Nav2ChildRoute) {
    if (navController.currentDestination?.route == route.navRoute) {
      return
    }
    transactionController?.activate(route.routeName)
    navController.navigate(route.navRoute)
  }

  NavHost(
    navController = navController,
    startDestination = Nav2ChildRoute.ProductDetail.navRoute,
    modifier = Modifier.fillMaxSize(),
  ) {
    composable(Nav2ChildRoute.ProductDetail.navRoute) {
      HostRouteScaffold(
        host = HostKind.NAV2,
        roleLabel = hostRole,
        title = Nav2RouteSpecs.productDetail.title,
        routeName = Nav2ChildRoute.ProductDetail.routeName,
        routeWorkOptions = routeWorkOptions,
        description =
          "Nav2 picks up the same product at detail view. Continue to checkout, then back out and inspect the transaction history.",
        cardContent = {
          InfoRow(label = "productId", value = "42")
          InfoRow(label = "source", value = "nav2-child")
        },
        footerContent = {
          RouteButton(label = "Go to Checkout") { push(Nav2ChildRoute.Checkout) }
          EmitSpanButton(routeName = Nav2ChildRoute.ProductDetail.routeName)
        },
      )
    }

    composable(Nav2ChildRoute.Checkout.navRoute) {
      HostRouteScaffold(
        host = HostKind.NAV2,
        roleLabel = hostRole,
        title = Nav2RouteSpecs.checkout.title,
        routeName = Nav2ChildRoute.Checkout.routeName,
        routeWorkOptions = routeWorkOptions,
        description =
          "The child Nav2 host should now own the visible checkout route. Complete the order or back up within Nav2.",
        cardContent = { InfoRow(label = "productId", value = "42") },
        footerContent = {
          RouteButton(label = "Complete Order") { push(Nav2ChildRoute.Confirmation) }
          EmitSpanButton(routeName = Nav2ChildRoute.Checkout.routeName)
        },
      )
    }

    composable(Nav2ChildRoute.Confirmation.navRoute) {
      HostRouteScaffold(
        host = HostKind.NAV2,
        roleLabel = hostRole,
        title = Nav2RouteSpecs.confirmation.title,
        routeName = Nav2ChildRoute.Confirmation.routeName,
        routeWorkOptions = routeWorkOptions,
        description =
          "Finish the child flow here, then return to the Nav3 parent and confirm ownership shifts back cleanly.",
        cardContent = { InfoRow(label = "orderId", value = "order-42") },
        footerContent = {
          EmitSpanButton(routeName = Nav2ChildRoute.Confirmation.routeName)
        },
      )
    }
  }
}

@Composable
private fun SiblingNav2Flow(
  navController: androidx.navigation.NavHostController,
  routeWorkOptions: Set<RouteWorkOption>,
  onShowNav3: () -> Unit,
  onMutateHiddenNav3: () -> Unit,
) {
  fun push(route: SiblingNav2Route) {
    if (navController.currentDestination?.route == route.navRoute) {
      return
    }
    navController.navigate(route.navRoute)
  }

  NavHost(
    navController = navController,
    startDestination = SiblingNav2Route.Home.navRoute,
    modifier = Modifier.fillMaxSize(),
  ) {
    composable(SiblingNav2Route.Home.navRoute) {
      HostRouteScaffold(
        host = HostKind.NAV2,
        roleLabel = "Sibling host",
        title = Nav2RouteSpecs.home.title,
        routeName = SiblingNav2Route.Home.routeName,
        routeWorkOptions = routeWorkOptions,
        description =
          "This Nav2 product flow stays mounted even when you switch visibility to the sibling Nav3 host.",
        footerContent = {
          RouteButton(label = "Browse Products") { push(SiblingNav2Route.ProductList) }
          RouteButton(label = "Show Nav3") { onShowNav3() }
          RouteButton(label = "Mutate hidden Nav3") { onMutateHiddenNav3() }
          EmitSpanButton(routeName = SiblingNav2Route.Home.routeName)
        },
      )
    }

    composable(SiblingNav2Route.ProductList.navRoute) {
      HostRouteScaffold(
        host = HostKind.NAV2,
        roleLabel = "Sibling host",
        title = Nav2RouteSpecs.productList.title,
        routeName = SiblingNav2Route.ProductList.routeName,
        routeWorkOptions = routeWorkOptions,
        description =
          "Advance the Nav2 stack, then switch to Nav3 and see whether the hidden Nav2 host stays quiet.",
        footerContent = {
          RouteButton(label = "Open Product 42") { push(SiblingNav2Route.ProductDetail) }
          RouteButton(label = "Show Nav3") { onShowNav3() }
          RouteButton(label = "Mutate hidden Nav3") { onMutateHiddenNav3() }
          EmitSpanButton(routeName = SiblingNav2Route.ProductList.routeName)
        },
      )
    }

    composable(SiblingNav2Route.ProductDetail.navRoute) {
      HostRouteScaffold(
        host = HostKind.NAV2,
        roleLabel = "Sibling host",
        title = Nav2RouteSpecs.productDetail.title,
        routeName = SiblingNav2Route.ProductDetail.routeName,
        routeWorkOptions = routeWorkOptions,
        description =
          "Move forward in Nav2 now, then switch away and use the hidden-host mutation buttons to probe stale ownership.",
        cardContent = {
          InfoRow(label = "productId", value = "42")
          InfoRow(label = "source", value = "nav2-sibling")
        },
        footerContent = {
          RouteButton(label = "Go to Checkout") { push(SiblingNav2Route.Checkout) }
          RouteButton(label = "Show Nav3") { onShowNav3() }
          RouteButton(label = "Mutate hidden Nav3") { onMutateHiddenNav3() }
          EmitSpanButton(routeName = SiblingNav2Route.ProductDetail.routeName)
        },
      )
    }

    composable(SiblingNav2Route.Checkout.navRoute) {
      HostRouteScaffold(
        host = HostKind.NAV2,
        roleLabel = "Sibling host",
        title = Nav2RouteSpecs.checkout.title,
        routeName = SiblingNav2Route.Checkout.routeName,
        routeWorkOptions = routeWorkOptions,
        description =
          "Checkout makes a good visible-owner checkpoint before or after you switch to Nav3.",
        cardContent = { InfoRow(label = "productId", value = "42") },
        footerContent = {
          RouteButton(label = "Complete Order") { push(SiblingNav2Route.Confirmation) }
          RouteButton(label = "Show Nav3") { onShowNav3() }
          RouteButton(label = "Mutate hidden Nav3") { onMutateHiddenNav3() }
          EmitSpanButton(routeName = SiblingNav2Route.Checkout.routeName)
        },
      )
    }

    composable(SiblingNav2Route.Confirmation.navRoute) {
      HostRouteScaffold(
        host = HostKind.NAV2,
        roleLabel = "Sibling host",
        title = Nav2RouteSpecs.confirmation.title,
        routeName = SiblingNav2Route.Confirmation.routeName,
        routeWorkOptions = routeWorkOptions,
        description =
          "Reset the Nav2 flow here or switch to Nav3 and mutate the hidden host to compare outcomes.",
        cardContent = { InfoRow(label = "orderId", value = "order-42") },
        footerContent = {
          RouteButton(label = "Reset Backstack") {
            while (navController.previousBackStackEntry != null) {
              navController.popBackStack()
            }
          }
          RouteButton(label = "Show Nav3") { onShowNav3() }
          RouteButton(label = "Mutate hidden Nav3") { onMutateHiddenNav3() }
          EmitSpanButton(routeName = SiblingNav2Route.Confirmation.routeName)
        },
      )
    }
  }
}

@Composable
private fun SiblingNav3Flow(
  backStack: SnapshotStateList<SiblingNav3Route>,
  routeWorkOptions: Set<RouteWorkOption>,
  backHandlingEnabled: Boolean,
  onShowNav2: () -> Unit,
  onMutateHiddenNav2: () -> Unit,
) {
  Nav3NavigationScope(enabled = backHandlingEnabled) {
    NavDisplay(
      backStack = backStack,
      modifier = Modifier.fillMaxSize(),
      onBack = {
        if (backStack.size > 1) {
          backStack.removeLastOrNull()
        }
      },
      entryProvider =
        entryProvider {
          entry<SiblingNav3Route.Home> {
            HostRouteScaffold(
              host = HostKind.NAV3,
              roleLabel = "Sibling host",
              title = Nav2RouteSpecs.home.title,
              routeName = SiblingNav3Route.Home.routeName,
              routeWorkOptions = routeWorkOptions,
              description =
                "This Nav3 product flow stays mounted even when you switch visibility back to the sibling Nav2 host.",
              footerContent = {
                RouteButton(label = "Browse Products") {
                  backStack.add(SiblingNav3Route.ProductList)
                }
                RouteButton(label = "Show Nav2") { onShowNav2() }
                RouteButton(label = "Mutate hidden Nav2") { onMutateHiddenNav2() }
                EmitSpanButton(routeName = SiblingNav3Route.Home.routeName)
              },
            )
          }

          entry<SiblingNav3Route.ProductList> {
            HostRouteScaffold(
              host = HostKind.NAV3,
              roleLabel = "Sibling host",
              title = Nav2RouteSpecs.productList.title,
              routeName = SiblingNav3Route.ProductList.routeName,
              routeWorkOptions = routeWorkOptions,
              description =
                "Advance the Nav3 stack, then switch to Nav2 and see whether the hidden Nav3 host stays quiet.",
              footerContent = {
                RouteButton(label = "Open Product 42") {
                  backStack.add(SiblingNav3Route.ProductDetail)
                }
                RouteButton(label = "Show Nav2") { onShowNav2() }
                RouteButton(label = "Mutate hidden Nav2") { onMutateHiddenNav2() }
                EmitSpanButton(routeName = SiblingNav3Route.ProductList.routeName)
              },
            )
          }

          entry<SiblingNav3Route.ProductDetail> {
            HostRouteScaffold(
              host = HostKind.NAV3,
              roleLabel = "Sibling host",
              title = Nav2RouteSpecs.productDetail.title,
              routeName = SiblingNav3Route.ProductDetail.routeName,
              routeWorkOptions = routeWorkOptions,
              description =
                "Move forward in Nav3 now, then switch away and use the hidden-host mutation buttons to probe stale ownership.",
              cardContent = {
                InfoRow(label = "productId", value = "42")
                InfoRow(label = "source", value = "nav3-sibling")
              },
              footerContent = {
                RouteButton(label = "Go to Checkout") { backStack.add(SiblingNav3Route.Checkout) }
                RouteButton(label = "Show Nav2") { onShowNav2() }
                RouteButton(label = "Mutate hidden Nav2") { onMutateHiddenNav2() }
                EmitSpanButton(routeName = SiblingNav3Route.ProductDetail.routeName)
              },
            )
          }

          entry<SiblingNav3Route.Checkout> {
            HostRouteScaffold(
              host = HostKind.NAV3,
              roleLabel = "Sibling host",
              title = Nav2RouteSpecs.checkout.title,
              routeName = SiblingNav3Route.Checkout.routeName,
              routeWorkOptions = routeWorkOptions,
              description =
                "Checkout makes a good visible-owner checkpoint before or after you switch to Nav2.",
              cardContent = { InfoRow(label = "productId", value = "42") },
              footerContent = {
                RouteButton(label = "Complete Order") {
                  backStack.add(SiblingNav3Route.Confirmation)
                }
                RouteButton(label = "Show Nav2") { onShowNav2() }
                RouteButton(label = "Mutate hidden Nav2") { onMutateHiddenNav2() }
                EmitSpanButton(routeName = SiblingNav3Route.Checkout.routeName)
              },
            )
          }

          entry<SiblingNav3Route.Confirmation> {
            HostRouteScaffold(
              host = HostKind.NAV3,
              roleLabel = "Sibling host",
              title = Nav2RouteSpecs.confirmation.title,
              routeName = SiblingNav3Route.Confirmation.routeName,
              routeWorkOptions = routeWorkOptions,
              description =
                "Reset the Nav3 flow here or switch to Nav2 and mutate the hidden host to compare outcomes.",
              cardContent = { InfoRow(label = "orderId", value = "order-42") },
              footerContent = {
                RouteButton(label = "Reset Backstack") {
                  backStack.resetSiblingNav3FlowTo(SiblingNav3Route.Home)
                }
                RouteButton(label = "Show Nav2") { onShowNav2() }
                RouteButton(label = "Mutate hidden Nav2") { onMutateHiddenNav2() }
                EmitSpanButton(routeName = SiblingNav3Route.Confirmation.routeName)
              },
            )
          }
        },
    )
  }
}

@Composable
private fun ProvideInteropNavigationEventDispatcher(content: @Composable () -> Unit) {
  val activity = LocalContext.current.getActivity()
  val rootOwner = rememberNavigationEventDispatcherOwner(parent = null)
  val input: NavigationEventInput? =
    remember(activity) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        OnBackInvokedDefaultInput(activity.onBackInvokedDispatcher)
      } else {
        null
      }
    }

  DisposableEffect(rootOwner, input) {
    if (input != null) {
      rootOwner.navigationEventDispatcher.addInput(input)
      onDispose { rootOwner.navigationEventDispatcher.removeInput(input) }
    } else {
      onDispose {}
    }
  }

  CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides rootOwner) {
    content()
  }
}

@Composable
private fun Nav3NavigationScope(content: @Composable () -> Unit) {
  Nav3NavigationScope(enabled = true, content = content)
}

@Composable
private fun Nav3NavigationScope(enabled: Boolean, content: @Composable () -> Unit) {
  val navigationOwner =
    rememberNavigationEventDispatcherOwner(
      enabled = enabled,
      parent = LocalNavigationEventDispatcherOwner.current,
    )
  CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides navigationOwner) {
    content()
  }
}

@Composable
private fun ClearBoundNavigationTransactionEffect(routeName: String) {
  DisposableEffect(routeName) {
    val transactionRoute = "/$routeName"
    Sentry.configureScope { scope ->
      scope.withTransaction { transaction ->
        if (transaction?.operation == NAVIGATION_OP && transaction.name == transactionRoute) {
          scope.clearTransaction()
        }
      }
    }
    onDispose {}
  }
}

private class InteropNav2TransactionController {
  private var activeTransaction: ITransaction? = null

  fun activate(routeName: String) {
    cleanup()

    val scopes = Sentry.getCurrentScopes()
    val options = scopes.options
    val normalizedRouteName = "/$routeName"

    if (options.isEnableScreenTracking) {
      Sentry.configureScope { scope -> scope.screen = normalizedRouteName }
    }

    if (!options.isTracingEnabled) {
      return
    }

    val transactionOptions =
      TransactionOptions().also {
        it.isWaitForChildren = true
        it.idleTimeout = options.idleTimeout
        val deadlineTimeoutMillis = options.deadlineTimeout
        it.deadlineTimeout = if (deadlineTimeoutMillis <= 0) null else deadlineTimeoutMillis
        it.isTrimEnd = true
      }

    val transaction =
      scopes.startTransaction(
        TransactionContext(normalizedRouteName, TransactionNameSource.ROUTE, NAVIGATION_OP),
        transactionOptions,
      )

    transaction.setTag(NAV2_SCENARIO_TAG, Nav2Scenario.NAV3_INTEROP.label)
    transaction.setTag(NAVIGATION_SAMPLE_SCENARIO_TAG, NAV3_INTEROP_SCENARIO_LABEL)

    Sentry.configureScope { scope -> scope.transaction = transaction }
    activeTransaction = transaction
  }

  fun cleanup() {
    val transaction = activeTransaction ?: return
    transaction.finish(transaction.status ?: SpanStatus.OK)
    Thread { Sentry.flush(SENTRY_FLUSH_TIMEOUT_MILLIS) }.start()
    Sentry.configureScope { scope ->
      scope.withTransaction { current ->
        if (current == transaction) {
          scope.clearTransaction()
        }
      }
    }
    activeTransaction = null
  }
}

@Composable
private fun InteropModeScaffold(
  showHud: Boolean,
  onToggleHud: () -> Unit,
  onBackToModePicker: () -> Unit,
  hudContent: @Composable () -> Unit,
  content: @Composable () -> Unit,
) {
  Box(modifier = Modifier.fillMaxSize()) {
    content()

    Surface(
      modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(999.dp),
      tonalElevation = 4.dp,
    ) {
      IconButton(onClick = onToggleHud) {
        Icon(
          imageVector = if (showHud) Icons.Filled.Close else Icons.Filled.Tune,
          contentDescription = if (showHud) "Hide interop HUD" else "Show interop HUD",
        )
      }
    }

    if (showHud) {
      Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.fillMaxSize().padding(20.dp),
      ) {
        Column(
          modifier = Modifier.fillMaxSize().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              "Ownership HUD",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = onToggleHud) {
              Icon(Icons.Filled.Close, contentDescription = "Close interop HUD")
            }
          }
          Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) { hudContent() }
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            TextButton(onClick = onBackToModePicker) { Text("Change mode") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onToggleHud) { Text("Close") }
          }
        }
      }
    }
  }
}

@Composable
private fun HostRouteScaffold(
  host: HostKind,
  roleLabel: String,
  title: String,
  routeName: String,
  routeWorkOptions: Set<RouteWorkOption>,
  description: String,
  cardContent: (@Composable ColumnScope.() -> Unit)? = null,
  footerContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
  TracedInteropRoute(host = host, routeName = routeName, routeWorkOptions = routeWorkOptions) {
    Column(
      modifier =
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)
    ) {
      Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        HostBanner(host = host, roleLabel = roleLabel)
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodyMedium)
        InfoRow(label = "Transaction route", value = "/$routeName")
        if (cardContent != null) {
          Card(
            colors =
              CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              cardContent()
            }
          }
        }
      }

      if (footerContent != null) {
        Spacer(Modifier.size(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { footerContent() }
      }
    }
  }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TracedInteropRoute(
  host: HostKind,
  routeName: String,
  routeWorkOptions: Set<RouteWorkOption>,
  content: @Composable BoxScope.() -> Unit,
) {
  tagCurrentInteropScenario(host)
  SentryTraced(
    tag = "${host.label} /$routeName",
    enableUserInteractionTracing = false,
  ) {
    InteropRouteWorkEffect(host = host, routeName = routeName, routeWorkOptions = routeWorkOptions)
    content()
  }
}

private fun tagCurrentInteropScenario(host: HostKind) {
  when (host) {
    HostKind.NAV2 -> tagCurrentNav2Scenario(Nav2Scenario.NAV3_INTEROP)
    HostKind.NAV3 -> {
      tagCurrentNavigationSampleScenario(NAV3_INTEROP_SCENARIO_LABEL)
      Sentry.getSpan()?.setTag(NAV3_SCENARIO_TAG, NAV3_INTEROP_SCENARIO_LABEL)
      Sentry.configureScope { scope ->
        scope.withTransaction { transaction ->
          transaction?.setTag(NAV3_SCENARIO_TAG, NAV3_INTEROP_SCENARIO_LABEL)
        }
      }
    }
  }
}

@Composable
private fun InteropRouteWorkEffect(
  host: HostKind,
  routeName: String,
  routeWorkOptions: Set<RouteWorkOption>,
) {
  val currentOptions = rememberUpdatedState(routeWorkOptions)

  if (RouteWorkOption.MANUAL_CHILD_SPAN in currentOptions.value) {
    recordInteropManualChildSpan(host, routeName)
  }

  LaunchedEffect(host, routeName) {
    runInteropRouteWork(
      host = host,
      routeName = routeName,
      options = currentOptions.value,
    )
  }
}

private suspend fun runInteropRouteWork(
  host: HostKind,
  routeName: String,
  options: Set<RouteWorkOption>,
) {
  RouteWorkOption.entries.forEach { option ->
    if (option !in options || option == RouteWorkOption.MANUAL_CHILD_SPAN) {
      return@forEach
    }

    tagInteropSampleAction(host, option.tagName, routeName)
    when (option) {
      RouteWorkOption.HTTP_REQUEST -> {
        try {
          GithubAPI.runRouteWorkRequest()
        } catch (e: IOException) {
          Sentry.captureException(e)
        } catch (e: HttpException) {
          Sentry.captureException(e)
        } finally {
          withContext(Dispatchers.IO) { Sentry.flush(SENTRY_FLUSH_TIMEOUT_MILLIS) }
        }
      }

      RouteWorkOption.MANUAL_CHILD_SPAN -> Unit
    }
  }
}

private fun recordInteropManualChildSpan(host: HostKind, routeName: String) {
  val span =
    Sentry.getSpan()
      ?.startChild(
        "test.navigation.manual_span",
        "${host.label} /$routeName manual span",
      )
  span?.setData("sample.manual_span", true)
  span?.finish()
}

private fun tagInteropSampleAction(host: HostKind, action: String, routeName: String) {
  val span = Sentry.getSpan() ?: return
  when (host) {
    HostKind.NAV2 -> tagNav2SampleAction(action, routeName)
    HostKind.NAV3 -> {
      span.setTag("sample_action", "nav3_$action")
      span.setTag("sample_nav3_route", routeName)
    }
  }
}

@Composable
private fun NestedHostCard(
  host: HostKind,
  roleLabel: String,
  title: String,
  description: String,
  content: @Composable () -> Unit,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, host.borderColor()),
    modifier = Modifier.fillMaxSize(),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      HostBanner(host = host, roleLabel = roleLabel)
      Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      Text(description, style = MaterialTheme.typography.bodyMedium)
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.fillMaxSize(),
      ) {
        content()
      }
    }
  }
}

@Composable
private fun Nav3InteropLandingScreen(onSelectMode: (InteropMode) -> Unit) {
  Column(
    modifier =
      Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(
        text = "Nav3 Interop",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text =
          "Choose one interop mode, then the picker gets out of the way so the selected Nav2/Nav3 flow can use the full tab content area.",
        style = MaterialTheme.typography.bodyMedium,
      )
    }

    InteropModeSelector(onSelect = onSelectMode)

    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          "What to look for",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          "After you enter a mode, use the route buttons and the Recent Transactions sheet to inspect transaction ownership, route handoff, and hidden-host mutations.",
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }

    Spacer(Modifier.weight(1f))
  }
}

@Composable
private fun InteropModeSelector(onSelect: (InteropMode) -> Unit) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(
        "Interop mode",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      InteropMode.entries.forEach { mode ->
        Button(onClick = { onSelect(mode) }, modifier = Modifier.fillMaxWidth()) {
          Text(mode.label)
        }
      }
    }
  }
}

@Composable
private fun InteropSummaryCard(summary: InteropSummary, sentrySnapshot: SentryScopeSnapshot) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        "Ownership HUD",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      InfoRow(label = "Mode", value = summary.mode.label)
      InfoRow(label = "Visible host", value = summary.visibleHost.label)
      InfoRow(label = "Current route", value = summary.currentRoute)
      InfoRow(label = "Nav2 stack", value = summary.nav2Stack)
      InfoRow(label = "Nav3 stack", value = summary.nav3Stack)
      InfoRow(label = "Scope screen", value = sentrySnapshot.screen ?: "<none>")
      InfoRow(
        label = "Scope transaction",
        value =
          sentrySnapshot.transactionName?.let { name ->
            val suffix = sentrySnapshot.transactionOp?.let { op -> " ($op)" } ?: ""
            "$name$suffix"
          } ?: "<none>",
      )
      Text(
        summary.notes,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun HostBanner(host: HostKind, roleLabel: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Pill(
      text = host.label,
      containerColor = host.containerColor(),
      contentColor = host.contentColor(),
    )
    Pill(
      text = roleLabel,
      containerColor = MaterialTheme.colorScheme.surfaceVariant,
      contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun Pill(text: String, containerColor: Color, contentColor: Color) {
  Surface(color = containerColor, contentColor = contentColor, shape = RoundedCornerShape(999.dp)) {
    Text(
      text = text,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
      fontWeight = FontWeight.Bold,
    )
  }
}

@Composable
private fun InfoRow(label: String, value: String) {
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
        .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(label, fontWeight = FontWeight.Bold)
    Text(value, maxLines = 3, overflow = TextOverflow.Ellipsis)
  }
}

@Composable
private fun RouteButton(label: String, onClick: () -> Unit) {
  Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

@Composable
private fun EmitSpanButton(routeName: String) {
  RouteButton(label = "Emit a span") { emitSampleNavigationSpan(routeName, "Nav3 Interop") }
}

@Composable
private fun rememberSentryScopeSnapshot(summary: InteropSummary): SentryScopeSnapshot {
  val latestSummary by rememberUpdatedState(summary)
  var snapshot by remember { mutableStateOf(SentryScopeSnapshot()) }

  SideEffect {
    latestSummary.currentRoute
    Sentry.configureScope { scope ->
      var transactionName: String? = null
      var transactionOp: String? = null
      scope.withTransaction { transaction ->
        transactionName = transaction?.name
        transactionOp = transaction?.operation
      }
      snapshot =
        SentryScopeSnapshot(
          screen = scope.screen,
          transactionName = transactionName,
          transactionOp = transactionOp,
        )
    }
  }

  return snapshot
}

private fun SnapshotStateList<Nav3ChildRoute>.resetNav3ChildFlowTo(route: Nav3ChildRoute) {
  clear()
  add(route)
}

private fun SnapshotStateList<SiblingNav3Route>.resetSiblingNav3FlowTo(route: SiblingNav3Route) {
  clear()
  add(route)
}

private fun <T> List<T>.toPreviewString(routeName: (T) -> String): String =
  joinToString(" -> ") { route -> "/${routeName(route)}" }

/**
 * Reads a Nav2 [NavHostController]'s back stack as route names. The NavController is the single
 * source of truth for the interop lab's Nav2 hosts, so this avoids shadowing it with a parallel
 * list that can drift out of sync with the real navigation state.
 */
@Composable
private fun rememberNav2RouteNames(navController: NavHostController): List<String> {
  val entries by navController.currentBackStack.collectAsState()
  return entries.mapNotNull { entry -> entry.destination.route }
}

private enum class InteropMode(val label: String) {
  NAV2_PARENT_NAV3_CHILD("Nav2 parent -> Nav3 child"),
  NAV3_PARENT_NAV2_CHILD("Nav3 parent -> Nav2 child"),
  SIBLING_ROOTS("Sibling roots"),
}

private enum class HostKind(val label: String) {
  NAV2("Nav2"),
  NAV3("Nav3"),
}

private data class InteropSummary(
  val mode: InteropMode,
  val visibleHost: HostKind,
  val currentRoute: String,
  val nav2Stack: String,
  val nav3Stack: String,
  val notes: String,
) {
  val backStackSummary: String
    get() = "Nav2: $nav2Stack | Nav3: $nav3Stack"

  companion object {
    fun empty(): InteropSummary =
      InteropSummary(
        mode = InteropMode.NAV2_PARENT_NAV3_CHILD,
        visibleHost = HostKind.NAV2,
        currentRoute = "/Nav3Interop",
        nav2Stack = "Pick a mode to begin",
        nav3Stack = "Pick a mode to begin",
        notes =
          "Choose a mode, then use route navigation and Recent Transactions to inspect ownership.",
      )
  }
}

private data class SentryScopeSnapshot(
  val screen: String? = null,
  val transactionName: String? = null,
  val transactionOp: String? = null,
)

private fun HostKind.containerColor(): Color =
  when (this) {
    HostKind.NAV2 -> Color(0xFF103E74)
    HostKind.NAV3 -> Color(0xFF5A2E91)
  }

private fun HostKind.contentColor(): Color = Color.White

private fun HostKind.borderColor(): Color =
  when (this) {
    HostKind.NAV2 -> Color(0xFF3C82D9)
    HostKind.NAV3 -> Color(0xFF9B6CFF)
  }

private const val NAV3_INTEROP_SCENARIO_LABEL = "Nav3 Interop"
private const val NAV3_SCENARIO_TAG = "sample_nav3_scenario"
private const val NAVIGATION_OP = "navigation"
private const val NAVIGATION_SAMPLE_SCENARIO_TAG = "sample_navigation_scenario"

private sealed class Nav2ParentRoute(val navRoute: String, val routeName: String) {
  data object Home : Nav2ParentRoute("Nav2Home", "Nav2Home")

  data object ProductList : Nav2ParentRoute("Nav2ProductList", "Nav2ProductList")

  data object ProductDetail : Nav2ParentRoute("Nav2ProductDetail", "Nav2ProductDetail")

  data object Nav3Host : Nav2ParentRoute("Nav2Nav3Host", "Nav2Nav3Host")

  companion object {
    private val all = listOf(Home, ProductList, ProductDetail, Nav3Host)

    fun fromNavRoute(navRoute: String?): Nav2ParentRoute? = all.firstOrNull {
      it.navRoute == navRoute
    }
  }
}

private sealed class Nav2ChildRoute(val navRoute: String, val routeName: String) {
  data object ProductDetail : Nav2ChildRoute("Nav2ProductDetail", "Nav2ProductDetail")

  data object Checkout : Nav2ChildRoute("Nav2Checkout", "Nav2Checkout")

  data object Confirmation : Nav2ChildRoute("Nav2Confirmation", "Nav2Confirmation")

  companion object {
    private val all = listOf(ProductDetail, Checkout, Confirmation)

    fun fromNavRoute(navRoute: String?): Nav2ChildRoute? = all.firstOrNull {
      it.navRoute == navRoute
    }
  }
}

private sealed class SiblingNav2Route(val navRoute: String, val routeName: String) {
  data object Home : SiblingNav2Route("Nav2Home", "Nav2Home")

  data object ProductList : SiblingNav2Route("Nav2ProductList", "Nav2ProductList")

  data object ProductDetail : SiblingNav2Route("Nav2ProductDetail", "Nav2ProductDetail")

  data object Checkout : SiblingNav2Route("Nav2Checkout", "Nav2Checkout")

  data object Confirmation : SiblingNav2Route("Nav2Confirmation", "Nav2Confirmation")

  companion object {
    private val all = listOf(Home, ProductList, ProductDetail, Checkout, Confirmation)

    fun fromNavRoute(navRoute: String?): SiblingNav2Route? = all.firstOrNull {
      it.navRoute == navRoute
    }
  }
}

private sealed interface Nav3ParentRoute {
  val routeName: String

  data object Home : Nav3ParentRoute {
    override val routeName: String = "Nav3Home"
  }

  data object ProductList : Nav3ParentRoute {
    override val routeName: String = "Nav3ProductList"
  }

  data object ProductDetail : Nav3ParentRoute {
    override val routeName: String = "Nav3ProductDetail"
  }

  data object Nav2Host : Nav3ParentRoute {
    override val routeName: String = "Nav3Nav2Host"
  }
}

private sealed interface Nav3ChildRoute {
  val routeName: String

  data object ProductDetail : Nav3ChildRoute {
    override val routeName: String = "Nav3ProductDetail"
  }

  data object Checkout : Nav3ChildRoute {
    override val routeName: String = "Nav3Checkout"
  }

  data object Confirmation : Nav3ChildRoute {
    override val routeName: String = "Nav3Confirmation"
  }
}

private sealed interface SiblingNav3Route {
  val routeName: String

  data object Home : SiblingNav3Route {
    override val routeName: String = "Nav3Home"
  }

  data object ProductList : SiblingNav3Route {
    override val routeName: String = "Nav3ProductList"
  }

  data object ProductDetail : SiblingNav3Route {
    override val routeName: String = "Nav3ProductDetail"
  }

  data object Checkout : SiblingNav3Route {
    override val routeName: String = "Nav3Checkout"
  }

  data object Confirmation : SiblingNav3Route {
    override val routeName: String = "Nav3Confirmation"
  }
}
