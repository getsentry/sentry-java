package io.sentry.samples.android.navigation.nav3

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.ui.NavDisplay
import io.sentry.compose.navigation3.BackStackEntryMapper
import io.sentry.compose.navigation3.SentryNavEffect
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Keys of the back stacks in [Nav3UncoveredActivity], named so each setup's records stand out. */
@Serializable
internal sealed interface Nav3UncoveredRoute {
  @Serializable @SerialName("EntriesHome") data object EntriesHome : Nav3UncoveredRoute

  @Serializable @SerialName("PageA") data object PageA : Nav3UncoveredRoute

  @Serializable @SerialName("PageB") data object PageB : Nav3UncoveredRoute

  @Serializable @SerialName("ActivityRoot") data object ActivityRoot : Nav3UncoveredRoute

  @Serializable @SerialName("FragmentRoot") data object FragmentRoot : Nav3UncoveredRoute

  @Serializable @SerialName("Detail") data class Detail(val from: String) : Nav3UncoveredRoute
}

/** The setups [Nav3UncoveredActivity] can show, one at a time. */
private enum class Nav3UncoveredSetup(val label: String, val expectation: String) {
  ENTRIES(
    label = "NavDisplay(entries)",
    expectation =
      "The plugin only instruments NavDisplay(backStack), so with auto-instrumentation alone " +
        "nothing records. In Manual and Manual + auto modes, the manual fallback " +
        "SentryNavEffect(backStack, ...) records.",
  ),
  PAGER(
    label = "Pager",
    expectation =
      "Two NavDisplays side by side in a HorizontalPager, both composed. They share this " +
        "Activity's nav root, so with auto-instrumentation only the first one (Page A) records, " +
        "even while Page B is showing.",
  ),
  ACTIVITY_AND_FRAGMENT(
    label = "Activity + Fragment",
    expectation =
      "A NavDisplay in this Activity and one in a Fragment inside it. The Fragment is its own nav " +
        "root, so both record and compete for the nav lease, as with two manual effects. This " +
        "setup is unsupported either way.",
  ),
}

/**
 * Shows setups that auto-instrumentation doesn't cover, each as the only `NavDisplay` setup in this
 * Activity so it isn't nested in another `NavDisplay`. See [Nav3UncoveredSetup] for what each
 * should record.
 */
class Nav3UncoveredActivity : FragmentActivity() {

  internal val effectMode: Nav3SentryNavEffectMode
    get() = intent.nav3SentryNavEffectMode()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    NavigationTransactionHistory.install()
    setContent { Nav3SampleTheme { UncoveredSetups(effectMode) } }
  }
}

@Composable
private fun UncoveredSetups(effectMode: Nav3SentryNavEffectMode) {
  var setup by rememberSaveable { mutableStateOf(Nav3UncoveredSetup.ENTRIES) }

  Column(
    modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text("Setups auto-instrumentation doesn't cover", style = MaterialTheme.typography.titleLarge)
    Text(
      Nav3AutoInstrumentationStatus.summary(effectMode),
      style = MaterialTheme.typography.bodySmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Nav3UncoveredSetup.entries.forEach { option ->
        FilterChip(
          selected = option == setup,
          onClick = { setup = option },
          label = { Text(option.label) },
        )
      }
    }
    Text(setup.expectation, style = MaterialTheme.typography.bodySmall)
    when (setup) {
      Nav3UncoveredSetup.ENTRIES -> EntriesSetup(effectMode)
      Nav3UncoveredSetup.PAGER -> PagerSetup(effectMode)
      Nav3UncoveredSetup.ACTIVITY_AND_FRAGMENT -> ActivityAndFragmentSetup(effectMode)
    }
  }
}

@Composable
private fun EntriesSetup(effectMode: Nav3SentryNavEffectMode) {
  val backStack = remember {
    mutableStateListOf<Nav3UncoveredRoute>(Nav3UncoveredRoute.EntriesHome)
  }
  // The manual fallback for NavDisplay(entries): observe the back stack the entries come from.
  ManualEffect(effectMode, backStack) {
    val entries =
      rememberDecoratedNavEntries(
        backStack = backStack,
        entryProvider = uncoveredEntryProvider(backStack, "EntriesHome"),
      )
    NavDisplay(
      entries = entries,
      modifier = Modifier.fillMaxWidth().height(360.dp),
      onBack = { backStack.removeLastOrNull() },
    )
  }
}

@Composable
private fun PagerSetup(effectMode: Nav3SentryNavEffectMode) {
  val pagerState = rememberPagerState(pageCount = { 2 })
  HorizontalPager(
    state = pagerState,
    // Keep both pages composed, so both NavDisplays exist at once.
    beyondViewportPageCount = 1,
    modifier = Modifier.fillMaxWidth().height(420.dp),
  ) { page ->
    val root = if (page == 0) Nav3UncoveredRoute.PageA else Nav3UncoveredRoute.PageB
    val name = if (page == 0) "PageA" else "PageB"
    val backStack = remember { mutableStateListOf<Nav3UncoveredRoute>(root) }
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(8.dp)
          .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
          .padding(8.dp)
    ) {
      Text("Page ${if (page == 0) "A" else "B"} (swipe to switch)")
      ManualEffect(effectMode, backStack) {
        NavDisplay(
          backStack = backStack,
          onBack = { backStack.removeLastOrNull() },
          entryProvider = uncoveredEntryProvider(backStack, name),
        )
      }
    }
  }
}

@Composable
private fun ActivityAndFragmentSetup(effectMode: Nav3SentryNavEffectMode) {
  val backStack = remember {
    mutableStateListOf<Nav3UncoveredRoute>(Nav3UncoveredRoute.ActivityRoot)
  }
  Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
    ManualEffect(effectMode, backStack) {
      NavDisplay(
        backStack = backStack,
        modifier = Modifier.fillMaxWidth().height(240.dp),
        onBack = { backStack.removeLastOrNull() },
        entryProvider = uncoveredEntryProvider(backStack, "ActivityRoot"),
      )
    }
    Text("Fragment:", style = MaterialTheme.typography.titleMedium)
    val containerId = rememberSaveable { View.generateViewId() }
    AndroidView(
      modifier = Modifier.fillMaxWidth().height(320.dp),
      factory = { context ->
        FragmentContainerView(context).apply {
          id = containerId
          post {
            val fragmentManager = (context as FragmentActivity).supportFragmentManager
            if (fragmentManager.findFragmentById(containerId) == null) {
              fragmentManager
                .beginTransaction()
                .add(containerId, Nav3UncoveredFragment())
                .commitAllowingStateLoss()
            }
          }
        }
      },
      onRelease = { view ->
        val fragmentManager = (view.context as FragmentActivity).supportFragmentManager
        fragmentManager.findFragmentById(view.id)?.let { fragment ->
          fragmentManager.beginTransaction().remove(fragment).commitAllowingStateLoss()
        }
      },
    )
  }
}

/** A Fragment with its own `NavDisplay`, inside [Nav3UncoveredActivity]'s. */
class Nav3UncoveredFragment : Fragment() {

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View =
    ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      val effectMode = (requireActivity() as Nav3UncoveredActivity).effectMode
      setContent {
        Nav3SampleTheme {
          val backStack = remember {
            mutableStateListOf<Nav3UncoveredRoute>(Nav3UncoveredRoute.FragmentRoot)
          }
          ManualEffect(effectMode, backStack) {
            NavDisplay(
              backStack = backStack,
              onBack = { backStack.removeLastOrNull() },
              entryProvider = uncoveredEntryProvider(backStack, "FragmentRoot"),
            )
          }
        }
      }
    }
}

/** Calls `SentryNavEffect` around [navDisplay] as [effectMode] requires. */
@Composable
private fun ManualEffect(
  effectMode: Nav3SentryNavEffectMode,
  backStack: List<Nav3UncoveredRoute>,
  navDisplay: @Composable () -> Unit,
) {
  if (effectMode.callsSentryNavEffectBeforeNavDisplay) {
    SentryNavEffect(backStack, BackStackEntryMapper.serialName())
  }
  navDisplay()
  if (effectMode.callsSentryNavEffectAfterNavDisplay) {
    SentryNavEffect(backStack, BackStackEntryMapper.serialName())
  }
}

private fun uncoveredEntryProvider(backStack: MutableList<Nav3UncoveredRoute>, rootName: String) =
  entryProvider<Nav3UncoveredRoute> {
    entry<Nav3UncoveredRoute.EntriesHome> { RootScreen(rootName, backStack) }
    entry<Nav3UncoveredRoute.PageA> { RootScreen(rootName, backStack) }
    entry<Nav3UncoveredRoute.PageB> { RootScreen(rootName, backStack) }
    entry<Nav3UncoveredRoute.ActivityRoot> { RootScreen(rootName, backStack) }
    entry<Nav3UncoveredRoute.FragmentRoot> { RootScreen(rootName, backStack) }
    entry<Nav3UncoveredRoute.Detail> { key ->
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Detail from ${key.from}", style = MaterialTheme.typography.titleMedium)
        RouteButton(
          "Back",
          onClick = { backStack.removeLastOrNull() },
          testTag = nav3TestTag("uncovered_back_${key.from}"),
        )
      }
    }
  }

@Composable
private fun RootScreen(rootName: String, backStack: MutableList<Nav3UncoveredRoute>) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(rootName, style = MaterialTheme.typography.titleMedium)
    RouteButton(
      "Open detail from $rootName",
      onClick = { backStack.add(Nav3UncoveredRoute.Detail(rootName)) },
      testTag = nav3TestTag("uncovered_open_detail_$rootName"),
    )
  }
}

@Composable
internal fun UncoveredRoute(onOpen: () -> Unit) {
  RouteScaffold(
    routeSpec = Nav3Route.Uncovered.routeSpec(),
    testTagPrefix = nav3TestTag("route_uncovered"),
    cardContent = {
      RouteButton(
        "Open uncovered setups Activity",
        onClick = onOpen,
        testTag = nav3TestTag("uncovered_open"),
      )
    },
  )
}
