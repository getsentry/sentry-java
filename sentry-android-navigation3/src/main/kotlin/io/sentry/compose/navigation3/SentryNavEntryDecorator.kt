package io.sentry.compose.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.ITransaction
import io.sentry.ScopesAdapter
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryLevel.INFO
import io.sentry.SentryLevel.WARNING
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.TypeCheckHint
import io.sentry.protocol.App
import io.sentry.protocol.TransactionNameSource
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion
import io.sentry.util.TracingUtils
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental public const val NAVIGATION_OP: String = "navigation"

/** Metadata key used by Nav3 list-detail scene entries for the detail pane. */
internal const val NAV3_METADATA_LIST_DETAIL_PANE: String = "listDetailPane"

/** Metadata value for a list pane in list-detail layouts. */
internal const val NAV3_PANE_LIST: String = "list"

/** Metadata value for a detail pane in list-detail layouts. */
internal const val NAV3_PANE_DETAIL: String = "detail"

/** Key under which the navigation backstack/visible state is attached to the Sentry scope. */
private const val NAVIGATION_CONTEXT_KEY = "navigation"

private const val DEFAULT_STACK_NAME = "default"

private const val TRACE_ORIGIN = "auto.navigation.nav3"

/** A Nav3 entry currently visible to the user and available for primary-route selection. */
@ApiStatus.Experimental
public data class SentryNavVisibleEntry<T : Any>(
  public val key: T,
  public val route: String,
  public val stack: String?,
  public val metadata: Map<String, Any>,
)

private data class MultiStackNavigationSnapshot<S : Any, T : Any>(
  val selectedStack: S,
  val backStacks: Map<S, List<T>>,
  val stacksInUse: Set<S>,
)

/**
 * Effect-only composable that captures navigation breadcrumbs, starts idle transactions, tracks
 * screen names, and attaches backstack context for Sentry.
 *
 * This is the recommended integration for single-stack Nav3 apps and is the Nav3 equivalent of
 * `NavHostController.withSentryObservableEffect()` from the Nav2 integration. It does not return a
 * [NavEntryDecorator] and does not need to be passed to `NavDisplay`.
 *
 * **Composition lifecycle:** call this from a composable that stays in the composition tree for the
 * full navigation session — the same level as `NavDisplay`, not inside a single screen. When this
 * composable leaves the tree, the active transaction is finished and navigation is no longer
 * observed. Placing the call too deep in the tree only ends tracing early.
 *
 * **Rapid navigation:** if several backstack changes occur within the same composition frame, only
 * the final state produces a breadcrumb and transaction. Intermediate destinations may be skipped.
 *
 * **Multipane (e.g. list-detail):** for layouts where multiple entries are composed simultaneously,
 * use [rememberSentryNavStateHolder] with [rememberSentryNavEntryDecorator] instead.
 *
 * **Known limitation — dialog routes:** Nav3 puts dialog entries on the back stack as regular
 * entries. They will fire breadcrumbs and transactions and may overwrite `scope.screen`. There is
 * no built-in filter; this is documented behavior.
 *
 * @param backStack The navigation backstack to observe.
 * @param scopes The Sentry scopes instance.
 * @param enableNavigationBreadcrumbs Whether to capture breadcrumbs for navigation events.
 * @param enableNavigationTracing Whether to start idle transactions for navigation events.
 * @param enableBackstackContext Whether to attach the backstack to the Sentry scope as context.
 * @param maxBackstackSize Maximum number of backstack entries to include in crash context.
 * @param nameExtractor Optional lambda to extract a human-readable route name from a backstack key.
 *   If not provided, defaults to the key’s class simple name.
 * @param argumentsExtractor Optional lambda to extract arguments from a backstack key. If not
 *   provided, no arguments are attached. Values should be primitives (`String`, `Number`,
 *   `Boolean`), `null`, or nested `Map`/`Collection` thereof. Non-primitive values are coerced to
 *   their `toString()` representation with a warning logged.
 *
 *   **Privacy:** the values you return here are attached verbatim to breadcrumbs,
 *   `contexts.navigation`, and the navigation transaction, and are sent to Sentry as-is. They are
 *   **not** filtered by `SentryOptions.isSendDefaultPii()` and are **not** run through any
 *   automatic PII scrubber. Navigation route arguments frequently contain PII or secrets (user IDs,
 *   email addresses, auth tokens or query params from deep links). Only return values that are safe
 *   to send, and scrub or redact anything sensitive in this lambda (or via `beforeBreadcrumb` /
 *   `beforeSend`). Leaving this parameter unset attaches no arguments at all.
 */
@Suppress("LongParameterList", "FunctionNaming")
@ApiStatus.Experimental
@Composable
@NonRestartableComposable
public fun <T : Any> SentryNav3NavigationEffect(
  backStack: SnapshotStateList<T>,
  scopes: IScopes = ScopesAdapter.getInstance(),
  enableNavigationBreadcrumbs: Boolean = true,
  enableNavigationTracing: Boolean = true,
  enableBackstackContext: Boolean = true,
  maxBackstackSize: Int = 30,
  nameExtractor: ((T) -> String)? = null,
  argumentsExtractor: ((T) -> Map<String, Any?>)? = null,
) {
  require(maxBackstackSize > 0) { "maxBackstackSize must be positive, was $maxBackstackSize" }

  val stateHolder =
    remember(
      backStack,
      scopes,
      enableNavigationBreadcrumbs,
      enableNavigationTracing,
      enableBackstackContext,
      maxBackstackSize,
    ) {
      SentryNavStateHolder(
        scopes = scopes,
        enableNavigationBreadcrumbs = enableNavigationBreadcrumbs,
        enableNavigationTracing = enableNavigationTracing,
        enableBackstackContext = enableBackstackContext,
        maxBackstackSize = maxBackstackSize,
        nameExtractor = nameExtractor,
        argumentsExtractor = argumentsExtractor,
        primaryRouteSelector = null,
      )
    }

  stateHolder.nameExtractor = nameExtractor
  stateHolder.argumentsExtractor = argumentsExtractor

  DisposableEffect(stateHolder) { onDispose { stateHolder.cleanup() } }

  LaunchedEffect(stateHolder) {
    snapshotFlow { backStack.toList() }
      .distinctUntilChanged()
      .collectLatest { snapshot -> stateHolder.onBackstackChanged(snapshot) }
  }
}

/**
 * Effect-only composable that observes app-owned multiple retained Navigation 3 back stacks.
 *
 * Use this for Navigation 3 patterns such as bottom navigation where the app owns a selected stack
 * and keeps inactive stacks retained in memory. Sentry writes every retained stack to
 * `contexts.navigation.backstacks`, records [selectedStack] as `selected_stack`, and records
 * [stacksInUse] separately so rendered stacks can be distinguished from retained background stacks.
 *
 * Switching [selectedStack] is treated as user-visible navigation: Sentry can emit a breadcrumb,
 * update `scope.screen`, and start a navigation transaction for the selected stack's top route.
 * Mutating an inactive retained stack only refreshes crash context.
 *
 * @param selectedStack The currently selected stack key.
 * @param backStacks Retained back stack snapshots keyed by stack.
 * @param stacksInUse Stack keys currently rendered by the UI. Defaults to only [selectedStack].
 * @param scopes The Sentry scopes instance.
 * @param enableNavigationBreadcrumbs Whether to capture breadcrumbs for navigation events.
 * @param enableNavigationTracing Whether to start idle transactions for navigation events.
 * @param enableBackstackContext Whether to attach the backstacks to the Sentry scope as context.
 * @param maxBackstackSize Maximum number of entries to include for each retained stack.
 * @param stackNameExtractor Optional lambda to extract a stable readable stack name. String stack
 *   keys use their own value by default; other keys default to their class simple name.
 * @param nameExtractor Optional lambda to extract a human-readable route name from a backstack key.
 * @param argumentsExtractor Optional lambda to extract route arguments. Extracted values are sent
 *   to Sentry as-is and are not gated by `SentryOptions.isSendDefaultPii()`.
 * @param primaryRouteSelector Optional callback that can choose which visible entry should drive
 *   `scope.screen`, breadcrumbs, and navigation transaction names. Return one of the provided
 *   entries. When unset or inconclusive, Sentry prefers a visible entry from [selectedStack] and
 *   then falls back to built-in multipane metadata heuristics.
 */
@Suppress("LongParameterList", "FunctionNaming")
@ApiStatus.Experimental
@Composable
@NonRestartableComposable
public fun <S : Any, T : Any> SentryNav3NavigationEffect(
  selectedStack: S,
  backStacks: Map<S, List<T>>,
  stacksInUse: Set<S> = setOf(selectedStack),
  scopes: IScopes = ScopesAdapter.getInstance(),
  enableNavigationBreadcrumbs: Boolean = true,
  enableNavigationTracing: Boolean = true,
  enableBackstackContext: Boolean = true,
  maxBackstackSize: Int = 30,
  stackNameExtractor: ((S) -> String)? = null,
  nameExtractor: ((T) -> String)? = null,
  argumentsExtractor: ((T) -> Map<String, Any?>)? = null,
  primaryRouteSelector: ((List<SentryNavVisibleEntry<T>>) -> SentryNavVisibleEntry<T>?)? = null,
) {
  require(maxBackstackSize > 0) { "maxBackstackSize must be positive, was $maxBackstackSize" }

  val stateHolder =
    remember(
      scopes,
      enableNavigationBreadcrumbs,
      enableNavigationTracing,
      enableBackstackContext,
      maxBackstackSize,
    ) {
      SentryNavStateHolder(
        scopes = scopes,
        enableNavigationBreadcrumbs = enableNavigationBreadcrumbs,
        enableNavigationTracing = enableNavigationTracing,
        enableBackstackContext = enableBackstackContext,
        maxBackstackSize = maxBackstackSize,
        nameExtractor = nameExtractor,
        argumentsExtractor = argumentsExtractor,
        primaryRouteSelector = primaryRouteSelector,
      )
    }

  stateHolder.nameExtractor = nameExtractor
  stateHolder.argumentsExtractor = argumentsExtractor
  stateHolder.primaryRouteSelector = primaryRouteSelector

  DisposableEffect(stateHolder) { onDispose { stateHolder.cleanup() } }

  LaunchedEffect(stateHolder, selectedStack, backStacks, stacksInUse, stackNameExtractor) {
    snapshotFlow {
        MultiStackNavigationSnapshot(
          selectedStack = selectedStack,
          backStacks = backStacks.entries.associate { it.key to it.value.toList() },
          stacksInUse = stacksInUse.toSet(),
        )
      }
      .distinctUntilChanged()
      .collectLatest { snapshot ->
        stateHolder.onBackstacksChanged(
          selectedStack = snapshot.selectedStack,
          backStacks = snapshot.backStacks,
          stacksInUse = snapshot.stacksInUse,
          stackNameExtractor = stackNameExtractor,
        )
      }
  }
}

/**
 * Variant of [SentryNav3NavigationEffect] that wires a pre-created [SentryNavStateHolder] to a
 * backstack. Use this for multipane layouts where the holder is shared across panes.
 *
 * @param backStack The navigation backstack to observe.
 * @param holder The [SentryNavStateHolder] created via [rememberSentryNavStateHolder].
 */
@Suppress("FunctionNaming")
@ApiStatus.Experimental
@Composable
@NonRestartableComposable
public fun <T : Any> SentryNav3NavigationEffect(
  backStack: SnapshotStateList<T>,
  holder: SentryNavStateHolder<T>,
) {
  LaunchedEffect(backStack, holder) {
    snapshotFlow { backStack.toList() }
      .distinctUntilChanged()
      .collectLatest { snapshot -> holder.onBackstackChanged(snapshot) }
  }
}

/**
 * Creates and remembers a [SentryNavStateHolder] for use with [rememberSentryNavEntryDecorator] in
 * multipane (list-detail) layouts. The holder manages all Sentry navigation state and can be shared
 * across multiple panes.
 *
 * Wire the backstack separately via [SentryNav3NavigationEffect]:
 * ```
 * val holder = rememberSentryNavStateHolder<MyKey>(...)
 * val decorator = rememberSentryNavEntryDecorator(holder)
 * SentryNav3NavigationEffect(backStack = myBackStack, holder = holder)
 * NavDisplay(backStack = myBackStack, entryDecorators = listOf(decorator), ...)
 * ```
 *
 * For single-stack apps, prefer [SentryNav3NavigationEffect] instead.
 *
 * @param scopes The Sentry scopes instance.
 * @param enableNavigationBreadcrumbs Whether to capture breadcrumbs for navigation events.
 * @param enableNavigationTracing Whether to start idle transactions for navigation events.
 * @param enableBackstackContext Whether to attach the backstack to the Sentry scope as context.
 * @param maxBackstackSize Maximum number of backstack entries to include in crash context.
 * @param nameExtractor Optional lambda to extract a human-readable route name from a backstack key.
 * @param argumentsExtractor Optional lambda to extract arguments from a backstack key.
 * @param primaryRouteSelector Optional callback that can choose which visible entry should drive
 *   `scope.screen`, breadcrumbs, and navigation transaction names. Return one of the provided
 *   entries. When unset or inconclusive, Sentry prefers a visible entry from the selected stack and
 *   then falls back to built-in multipane metadata heuristics.
 *
 *   **Privacy:** extracted argument values are sent to Sentry as-is via breadcrumbs,
 *   `contexts.navigation`, and the navigation transaction. They are **not** gated by
 *   `SentryOptions.isSendDefaultPii()` and are **not** automatically PII-scrubbed. Route arguments
 *   commonly contain PII or secrets; only return values safe to send, and redact sensitive data in
 *   this lambda (or via `beforeBreadcrumb` / `beforeSend`). Leaving this unset attaches no
 *   arguments.
 */
@Suppress("LongParameterList")
@ApiStatus.Experimental
@Composable
public fun <T : Any> rememberSentryNavStateHolder(
  scopes: IScopes = ScopesAdapter.getInstance(),
  enableNavigationBreadcrumbs: Boolean = true,
  enableNavigationTracing: Boolean = true,
  enableBackstackContext: Boolean = true,
  maxBackstackSize: Int = 30,
  nameExtractor: ((T) -> String)? = null,
  argumentsExtractor: ((T) -> Map<String, Any?>)? = null,
  primaryRouteSelector: ((List<SentryNavVisibleEntry<T>>) -> SentryNavVisibleEntry<T>?)? = null,
): SentryNavStateHolder<T> {
  require(maxBackstackSize > 0) { "maxBackstackSize must be positive, was $maxBackstackSize" }

  val holder =
    remember(
      scopes,
      enableNavigationBreadcrumbs,
      enableNavigationTracing,
      enableBackstackContext,
      maxBackstackSize,
    ) {
      SentryNavStateHolder(
        scopes = scopes,
        enableNavigationBreadcrumbs = enableNavigationBreadcrumbs,
        enableNavigationTracing = enableNavigationTracing,
        enableBackstackContext = enableBackstackContext,
        maxBackstackSize = maxBackstackSize,
        nameExtractor = nameExtractor,
        argumentsExtractor = argumentsExtractor,
        primaryRouteSelector = primaryRouteSelector,
      )
    }

  holder.nameExtractor = nameExtractor
  holder.argumentsExtractor = argumentsExtractor
  holder.primaryRouteSelector = primaryRouteSelector

  DisposableEffect(holder) { onDispose { holder.cleanup() } }

  return holder
}

/**
 * Creates a [NavEntryDecorator] from a pre-created [SentryNavStateHolder]. Use this for multipane
 * layouts where the holder is shared across panes.
 *
 * The backstack observer must be wired separately via [SentryNav3NavigationEffect]:
 * ```
 * val holder = rememberSentryNavStateHolder<MyKey>(...)
 * val decorator = rememberSentryNavEntryDecorator(holder)
 * SentryNav3NavigationEffect(backStack = myBackStack, holder = holder)
 * ```
 *
 * @param holder The [SentryNavStateHolder] to receive visibility events from this decorator.
 */
@ApiStatus.Experimental
@Composable
@NonRestartableComposable
public fun <T : Any> rememberSentryNavEntryDecorator(
  holder: SentryNavStateHolder<T>
): NavEntryDecorator<T> {
  return remember(holder) {
    NavEntryDecorator(
      decorate = { entry -> SentryNavEntryObservation(entry = entry, stateHolder = holder) },
      onPop = { contentKey -> holder.onEntryPopped(contentKey) },
    )
  }
}

/**
 * Creates a [NavEntryDecorator] that captures navigation breadcrumbs, starts idle transactions,
 * tracks screen names, and attaches backstack context for Sentry.
 *
 * **Composition lifecycle:** invoke this from a composable that stays in the composition tree for
 * as long as [backStack] is the live navigation state — for example the same composable that holds
 * the back stack and [androidx.navigation3.runtime.NavDisplay]. Do not call it from a destination
 * or nested subtree that leaves composition when the user navigates (for example only inside a
 * single screen’s content). When this composable leaves the tree, disposal finishes the active
 * navigation transaction and clears it from the scope, and navigation is no longer observed. That
 * is correct teardown, not a leak; placing the call too low in the tree only ends tracing early.
 *
 * **Single-stack apps:** pass the decorator to `NavDisplay`’s `entryDecorators`. Navigation events
 * are driven by composed entries; the [backStack] observer keeps crash backstack context in sync.
 *
 * **Multipane (e.g. list-detail):** when multiple entries are composed at once, Sentry sets
 * `contexts.app.view_names` to every visible route, uses the detail pane (when metadata marks it)
 * as the primary route for `scope.screen` and performance transactions, and adds a
 * `visible_entries` array to `contexts.navigation`.
 *
 * **Rapid navigation:** if several backstack changes occur within the same composition frame, only
 * the final state produces a breadcrumb and transaction. Intermediate destinations may be skipped.
 *
 * The returned [NavEntryDecorator] must be passed to `NavDisplay`’s `entryDecorators`.
 *
 * @param backStack The navigation backstack to observe.
 * @param scopes The Sentry scopes instance.
 * @param enableNavigationBreadcrumbs Whether to capture breadcrumbs for navigation events.
 * @param enableNavigationTracing Whether to start idle transactions for navigation events.
 * @param enableBackstackContext Whether to attach the backstack to the Sentry scope as context.
 * @param maxBackstackSize Maximum number of backstack entries to include in crash context.
 * @param nameExtractor Optional lambda to extract a human-readable route name from a backstack key.
 *   If not provided, defaults to the key’s class simple name.
 * @param argumentsExtractor Optional lambda to extract arguments from a backstack key. If not
 *   provided, no arguments are attached. Values should be primitives (`String`, `Number`,
 *   `Boolean`), `null`, or nested `Map`/`Collection` thereof. Non-primitive values are coerced to
 *   their `toString()` representation with a warning logged.
 * @param primaryRouteSelector Optional callback that can choose which visible entry should drive
 *   `scope.screen`, breadcrumbs, and navigation transaction names. Return one of the provided
 *   entries. When unset or inconclusive, Sentry prefers a visible entry from the selected stack and
 *   then falls back to built-in multipane metadata heuristics.
 *
 *   **Privacy:** the values you return here are attached verbatim to breadcrumbs,
 *   `contexts.navigation`, and the navigation transaction, and are sent to Sentry as-is. They are
 *   **not** filtered by `SentryOptions.isSendDefaultPii()` and are **not** run through any
 *   automatic PII scrubber. Navigation route arguments frequently contain PII or secrets (user IDs,
 *   email addresses, auth tokens or query params from deep links). Only return values that are safe
 *   to send, and scrub or redact anything sensitive in this lambda (or via `beforeBreadcrumb` /
 *   `beforeSend`). Leaving this parameter unset attaches no arguments at all.
 */
@Suppress("LongParameterList")
@ApiStatus.Experimental
@Composable
@NonRestartableComposable
public fun <T : Any> rememberSentryNavEntryDecorator(
  backStack: SnapshotStateList<T>,
  scopes: IScopes = ScopesAdapter.getInstance(),
  enableNavigationBreadcrumbs: Boolean = true,
  enableNavigationTracing: Boolean = true,
  enableBackstackContext: Boolean = true,
  maxBackstackSize: Int = 30,
  nameExtractor: ((T) -> String)? = null,
  argumentsExtractor: ((T) -> Map<String, Any?>)? = null,
  primaryRouteSelector: ((List<SentryNavVisibleEntry<T>>) -> SentryNavVisibleEntry<T>?)? = null,
): NavEntryDecorator<T> {
  require(maxBackstackSize > 0) { "maxBackstackSize must be positive, was $maxBackstackSize" }

  val stateHolder =
    remember(
      backStack,
      scopes,
      enableNavigationBreadcrumbs,
      enableNavigationTracing,
      enableBackstackContext,
      maxBackstackSize,
    ) {
      SentryNavStateHolder(
        scopes = scopes,
        enableNavigationBreadcrumbs = enableNavigationBreadcrumbs,
        enableNavigationTracing = enableNavigationTracing,
        enableBackstackContext = enableBackstackContext,
        maxBackstackSize = maxBackstackSize,
        nameExtractor = nameExtractor,
        argumentsExtractor = argumentsExtractor,
        primaryRouteSelector = primaryRouteSelector,
      )
    }

  stateHolder.nameExtractor = nameExtractor
  stateHolder.argumentsExtractor = argumentsExtractor
  stateHolder.primaryRouteSelector = primaryRouteSelector

  DisposableEffect(stateHolder) { onDispose { stateHolder.cleanup() } }

  LaunchedEffect(stateHolder) {
    snapshotFlow { backStack.toList() }
      .distinctUntilChanged()
      .collectLatest { snapshot -> stateHolder.onBackstackChanged(snapshot) }
  }

  return remember(stateHolder) {
    NavEntryDecorator(
      decorate = { entry -> SentryNavEntryObservation(entry = entry, stateHolder = stateHolder) },
      onPop = { contentKey -> stateHolder.onEntryPopped(contentKey) },
    )
  }
}

@Suppress("FunctionNaming")
@Composable
private fun <T : Any> SentryNavEntryObservation(
  entry: NavEntry<T>,
  stateHolder: SentryNavStateHolder<T>,
) {
  DisposableEffect(entry.contentKey) {
    stateHolder.onEntryVisible(entry.contentKey, entry.metadata)
    onDispose { stateHolder.onEntryHidden(entry.contentKey) }
  }
  entry.Content()
}

/** A Nav3 entry that is currently composed and visible to the user. */
internal data class VisiblePane<T : Any>(
  val key: T,
  val contentKey: Any,
  val metadata: Map<String, Any>,
  val stackName: String?,
)

private data class ResolvedVisibleKey<T : Any>(val key: T, val stackName: String?)

private data class RetainedStack<T : Any>(
  val name: String,
  val selected: Boolean,
  val inUse: Boolean,
  val backStack: List<T>,
)

@Suppress("LongParameterList", "TooManyFunctions")
@ApiStatus.Experimental
/**
 * Holds Sentry navigation state for a Nav3 back stack.
 *
 * This type is not thread-safe. Call its methods from the same UI thread/composition context that
 * owns the Nav3 back stack.
 */
public class SentryNavStateHolder<T : Any>
@ApiStatus.Internal
constructor(
  private val scopes: IScopes,
  private val enableNavigationBreadcrumbs: Boolean,
  private val enableNavigationTracing: Boolean,
  private val enableBackstackContext: Boolean,
  private val maxBackstackSize: Int,
  nameExtractor: ((T) -> String)?,
  argumentsExtractor: ((T) -> Map<String, Any?>)?,
  primaryRouteSelector: ((List<SentryNavVisibleEntry<T>>) -> SentryNavVisibleEntry<T>?)?,
) {
  internal var nameExtractor: ((T) -> String)? = nameExtractor
  internal var argumentsExtractor: ((T) -> Map<String, Any?>)? = argumentsExtractor
  internal var primaryRouteSelector:
    ((List<SentryNavVisibleEntry<T>>) -> SentryNavVisibleEntry<T>?)? =
    primaryRouteSelector

  private var currentBackStack: List<T> = emptyList()
  private var currentRetainedStacks: List<RetainedStack<T>> = emptyList()
  private var currentSelectedStackName: String? = null
  private var currentStacksInUseNames: List<String> = emptyList()
  private val visiblePanes = LinkedHashMap<Any, VisiblePane<T>>()
  private var previousPrimaryKeyRef: WeakReference<T>? = null
  private var previousPrimaryStackName: String? = null
  private var activeTransaction: ITransaction? = null

  private val isPerformanceEnabled
    get() = scopes.options.isTracingEnabled && enableNavigationTracing

  public companion object {
    init {
      addIntegrationToSdkVersion("Navigation3")
      SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-android-navigation3", BuildConfig.VERSION_NAME)
    }
  }

  /**
   * Runs an instrumentation callback, swallowing any [Throwable] so a misbehaving host key type
   * (e.g. a backstack key whose `equals`/`hashCode`/`toString` throws) can never crash the host
   * app. These callbacks run inside Compose effects/coroutines and invoke user-defined key methods
   * (via map lookups, equality checks and string coercion) outside the extractor-lambda guards, so
   * they need their own outermost safety net.
   */
  @Suppress("TooGenericExceptionCaught") // SDK instrumentation must never crash the host app
  private inline fun guard(operation: String, body: () -> Unit) {
    try {
      body()
    } catch (t: Throwable) {
      scopes.options.logger.log(
        WARNING,
        t,
        "Nav3 instrumentation failed during %s (possibly a host key type whose equals/hashCode/" +
          "toString threw). Skipping this navigation update.",
        operation,
      )
    }
  }

  /** Updates crash backstack context from the live back stack. */
  @ApiStatus.Internal
  public fun onBackstackChanged(backStack: List<T>): Unit =
    guard("onBackstackChanged") {
      currentBackStack = backStack
      currentSelectedStackName = if (backStack.isEmpty()) null else DEFAULT_STACK_NAME
      currentStacksInUseNames = if (backStack.isEmpty()) emptyList() else listOf(DEFAULT_STACK_NAME)
      currentRetainedStacks =
        if (backStack.isEmpty()) {
          emptyList()
        } else {
          listOf(
            RetainedStack(
              name = DEFAULT_STACK_NAME,
              selected = true,
              inUse = true,
              backStack = backStack,
            )
          )
        }

      if (backStack.isEmpty()) {
        if (enableBackstackContext) {
          scopes.configureScope { scope -> scope.removeContexts(NAVIGATION_CONTEXT_KEY) }
        }
        if (visiblePanes.isEmpty()) {
          applyPrimaryChange(null)
        }
        return@guard
      }

      updateNavigationContext()

      if (visiblePanes.isEmpty()) {
        applyPrimaryChange(backStack.lastOrNull(), DEFAULT_STACK_NAME)
      }
    }

  /** Updates crash backstack context from app-owned retained back stacks. */
  internal fun <S : Any> onBackstacksChanged(
    selectedStack: S,
    backStacks: Map<S, List<T>>,
    stacksInUse: Set<S>,
    stackNameExtractor: ((S) -> String)?,
  ): Unit =
    guard("onBackstacksChanged") {
      val selectedStackName = resolveStackName(selectedStack, stackNameExtractor)
      val inUseNames = stacksInUse.map { resolveStackName(it, stackNameExtractor) }

      currentSelectedStackName = selectedStackName
      currentStacksInUseNames = inUseNames
      currentBackStack = backStacks[selectedStack].orEmpty()
      currentRetainedStacks = backStacks.map { (stackKey, stackBackStack) ->
        RetainedStack(
          name = resolveStackName(stackKey, stackNameExtractor),
          selected = stackKey == selectedStack,
          inUse = stacksInUse.any { it == stackKey },
          backStack = stackBackStack,
        )
      }
      refreshVisiblePaneOwnership()

      updateNavigationContext()

      if (visiblePanes.isEmpty()) {
        applyPrimaryChange(currentBackStack.lastOrNull(), selectedStackName)
      } else {
        applyPrimaryPane(selectPrimaryPane(visiblePanes.values))
      }
    }

  @ApiStatus.Internal
  public fun onEntryVisible(contentKey: Any, metadata: Map<String, Any>): Unit =
    guard("onEntryVisible") {
      val visibleKey = resolveVisibleKey(contentKey) ?: return@guard
      visiblePanes[contentKey] =
        VisiblePane(
          key = visibleKey.key,
          contentKey = contentKey,
          metadata = metadata,
          stackName = visibleKey.stackName,
        )
      updateNavigationContext()
      applyPrimaryPane(selectPrimaryPane(visiblePanes.values))
    }

  @ApiStatus.Internal
  public fun onEntryHidden(contentKey: Any): Unit =
    guard("onEntryHidden") {
      visiblePanes.remove(contentKey)
      updateNavigationContext()
      if (visiblePanes.isEmpty()) {
        applyPrimaryChange(currentBackStack.lastOrNull())
      } else {
        applyPrimaryPane(selectPrimaryPane(visiblePanes.values))
      }
    }

  @ApiStatus.Internal
  public fun onEntryPopped(contentKey: Any): Unit =
    guard("onEntryPopped") {
      visiblePanes.remove(contentKey)
      updateNavigationContext()
      if (visiblePanes.isNotEmpty()) {
        applyPrimaryPane(selectPrimaryPane(visiblePanes.values))
      }
    }

  private fun applyPrimaryPane(pane: VisiblePane<T>?) {
    applyPrimaryChange(
      primaryKey = pane?.key,
      primaryStackName = pane?.stackName ?: currentSelectedStackName ?: DEFAULT_STACK_NAME,
    )
  }

  private fun applyPrimaryChange(
    primaryKey: T?,
    primaryStackName: String = currentSelectedStackName ?: DEFAULT_STACK_NAME,
  ) {
    if (primaryKey == null) {
      return
    }

    val previousPrimaryKey = previousPrimaryKeyRef?.get()
    if (
      previousPrimaryKey != null &&
        previousPrimaryKey == primaryKey &&
        previousPrimaryStackName == primaryStackName
    ) {
      updateScreenTracking(primaryKey)
      return
    }

    val routeName = resolveRouteName(primaryKey)
    val arguments = resolveArguments(primaryKey)

    addBreadcrumb(previousPrimaryKey, primaryKey, routeName, arguments)
    updateScreenTracking(primaryKey)
    startTracing(routeName, arguments)
    previousPrimaryKeyRef = WeakReference(primaryKey)
    previousPrimaryStackName = primaryStackName
  }

  private fun updateScreenTracking(primaryKey: T) {
    if (!scopes.options.isEnableScreenTracking) {
      return
    }

    val primaryRoute = resolveRouteName(primaryKey)
    val viewNames =
      if (visiblePanes.isEmpty()) {
        listOf(primaryRoute)
      } else {
        visiblePanes.values.map { resolveRouteName(it.key) }
      }

    scopes.configureScope { scope ->
      scope.screen = primaryRoute
      val contexts = scope.contexts ?: return@configureScope
      val app = contexts.app ?: App().also { contexts.setApp(it) }
      app.viewNames = viewNames
    }
  }

  private fun addBreadcrumb(
    fromKey: T?,
    toKey: T,
    routeName: String,
    arguments: Map<String, Any?>,
  ) {
    if (!enableNavigationBreadcrumbs) return

    val breadcrumb =
      Breadcrumb().apply {
        type = NAVIGATION_OP
        category = NAVIGATION_OP

        fromKey?.let { prevKey ->
          data["from"] = resolveRouteName(prevKey)
          val fromArgs = resolveArguments(prevKey)
          if (fromArgs.isNotEmpty()) {
            data["from_arguments"] = fromArgs
          }
        }

        data["to"] = routeName
        if (arguments.isNotEmpty()) {
          data["to_arguments"] = arguments
        }

        if (visiblePanes.size > 1) {
          data["visible"] = visiblePanes.values.map { resolveRouteName(it.key) }
        }

        level = INFO
      }

    val hint = Hint()
    hint.set(TypeCheckHint.NAV3_DESTINATION, toKey)
    scopes.addBreadcrumb(breadcrumb, hint)
  }

  private fun startTracing(routeName: String, arguments: Map<String, Any?>) {
    if (!isPerformanceEnabled) {
      TracingUtils.startNewTrace(scopes)
      return
    }

    if (activeTransaction != null) {
      stopTracing()
    }

    val transactionOptions =
      TransactionOptions().also {
        it.isWaitForChildren = true
        it.idleTimeout = scopes.options.idleTimeout

        val deadlineTimeoutMillis = scopes.options.deadlineTimeout
        it.deadlineTimeout = if (deadlineTimeoutMillis <= 0) null else deadlineTimeoutMillis

        it.isTrimEnd = true
      }

    val transaction =
      scopes.startTransaction(
        TransactionContext(routeName, TransactionNameSource.ROUTE, NAVIGATION_OP),
        transactionOptions,
      )

    // Track the transaction immediately so a later failure (e.g. while attaching data or
    // configuring the scope) can never orphan a started-but-unfinished transaction; stopTracing /
    // cleanup rely on activeTransaction to finish it.
    activeTransaction = transaction

    transaction.spanContext.origin = TRACE_ORIGIN

    if (arguments.isNotEmpty()) {
      transaction.setData("arguments", arguments)
    }

    scopes.configureScope { scope ->
      scope.withTransaction { tx ->
        if (tx == null) {
          scope.transaction = transaction
        }
      }
    }
  }

  @ApiStatus.Internal
  public fun cleanup() {
    if (activeTransaction != null) {
      stopTracing()
    }
    visiblePanes.clear()
    currentBackStack = emptyList()
    currentRetainedStacks = emptyList()
    currentSelectedStackName = null
    currentStacksInUseNames = emptyList()
    previousPrimaryKeyRef = null
    previousPrimaryStackName = null
    if (enableBackstackContext) {
      scopes.configureScope { scope -> scope.removeContexts(NAVIGATION_CONTEXT_KEY) }
    }
  }

  private fun stopTracing() {
    val status = activeTransaction?.status ?: SpanStatus.OK
    activeTransaction?.finish(status)

    scopes.configureScope { scope ->
      scope.withTransaction { tx ->
        if (tx == activeTransaction) {
          scope.clearTransaction()
        }
      }
    }

    activeTransaction = null
  }

  private fun updateNavigationContext() {
    if (!enableBackstackContext) return

    val context = LinkedHashMap<String, Any?>()

    if (currentRetainedStacks.isNotEmpty()) {
      context["selected_stack"] = currentSelectedStackName
      context["stacks_in_use"] = currentStacksInUseNames
      context["backstacks"] = currentRetainedStacks.map { stack ->
        mapOf(
          "name" to stack.name,
          "selected" to stack.selected,
          "in_use" to stack.inUse,
          "backstack" to buildRouteEntries(stack.backStack),
        )
      }
    }

    if (visiblePanes.isNotEmpty()) {
      context["visible_entries"] = buildVisibleRouteEntries(visiblePanes.values.toList())
    }

    if (context.isEmpty()) {
      scopes.configureScope { scope -> scope.removeContexts(NAVIGATION_CONTEXT_KEY) }
    } else {
      scopes.configureScope { scope -> scope.setContexts(NAVIGATION_CONTEXT_KEY, context) }
    }
  }

  private fun buildRouteEntries(keys: List<T>): List<Map<String, Any?>> {
    return keys.takeLast(maxBackstackSize).map { key ->
      buildMap {
        put("route", resolveRouteName(key))
        val args = resolveArguments(key)
        if (args.isNotEmpty()) {
          put("args", args)
        }
      }
    }
  }

  private fun buildVisibleRouteEntries(panes: List<VisiblePane<T>>): List<Map<String, Any?>> {
    return panes.takeLast(maxBackstackSize).map { pane ->
      buildMap {
        pane.stackName?.let { put("stack", it) }
        put("route", resolveRouteName(pane.key))
        val args = resolveArguments(pane.key)
        if (args.isNotEmpty()) {
          put("args", args)
        }
      }
    }
  }

  internal fun resolveKey(contentKey: Any): T? {
    return resolveVisibleKey(contentKey)?.key
  }

  private fun resolveVisibleKey(contentKey: Any): ResolvedVisibleKey<T>? {
    if (currentRetainedStacks.isEmpty()) {
      return currentBackStack
        .find { key -> contentKeyMatches(key, contentKey) }
        ?.let { key ->
          ResolvedVisibleKey(key, currentSelectedStackName)
        }
    }

    val matches = mutableListOf<ResolvedVisibleKey<T>>()
    val candidateStacks =
      currentRetainedStacks.filter { it.inUse }.ifEmpty { currentRetainedStacks }
    for (stack in candidateStacks) {
      for (key in stack.backStack) {
        if (contentKeyMatches(key, contentKey)) {
          matches += ResolvedVisibleKey(key, stack.name)
        }
      }
    }

    if (matches.isEmpty()) {
      return null
    }

    val selectedMatch = matches.firstOrNull { it.stackName == currentSelectedStackName }
    if (selectedMatch != null) {
      return selectedMatch
    }

    return if (matches.size == 1) {
      matches.first()
    } else {
      // Route keys can be equal across retained stacks; keep the route but omit ambiguous
      // ownership.
      matches.first().copy(stackName = null)
    }
  }

  private fun refreshVisiblePaneOwnership() {
    if (visiblePanes.isEmpty()) {
      return
    }
    val refreshed = LinkedHashMap<Any, VisiblePane<T>>()
    for ((contentKey, pane) in visiblePanes) {
      val visibleKey = resolveVisibleKey(contentKey)
      refreshed[contentKey] =
        pane.copy(key = visibleKey?.key ?: pane.key, stackName = visibleKey?.stackName)
    }
    visiblePanes.clear()
    visiblePanes.putAll(refreshed)
  }

  @Suppress("TooGenericExceptionCaught") // SDK instrumentation must never crash the host app
  internal fun <S : Any> resolveStackName(
    stackKey: S,
    stackNameExtractor: ((S) -> String)?,
  ): String {
    val name =
      try {
        stackNameExtractor?.invoke(stackKey)
      } catch (t: Throwable) {
        scopes.options.logger.log(
          WARNING,
          "Nav3 stackNameExtractor threw while resolving a stack name. Falling back to default name.",
          t,
        )
        null
      }
        ?: when (stackKey) {
          is String -> stackKey
          else -> stackKey::class.simpleName ?: stackKey.toString()
        }
    return name.removePrefix("/")
  }

  private fun contentKeyMatches(key: T, contentKey: Any): Boolean =
    key == contentKey || key.toString() == contentKey.toString()

  internal fun selectPrimaryPane(panes: Collection<VisiblePane<T>>): VisiblePane<T>? {
    if (panes.isEmpty()) {
      return null
    }
    val paneList = panes.toList()
    val selectedByCallback = selectPrimaryPaneWithCallback(paneList)
    if (selectedByCallback != null) {
      return selectedByCallback
    }

    val candidates =
      currentSelectedStackName
        ?.let { selectedStack -> paneList.filter { it.stackName == selectedStack } }
        ?.ifEmpty { paneList } ?: paneList

    if (candidates.size == 1) {
      return candidates.first()
    }
    return candidates.maxWithOrNull(
      compareBy<VisiblePane<T>> { panePriority(it.metadata) }
        .thenBy { visiblePanes.keys.indexOf(it.contentKey) }
    )
  }

  @Suppress("TooGenericExceptionCaught") // SDK instrumentation must never crash the host app
  private fun selectPrimaryPaneWithCallback(panes: List<VisiblePane<T>>): VisiblePane<T>? {
    val selector = primaryRouteSelector ?: return null
    val entries = panes.map { it.toVisibleEntry() }
    val selectedEntry =
      try {
        selector(entries)
      } catch (t: Throwable) {
        scopes.options.logger.log(
          WARNING,
          "Nav3 primaryRouteSelector threw while selecting a visible route. Falling back to default selection.",
          t,
        )
        null
      } ?: return null

    return panes.firstOrNull { pane ->
      pane.key == selectedEntry.key && pane.stackName == selectedEntry.stack
    } ?: panes.firstOrNull { pane -> pane.key == selectedEntry.key }
  }

  private fun VisiblePane<T>.toVisibleEntry(): SentryNavVisibleEntry<T> =
    SentryNavVisibleEntry(
      key = key,
      route = resolveRouteName(key),
      stack = stackName,
      metadata = metadata,
    )

  @Suppress("ReturnCount")
  internal fun panePriority(metadata: Map<String, Any>): Int {
    val listDetailPane = metadata[NAV3_METADATA_LIST_DETAIL_PANE]?.toString()?.lowercase()
    if (listDetailPane != null) {
      return when {
        listDetailPane.contains(NAV3_PANE_DETAIL) -> 2
        listDetailPane.contains(NAV3_PANE_LIST) -> 1
        else -> 0
      }
    }
    for (value in metadata.values) {
      when (value.toString().lowercase()) {
        NAV3_PANE_DETAIL -> return 2
        "detailpane" -> return 2
        NAV3_PANE_LIST -> return 1
        "listpane" -> return 1
      }
    }
    return 0
  }

  @Suppress("TooGenericExceptionCaught") // SDK instrumentation must never crash the host app
  internal fun resolveRouteName(key: T): String {
    val name =
      try {
        nameExtractor?.invoke(key)
      } catch (t: Throwable) {
        scopes.options.logger.log(
          WARNING,
          "Nav3 nameExtractor threw while resolving a route name. Falling back to class simpleName.",
          t,
        )
        null
      } ?: key::class.simpleName ?: "unknown"
    return "/${name.removePrefix("/")}"
  }

  @Suppress("TooGenericExceptionCaught") // SDK instrumentation must never crash the host app
  private fun resolveArguments(key: T): Map<String, Any?> {
    val raw =
      try {
        argumentsExtractor?.invoke(key) ?: return emptyMap()
      } catch (t: Throwable) {
        scopes.options.logger.log(
          WARNING,
          "Nav3 argumentsExtractor threw while resolving arguments. Skipping arguments.",
          t,
        )
        return emptyMap()
      }
    return try {
      sanitizeArguments(raw)
    } catch (t: Throwable) {
      scopes.options.logger.log(
        WARNING,
        "Nav3 argument sanitization failed (possibly a cyclic or deeply nested structure). " +
          "Skipping arguments.",
        t,
      )
      emptyMap()
    }
  }

  private fun sanitizeArguments(args: Map<String, Any?>): Map<String, Any?> {
    val sanitized = LinkedHashMap<String, Any?>(args.size)
    for ((key, value) in args) {
      sanitized[key] = sanitizeValue(value)
    }
    return sanitized
  }

  private fun sanitizeValue(value: Any?): Any? {
    return when (value) {
      null,
      is String,
      is Number,
      is Boolean -> value
      is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to sanitizeValue(v) }
      is Collection<*> -> value.map { sanitizeValue(it) }
      else -> {
        scopes.options.logger.log(
          WARNING,
          "Nav3 argumentsExtractor returned non-primitive value of type %s for serialization. " +
            "Falling back to toString(). Use primitive types (String, Number, Boolean) for reliable results.",
          value::class.simpleName,
        )
        value.toString()
      }
    }
  }
}
