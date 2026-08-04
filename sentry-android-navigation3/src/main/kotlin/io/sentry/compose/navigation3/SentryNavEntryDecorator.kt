package io.sentry.compose.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntryDecorator
import io.sentry.IScopes
import io.sentry.ScopesAdapter
import org.jetbrains.annotations.ApiStatus

/*
 * TODO ADAM: NEXT STEPS
 *
 * - Consider properly handling list vs detail on phone vs tablet even in phase 1. Do it if we do so for Nav2. (Do we need some sort of special SceneStrategy support? Doesn't look like it if we use the approach in our phase 1-3 branch. There, we rely on NavEntryDecorator to tell us what's currently visible on the screen. We may want to bring this forward into phase 1??)
 *
 * - Note that Sentry's own Compose rendering spans aren't showing up under our Nav2 compose or Nav3 navigation transactions. Have LLM retry with the sample app to ensure it tested adequately. 
 *
 * - Any performance concerns?
 *
 * - Any way our implementation could crash the host app?
 *
 * - Any security concerns?
 *
 * - Make sure our composition ordering updates work correctly when creating a nav transaction. (Eg, if we navigate to a screen that uses a LaunchedEffect to do initial work, we want to make sure that work is tracked under the nav transaction.)
 *
 * --- Add simulated work in the destination that involves LaunchedEffect, DisposableEffect, etc.
 *
 * - Make sure sample app contains all required nav3 recipes.
 *
 * - Have LLM re-check for Nav2 vs Nav3 parity.
 *
 * - Use kotlinx serialization to produce routes in sample app. Create any helper methods customers might need (?).
 *
 * - Determine whether we want to emit any additional Sentry state when the backstack is updated (eg, SceneStrategy, DialogStrategy, etc.).
 *
 * - Harmonize Nav2 and Nav3 sample apps.
 *
 * - Does SAGP auto-instrument for Nav2? Yes: SAGP auto-instruments Compose Navigation 2 by bytecode-patching rememberNavController() and wrapping the returned NavHostController with Sentry’s compose navigation hook. That hook ends up enabling both breadcrumbs and navigation transactions by default. SAGP does NOT auto-instrument classic Nav2 navigation.
 *
 * PRs:
 * ---------------------
 * - Initial PRs for Nav2 sample app without Compose tab, and then Compose tab.
 *
 * - Then PR for Nav3 phase 1 implementation broken up as follows (or something like it):
 *
 *  --- Non-public classes / infra in one or more PRs: SentryNavStateHolder, etc.
 *  --- What would be public classes, converted for now to internal.
 *  --- Convert classes to public with @ApiInternal annotations + Nav3 sample app.
 *
 */
// TODO ADAM: Update KDoc.
/**
 * Returns a Sentry [NavEntryDecorator] for a single Navigation 3 back stack.
 *
 * Call this from the same long-lived composable that owns the back stack and
 * [androidx.navigation3.ui.NavDisplay], then pass the returned decorator to `NavDisplay`'s
 * `entryDecorators`. Sentry treats the final [backStack] entry as the active route. Multiple
 * retained back stacks and multipane-aware visible-route selection are not supported.
 *
 * Unlike the Navigation 2 integration, which is notified synchronously during
 * `NavController.navigate()`, Navigation 3 exposes an app-owned back stack without a
 * pre-composition navigation callback. Spans started during a destination's initial composable body
 * may therefore attach to the previous route transaction or no navigation transaction. Start
 * destination work from `LaunchedEffect`, `DisposableEffect`, or `SideEffect` so it attaches to the
 * new navigation transaction.
 *
 * @param backStack The navigation back stack and route representation Sentry should use.
 * @param options The navigation information Sentry should record.
 * @param scopes The Sentry scopes instance used for instrumentation.
 */
@ApiStatus.Experimental
@Composable
@NonRestartableComposable
public fun <T : Any> rememberSentryNavEntryDecorator(
  backStack: SentryNavBackStack<T>,
  options: SentryNavOptions = SentryNavOptions(),
  scopes: IScopes = ScopesAdapter.getInstance(),
): NavEntryDecorator<T> {
  val holder = rememberSentryNavStateHolder(backStack, options, scopes)
  return rememberSentryNavEntryDecorator(holder)
}

@Composable
@NonRestartableComposable
internal fun <T : Any> rememberSentryNavEntryDecorator(
  holder: SentryNavStateHolder<T>
): NavEntryDecorator<T> {
  // Phase 1 instrumentation is driven by back-stack observation; the returned decorator keeps the
  // call site aligned with NavDisplay and preserves composition ordering without owning NavDisplay.
  return remember(holder) { NavEntryDecorator { entry -> entry.Content() } }
}
