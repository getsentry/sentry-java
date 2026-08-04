package io.sentry.samples.android.navigation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.ui.NavDisplay
import io.sentry.compose.navigation3.SentryNav3NavigationEffect
import io.sentry.compose.navigation3.rememberSentryNavEntryDecorator
import io.sentry.compose.navigation3.rememberSentryNavStateHolder

class NavigationActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) { NavigationSampleShell() }
      }
    }
  }
}

@Composable
private fun NavigationSampleShell() {
  var selectedMilestone by remember { mutableStateOf(MilestoneTab.Nav2Parity) }
  val parityBackStack = remember { mutableStateListOf<SampleRoute>(SampleRoute.ParityHome) }
  val paneBackStack = remember {
    mutableStateListOf<SampleRoute>(SampleRoute.PaneList, SampleRoute.PaneDetail("demo-alpha"))
  }
  var selectedStack by remember { mutableStateOf(DemoStack.Home) }
  val retainedStacks = rememberRetainedStacks()

  Column(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Navigation 3 Sample", style = MaterialTheme.typography.headlineSmall)
      Text(
        "Each tab maps to an Android Nav3 Support milestone and owns real Nav3 state.",
        style = MaterialTheme.typography.bodyMedium,
      )
    }

    PrimaryTabRow(selectedTabIndex = selectedMilestone.ordinal) {
      MilestoneTab.entries.forEach { tab ->
        Tab(
          selected = selectedMilestone == tab,
          onClick = { selectedMilestone = tab },
          text = { Text(tab.label) },
        )
      }
    }

    Box(modifier = Modifier.fillMaxSize().padding(20.dp)) {
      when (selectedMilestone) {
        MilestoneTab.Nav2Parity -> Nav2ParityTab(backStack = parityBackStack)
        MilestoneTab.MultiPane -> MultiPaneTab(backStack = paneBackStack)
        MilestoneTab.MultiBackstack ->
          MultiBackstackTab(
            selectedStack = selectedStack,
            retainedStacks = retainedStacks,
            onSelectStack = { selectedStack = it },
          )
      }
    }
  }
}

@Composable
private fun rememberRetainedStacks(): SnapshotStateMap<DemoStack, SnapshotStateList<SampleRoute>> =
  remember {
    mutableStateMapOf(
      DemoStack.Home to mutableStateListOf<SampleRoute>(SampleRoute.StackRoot(DemoStack.Home)),
      DemoStack.Search to mutableStateListOf<SampleRoute>(SampleRoute.StackRoot(DemoStack.Search)),
      DemoStack.Profile to
        mutableStateListOf<SampleRoute>(SampleRoute.StackRoot(DemoStack.Profile)),
    )
  }

@Composable
private fun Nav2ParityTab(backStack: SnapshotStateList<SampleRoute>) {
  val dialogStrategy = remember { DialogSceneStrategy<SampleRoute>() }

  SentryNav3NavigationEffect(
    backStack = backStack,
    nameExtractor = ::routeName,
    argumentsExtractor = ::routeArguments,
  )

  ScenarioColumn(
    title = "Milestone 1: Nav2 parity",
    description =
      "Single-stack navigation with ordinary screens, back navigation, and a real dialog " +
        "destination.",
  ) {
    BackStackSummary(backStack)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      Button(onClick = { backStack.add(SampleRoute.ParityDetail("demo-${backStack.size}")) }) {
        Text("Open detail")
      }
      OutlinedButton(onClick = { backStack.add(SampleRoute.ParityDialog("demo-dialog")) }) {
        Text("Open dialog")
      }
      OutlinedButton(enabled = backStack.size > 1, onClick = { backStack.pop() }) {
        Text("Back")
      }
      OutlinedButton(onClick = { backStack.resetTo(SampleRoute.ParityHome) }) { Text("Reset") }
    }

    NavDisplay(
      backStack = backStack,
      onBack = { backStack.pop() },
      sceneStrategies = listOf(dialogStrategy),
      modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp),
      entryProvider =
        entryProvider {
          entry<SampleRoute.ParityHome> {
            RouteCard(
              title = "Home",
              description = "Root route for the Nav2 parity scenario.",
            )
          }
          entry<SampleRoute.ParityDetail> { route ->
            RouteCard(
              title = "Detail ${route.itemId}",
              description = "Safe demo argument item_id=${route.itemId} is sent to Sentry.",
            )
          }
          entry<SampleRoute.ParityDialog>(
            metadata =
              DialogSceneStrategy.dialog(DialogProperties(windowTitle = "Nav3 sample dialog"))
          ) { route ->
            RouteCard(
              title = "Dialog ${route.dialogKind}",
              description = "A real Nav3 dialog destination on the same backstack.",
            )
          }
        },
    )
  }
}

@Composable
private fun MultiPaneTab(backStack: SnapshotStateList<SampleRoute>) {
  val holder =
    rememberSentryNavStateHolder(
      nameExtractor = ::routeName,
      argumentsExtractor = ::routeArguments,
      primaryRouteSelector = { visibleEntries ->
        visibleEntries.firstOrNull { it.metadata[LIST_DETAIL_PANE] == DETAIL_PANE }
          ?: visibleEntries.lastOrNull()
      },
    )
  val sentryDecorator = rememberSentryNavEntryDecorator(holder)
  val listDetailStrategy = remember { SampleListDetailSceneStrategy<SampleRoute>() }

  SentryNav3NavigationEffect(backStack = backStack, holder = holder)

  ScenarioColumn(
    title = "Milestone 2: Multi-pane support",
    description =
      "A local SceneStrategy renders list and detail entries together, and the Sentry " +
        "decorator reports visible entries.",
  ) {
    BackStackSummary(backStack)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      Button(onClick = { backStack.showDetail("demo-alpha") }) { Text("Show alpha") }
      OutlinedButton(onClick = { backStack.showDetail("demo-beta") }) { Text("Show beta") }
      OutlinedButton(onClick = { backStack.add(SampleRoute.PaneProfile) }) {
        Text("Open profile")
      }
      OutlinedButton(onClick = { backStack.resetToListDetail("demo-alpha") }) { Text("Reset") }
    }

    NavDisplay(
      backStack = backStack,
      onBack = { backStack.popToPaneList() },
      entryDecorators = listOf(sentryDecorator),
      sceneStrategies = listOf(listDetailStrategy),
      modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp),
      entryProvider =
        entryProvider {
          entry<SampleRoute.PaneList>(metadata = listPaneMetadata()) {
            RouteCard(
              title = "List pane",
              description = "Choose a demo item. Detail routes should become primary.",
            )
          }
          entry<SampleRoute.PaneDetail>(metadata = detailPaneMetadata()) { route ->
            RouteCard(
              title = "Detail ${route.itemId}",
              description = "Visible detail entry with safe item_id=${route.itemId}.",
            )
          }
          entry<SampleRoute.PaneProfile> {
            RouteCard(
              title = "Profile",
              description = "Single-pane route used to leave and re-enter the list/detail scene.",
            )
          }
        },
    )
  }
}

@Composable
private fun MultiBackstackTab(
  selectedStack: DemoStack,
  retainedStacks: SnapshotStateMap<DemoStack, SnapshotStateList<SampleRoute>>,
  onSelectStack: (DemoStack) -> Unit,
) {
  val selectedBackStack = retainedStacks[selectedStack] ?: return
  val backStackSnapshots = retainedStacks.mapValues { it.value.toList() }
  val inactiveStack = DemoStack.entries.first { it != selectedStack }

  SentryNav3NavigationEffect(
    selectedStack = selectedStack,
    backStacks = backStackSnapshots,
    stacksInUse = setOf(selectedStack),
    stackNameExtractor = { it.routeName },
    nameExtractor = ::routeName,
    argumentsExtractor = ::routeArguments,
  )

  ScenarioColumn(
    title = "Milestone 3: Multi-backstack support",
    description =
      "Bottom-tab style navigation with one selected stack visible and all stacks retained in " +
        "background.",
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      DemoStack.entries.forEach { stack ->
        if (stack == selectedStack) {
          Button(onClick = { onSelectStack(stack) }) { Text(stack.label) }
        } else {
          OutlinedButton(onClick = { onSelectStack(stack) }) { Text(stack.label) }
        }
      }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      Button(onClick = { selectedBackStack.pushStackDetail(selectedStack) }) {
        Text("Push selected")
      }
      OutlinedButton(onClick = { retainedStacks[inactiveStack]?.pushStackDetail(inactiveStack) }) {
        Text("Push ${inactiveStack.label}")
      }
      OutlinedButton(enabled = selectedBackStack.size > 1, onClick = { selectedBackStack.pop() }) {
        Text("Back")
      }
      OutlinedButton(onClick = { retainedStacks.resetRetainedStacks() }) { Text("Reset") }
    }

    RetainedStacksSummary(retainedStacks = retainedStacks, selectedStack = selectedStack)

    NavDisplay(
      backStack = selectedBackStack,
      onBack = { selectedBackStack.pop() },
      modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp),
      entryProvider =
        entryProvider {
          entry<SampleRoute.StackRoot> { route ->
            RouteCard(
              title = "${route.stack.label} root",
              description = "Selected retained stack: ${selectedStack.routeName}.",
            )
          }
          entry<SampleRoute.StackDetail> { route ->
            RouteCard(
              title = "${route.stack.label} detail ${route.itemId}",
              description = "Retained stack detail with safe item_id=${route.itemId}.",
            )
          }
        },
    )
  }
}

@Composable
private fun ScenarioColumn(
  title: String,
  description: String,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    Text(description, style = MaterialTheme.typography.bodyMedium)
    content()
  }
}

@Composable
private fun BackStackSummary(backStack: List<SampleRoute>) {
  Text("Current route: ${routeName(backStack.last())}", style = MaterialTheme.typography.bodyMedium)
  Text(
    "Backstack: ${backStack.joinToString(" > ") { routeName(it) }}",
    style = MaterialTheme.typography.bodySmall,
  )
}

@Composable
private fun RetainedStacksSummary(
  retainedStacks: Map<DemoStack, List<SampleRoute>>,
  selectedStack: DemoStack,
) {
  Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        "Selected stack: ${selectedStack.routeName}",
        style = MaterialTheme.typography.bodyMedium,
      )
      retainedStacks.forEach { (stack, backStack) ->
        Text(
          "${stack.label}: ${backStack.joinToString(" > ") { routeName(it) }}",
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
  }
}

@Composable
private fun RouteCard(title: String, description: String) {
  Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(description, style = MaterialTheme.typography.bodyMedium)
    }
  }
}

private class SampleListDetailScene<T : Any>(
  override val key: Any,
  override val previousEntries: List<NavEntry<T>>,
  private val listEntry: NavEntry<T>,
  private val detailEntry: NavEntry<T>,
) : Scene<T> {
  override val entries: List<NavEntry<T>> = listOf(listEntry, detailEntry)

  override val content: @Composable () -> Unit = {
    Row(modifier = Modifier.fillMaxSize()) {
      Column(modifier = Modifier.weight(0.42f).fillMaxHeight()) { listEntry.Content() }
      Column(modifier = Modifier.weight(0.58f).fillMaxHeight()) { detailEntry.Content() }
    }
  }
}

private class SampleListDetailSceneStrategy<T : Any> : SceneStrategy<T> {
  override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
    val detailEntry =
      entries.lastOrNull()?.takeIf { it.metadata[PaneMetadataKey] == DETAIL_PANE } ?: return null
    val listEntry = entries.findLast { it.metadata[PaneMetadataKey] == LIST_PANE } ?: return null

    return SampleListDetailScene(
      key = listEntry.contentKey,
      previousEntries = entries.dropLast(1),
      listEntry = listEntry,
      detailEntry = detailEntry,
    )
  }
}

private sealed interface SampleRoute {
  data object ParityHome : SampleRoute

  data class ParityDetail(val itemId: String) : SampleRoute

  data class ParityDialog(val dialogKind: String) : SampleRoute

  data object PaneList : SampleRoute

  data class PaneDetail(val itemId: String) : SampleRoute

  data object PaneProfile : SampleRoute

  data class StackRoot(val stack: DemoStack) : SampleRoute

  data class StackDetail(val stack: DemoStack, val itemId: String) : SampleRoute
}

private enum class MilestoneTab(val label: String) {
  Nav2Parity("Nav2 parity"),
  MultiPane("Multi-pane"),
  MultiBackstack("Multi-backstack"),
}

private enum class DemoStack(val label: String, val routeName: String) {
  Home("Home", "home"),
  Search("Search", "search"),
  Profile("Profile", "profile"),
}

private object PaneMetadataKey : NavMetadataKey<String>

private const val LIST_DETAIL_PANE = "listDetailPane"
private const val LIST_PANE = "list"
private const val DETAIL_PANE = "detail"

private fun listPaneMetadata(): Map<String, Any> =
  metadata { put(PaneMetadataKey, LIST_PANE) } + mapOf(LIST_DETAIL_PANE to LIST_PANE)

private fun detailPaneMetadata(): Map<String, Any> =
  metadata { put(PaneMetadataKey, DETAIL_PANE) } + mapOf(LIST_DETAIL_PANE to DETAIL_PANE)

private fun SnapshotStateList<SampleRoute>.pop() {
  if (size > 1) removeAt(lastIndex)
}

private fun SnapshotStateList<SampleRoute>.resetTo(vararg routes: SampleRoute) {
  clear()
  addAll(routes)
}

private fun SnapshotStateList<SampleRoute>.showDetail(itemId: String) {
  removeAll { it is SampleRoute.PaneDetail || it is SampleRoute.PaneProfile }
  add(SampleRoute.PaneDetail(itemId))
}

private fun SnapshotStateList<SampleRoute>.resetToListDetail(itemId: String) {
  resetTo(SampleRoute.PaneList, SampleRoute.PaneDetail(itemId))
}

private fun SnapshotStateList<SampleRoute>.popToPaneList() {
  if (size > 2) {
    removeAt(lastIndex)
  } else if (size == 2 && last() is SampleRoute.PaneDetail) {
    removeAt(lastIndex)
  }
}

private fun SnapshotStateList<SampleRoute>.pushStackDetail(stack: DemoStack) {
  add(SampleRoute.StackDetail(stack, "${stack.routeName}-$size"))
}

private fun SnapshotStateMap<DemoStack, SnapshotStateList<SampleRoute>>.resetRetainedStacks() {
  DemoStack.entries.forEach { stack -> this[stack]?.resetTo(SampleRoute.StackRoot(stack)) }
}

private fun routeName(route: SampleRoute): String =
  when (route) {
    SampleRoute.ParityHome -> "/nav3/parity/home"
    is SampleRoute.ParityDetail -> "/nav3/parity/detail"
    is SampleRoute.ParityDialog -> "/nav3/parity/dialog"
    SampleRoute.PaneList -> "/nav3/multipane/list"
    is SampleRoute.PaneDetail -> "/nav3/multipane/detail"
    SampleRoute.PaneProfile -> "/nav3/multipane/profile"
    is SampleRoute.StackRoot -> "/nav3/stacks/${route.stack.routeName}"
    is SampleRoute.StackDetail -> "/nav3/stacks/${route.stack.routeName}/detail"
  }

private fun routeArguments(route: SampleRoute): Map<String, Any?> =
  when (route) {
    SampleRoute.ParityHome -> emptyMap()
    is SampleRoute.ParityDetail -> mapOf("item_id" to route.itemId)
    is SampleRoute.ParityDialog -> mapOf("dialog_kind" to route.dialogKind)
    SampleRoute.PaneList -> emptyMap()
    is SampleRoute.PaneDetail -> mapOf("item_id" to route.itemId)
    SampleRoute.PaneProfile -> mapOf("section" to "profile")
    is SampleRoute.StackRoot -> mapOf("tab" to route.stack.routeName)
    is SampleRoute.StackDetail -> mapOf("tab" to route.stack.routeName, "item_id" to route.itemId)
  }
