package io.sentry.compose.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.sentry.IScopes
import io.sentry.ScopesAdapter
import io.sentry.SentryOptions
import io.sentry.compose.navigation3.internal.backstack.BackStackKey
import io.sentry.compose.navigation3.internal.backstack.CoordinatedBackStackObserver
import io.sentry.compose.navigation3.internal.backstack.ForwardingBackStackEntryMapper
import io.sentry.compose.navigation3.internal.backstack.GatedBackStackObserver
import io.sentry.compose.navigation3.internal.backstack.defaultNavEffectGate
import io.sentry.compose.navigation3.internal.backstack.defaultNavLeaseCoordinator
import io.sentry.compose.navigation3.internal.lifecycle.NavEffectGate
import io.sentry.compose.navigation3.internal.lifecycle.NavEffectKind
import io.sentry.compose.navigation3.internal.lifecycle.NavLeaseCoordinator
import io.sentry.compose.navigation3.internal.lifecycle.NavRootInfo
import io.sentry.compose.navigation3.internal.lifecycle.findActivity
import org.jetbrains.annotations.ApiStatus

/**
 * A composable effect for generating Sentry data from your Nav3 backstack. Configure it via
 * [options] and call it before you invoke your `NavDisplay`:
 * ```kotlin
 *  @Composable
 *  fun AppNavigation() {
 *    // Create your back stack like usual.
 *    val navBackStack = rememberNavBackStack(Home)
 *
 *    // Place SentryNavEffect in the same composable as your NavDisplay and call
 *    // the effect first. Doing so ensures the effect's lifecycle matches your
 *    // NavDisplay, and that any Sentry data produced by your initial nav
 *    // destination are attributed to the appropriate nav transaction.
 *    SentryNavEffect(
 *      backStack = navBackStack,
 *      backStackEntryMapper = BackStackEntryMapper.SerialName,
 *      options = SentryNavOptions(),
 *    )
 *
 *    // Configure your NavDisplay like usual.
 *    NavDisplay(
 *      backStack = navBackStack,
 *      ...
 *    )
 *  }
 * ```
 *
 * **Data generated**
 *
 * By default, the following are produced every time the top of the provided [backStack] changes:
 *
 * - a new navigation transaction (unless another transaction is already active on the current
 *   scope)
 * - a breadcrumb
 * - a screen name
 * - a record of the current back stack (last 5 entries)
 *
 * You can configure the above defaults via [SentryNavOptions]. (Screen names can be disabled via
 * [SentryOptions.setEnableScreenTracking].)
 *
 * **Limitations**
 *
 * *Tracks the back stack, not scenes*: `SentryNavEffect` generates all Sentry data based solely on
 * the top entry of your back stack. In particular, it has no awareness of
 * [Scenes](https://developer.android.com/guide/navigation/navigation-3/scenes). Transaction routes,
 * breadcrumbs, and screen names are all derived from the top entry of the back stack and are
 * updated as it changes.
 *
 * *No accommodation for predictive back*: `SentryNavEffect` doesn't make any special accommodations
 * for
 * [predictive back](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture)
 * gestures. That means that spans produced by predictively rendered composables can show up under
 * the current destination's transaction.
 *
 * *Limited support for simultaneously active `SentryNavEffect`s*: Multiple `SentryNavEffect`s are
 * supported only in certain setups: roughly, one effect per Activity or Fragment, placed next to
 * (and not inside) its `NavDisplay`. See the
 * [Navigation for Android docs](https://docs.sentry.io/platforms/android/integrations/navigation/#supported-setups)
 * for a detailed list of supported vs unsupported configurations.
 *
 * @param backStack The navigation backstack to observe.
 * @param backStackEntryMapper Maps each entry of the [backStack] to a name and optional arguments
 *   for display in Sentry. See the [BackStackEntryMapper] KDoc for best practices.
 * @param options The kinds of navigation info this effect should record.
 */
@ApiStatus.Experimental
@Composable
public fun <T : Any> SentryNavEffect(
  backStack: List<T>,
  backStackEntryMapper: BackStackEntryMapper<T>,
  options: SentryNavOptions = SentryNavOptions(),
) {
  SentryNavEffect(
    backStack = backStack,
    backStackEntryMapper = backStackEntryMapper,
    options = options,
    scopes = ScopesAdapter.getInstance(),
    coordinator = defaultNavLeaseCoordinator,
  )
}

/**
 * Implements [SentryNavEffect] for both the host app's calls and auto-instrumentation.
 *
 * @param gate decides whether this effect may observe [backStack] alongside other effects (see
 *   [NavEffectGate]).
 * @param kind whether the host app or auto-instrumentation called this effect.
 * @param navRoot the Activity or Fragment this effect belongs to. Its lifecycle is what
 *   [coordinator] observes, and the [gate] groups effects by it. Defaults to [LocalLifecycleOwner],
 *   which is the Activity or Fragment when the effect is placed next to its `NavDisplay`, as
 *   [SentryNavEffect] requires.
 */
@Suppress("LongParameterList")
@Composable
internal fun <T : Any> SentryNavEffect(
  backStack: List<T>,
  backStackEntryMapper: BackStackEntryMapper<T>,
  options: SentryNavOptions,
  scopes: IScopes,
  coordinator: NavLeaseCoordinator,
  gate: NavEffectGate = defaultNavEffectGate,
  kind: NavEffectKind = NavEffectKind.MANUAL,
  navRoot: LifecycleOwner = LocalLifecycleOwner.current,
) {
  val currentBackStackEntryMapper = rememberUpdatedState(backStackEntryMapper)
  val context = LocalContext.current

  val observer =
    remember(scopes, options, navRoot, coordinator, gate, kind) {
      GatedBackStackObserver {
        CoordinatedBackStackObserver(
          scopes = scopes,
          options = options,
          entryMapper = ForwardingBackStackEntryMapper { currentBackStackEntryMapper.value },
          coordinator = coordinator,
        )
      }
    }

  // Register first so the initial prepared back stack update can acquire navigation ownership.
  //
  // Intentionally don't key on context. It's only used to find the Activity that displays the nav
  // root, which can't change without the nav root changing too, and that replaces the observer.
  // The context's identity can change more often (e.g., if the host app provides a new
  // ContextThemeWrapper on every recomposition), and re-registering for those changes would
  // needlessly finish the nav transaction.
  DisposableEffect(observer) {
    observer.register(
      gate = gate,
      kind = kind,
      navRoot = navRoot,
      rootInfo = NavRootInfo(navRoot.lifecycle, context.findActivity()),
      backStack = backStack,
    )
    onDispose { observer.unregister() }
  }

  // Intentionally don't remember this copy. Snapshot-backed lists mutate in place, so
  // remember(backStack) { backStack.toList() } would cache a stale copy. (The key reference
  // retained by remember() and the backStack reference passed to this effect would point to the
  // same instance, causing remember() to always return the originally copied list.) Making a fresh
  // copy ensures a stable snapshot for the duration of each update and lets BackStackKey compare
  // it with the previous one.
  val copy = backStack.toList()

  DisposableEffect(observer, BackStackKey(copy)) {
    observer.onBackStackChanged(backStack = backStack, copy = copy)
    onDispose {}
  }

  // The gate tells effects apart by the back stack instance they observe, which can change without
  // the back stack's contents changing. The effect above only runs when the contents change.
  SideEffect { observer.onBackStackInstanceChanged(backStack) }
}
