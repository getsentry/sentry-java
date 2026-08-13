package io.sentry.compose.navigation3

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec
import androidx.navigationevent.NavigationEvent
import io.sentry.IScopes
import io.sentry.ScopesAdapter
import org.jetbrains.annotations.ApiStatus

/**
 * A Sentry-instrumented [NavDisplay] for a single Navigation 3 back stack.
 *
 * This function mirrors the canonical single-back-stack `NavDisplay` overload from Navigation 3
 * 1.1.5 and forwards its rendering configuration unchanged. Sentry treats the final [backStack]
 * entry as the active route. Multiple retained back stacks and multipane-aware visible-route
 * selection are not supported.
 *
 * Unlike the Navigation 2 integration, which is notified synchronously during
 * `NavController.navigate()`, Navigation 3 exposes an app-owned back stack without a
 * pre-composition navigation callback. Spans started during a destination's initial composable body
 * may therefore attach to the previous route transaction or no navigation transaction. Start
 * destination work from `LaunchedEffect`, `DisposableEffect`, or `SideEffect` so it attaches to the
 * new navigation transaction.
 *
 * Values returned from [nameExtractor] and [argumentsExtractor] are sent to Sentry in breadcrumbs,
 * navigation state, and navigation transactions and are not scrubbed by the Sentry SDK. Only return
 * route names and arguments that are known to be safe or have been pre-scrubbed.
 *
 * @param backStack The navigation back stack to render and instrument.
 * @param modifier The modifier applied to the display layout.
 * @param contentAlignment The alignment of the animated content.
 * @param onBack Callback for handling system back. By default, this removes the final entry when
 *   [backStack] is mutable.
 * @param entryDecorators Decorators that add information to entry content.
 * @param sceneStrategies Strategies used to determine which scene to render.
 * @param sceneDecoratorStrategies Strategies used to decorate rendered scenes.
 * @param sharedTransitionScope Scope used for shared transitions between scenes.
 * @param sizeTransform Size transform used by the animated content.
 * @param transitionSpec Default transition used when navigating to an entry.
 * @param popTransitionSpec Default transition used when popping an entry.
 * @param predictivePopTransitionSpec Default predictive-back pop transition.
 * @param scopes The Sentry scopes instance used for instrumentation.
 * @param options The navigation information Sentry should record.
 * @param nameExtractor Optional extractor for a stable, human-readable route name. The entry's
 *   class simple name is used by default.
 * @param argumentsExtractor Optional extractor for route arguments. Values should be primitives
 *   (`String`, `Number`, `Boolean`), `null`, or nested `Map`/`Collection` values. Other values are
 *   converted to strings.
 * @param entryProvider Creates the [NavEntry] for a back-stack key.
 */
@Suppress("LongParameterList", "FunctionNaming")
@ApiStatus.Experimental
@Composable
public fun <T : Any> SentryNavDisplay(
  backStack: List<T>,
  modifier: Modifier = Modifier,
  contentAlignment: Alignment = Alignment.TopStart,
  onBack: () -> Unit = {
    if (backStack is MutableList<T>) {
      backStack.removeLastOrNull()
    }
  },
  entryDecorators: List<NavEntryDecorator<T>> =
    listOf(rememberSaveableStateHolderNavEntryDecorator()),
  sceneStrategies: List<SceneStrategy<T>> = listOf(SinglePaneSceneStrategy()),
  sceneDecoratorStrategies: List<SceneDecoratorStrategy<T>> = emptyList(),
  sharedTransitionScope: SharedTransitionScope? = null,
  sizeTransform: SizeTransform? = null,
  transitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
    defaultTransitionSpec(),
  popTransitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
    defaultPopTransitionSpec(),
  predictivePopTransitionSpec:
    AnimatedContentTransitionScope<Scene<T>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform =
    defaultPredictivePopTransitionSpec(),
  scopes: IScopes = ScopesAdapter.getInstance(),
  options: SentryNav3Options = SentryNav3Options(),
  nameExtractor: ((T) -> String)? = null,
  argumentsExtractor: ((T) -> Map<String, Any?>)? = null,
  entryProvider: (T) -> NavEntry<T>,
) {
  require(options.maxCapturedBackStackEntries > 0) {
    "maxCapturedBackStackEntries must be positive, was ${options.maxCapturedBackStackEntries}"
  }

  SentryNav3Effect(
    backStack = backStack,
    scopes = scopes,
    options = options,
    nameExtractor = nameExtractor,
    argumentsExtractor = argumentsExtractor,
  )

  NavDisplay(
    backStack = backStack,
    modifier = modifier,
    contentAlignment = contentAlignment,
    onBack = onBack,
    entryDecorators = entryDecorators,
    sceneStrategies = sceneStrategies,
    sceneDecoratorStrategies = sceneDecoratorStrategies,
    sharedTransitionScope = sharedTransitionScope,
    sizeTransform = sizeTransform,
    transitionSpec = transitionSpec,
    popTransitionSpec = popTransitionSpec,
    predictivePopTransitionSpec = predictivePopTransitionSpec,
    entryProvider = entryProvider,
  )
}

@Composable
@NonRestartableComposable
@Suppress("FunctionNaming")
private fun <T : Any> SentryNav3Effect(
  backStack: List<T>,
  scopes: IScopes,
  options: SentryNav3Options,
  nameExtractor: ((T) -> String)?,
  argumentsExtractor: ((T) -> Map<String, Any?>)?,
) {
  val backStackSnapshot = backStack.toList()
  val currentNameExtractor = rememberUpdatedState(nameExtractor)
  val currentArgumentsExtractor = rememberUpdatedState(argumentsExtractor)

  val observer =
    remember(
      scopes,
      options.enableNavigationBreadcrumbs,
      options.enableNavigationTransactions,
      options.captureBackStack,
      options.maxCapturedBackStackEntries,
    ) {
      SentryBackStackObserver(
        scopes = scopes,
        enableNavigationBreadcrumbs = options.enableNavigationBreadcrumbs,
        enableNavigationTransactions = options.enableNavigationTransactions,
        captureBackStack = options.captureBackStack,
        maxCapturedBackStackEntries = options.maxCapturedBackStackEntries,
        nameExtractor = nameExtractor,
        argumentsExtractor = argumentsExtractor,
      )
    }

  DisposableEffect(observer) { onDispose { observer.cleanup() } }

  DisposableEffect(observer, backStackSnapshot) {
    observer.nameExtractor = currentNameExtractor.value
    observer.argumentsExtractor = currentArgumentsExtractor.value
    observer.onBackStackChanged(backStackSnapshot)
    onDispose {}
  }
}
