package io.sentry.compose.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.sentry.IScopes
import io.sentry.ScopesAdapter
import io.sentry.SentryOptions
import org.jetbrains.annotations.ApiStatus

/**
 * An effect for generating Sentry data from your Nav3 backstack. Configure it via [options] and
 * call it before you invoke your `NavDisplay`.
 *
 * ```kotlin
 *  @Composable
 *  fun AppNavigation() {
 *    // Create your back stack like usual.
 *    val navBackStack = rememberNavBackStack(Home)
 *
 *    // Place SentryNavEffect in the same composable as your NavDisplay and call
 *    // the effect first. Doing so ensures the effect's lifecycle matches your
 *    // NavDisplay, and that any Sentry data produced by your initial nav
 *    // destination get attributed to the appropriate nav transaction.
 *    SentryNavEffect(
 *      backStack = navBackStack,
 *      backStackEntryMapper = { entry ->
 *        SentryBackStackEntry(entry.toName(), entry.extractArguments())
 *      },
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
 * By default, the following data is produced every time the top of the provided [backStack]
 * changes:
 *
 * - a new navigation transaction
 * - a breadcrumb
 * - a screen name
 * - a record of the current back stack (last 10 entries)
 *
 * You can configure the above defaults via [SentryNavOptions]. (Screen names can be disabled via
 * [SentryOptions.setEnableScreenTracking].)
 *
 * **Limitations**
 *
 * `SentryNavEffect` generates all Sentry data based solely on the top entry of your back stack. In
 * particular, it has no awareness of
 * [Scenes](https://developer.android.com/guide/navigation/navigation-3/scenes). Transaction routes,
 * breadcrumbs, and screen names are all derived from the top entry of the back stack and are
 * updated as it changes.
 *
 * `SentryNavEffect` doesn't make any special accommodations for
 * [predictive back](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture)
 * gestures. That means that spans produced by predictively rendered composables can show up under
 * the current destination's transaction.
 *
 * Only one `SentryNavEffect` should be active at a time. `SentryNavEffect` protects against
 * multiple instances being active during [lifecycle owner][androidx.lifecycle.LifecycleOwner]
 * transitions, such as when navigating between activities where each Activity has its own
 * `SentryNavEffect`. But `SentryNavEffect`s associated with side-by-side or nested `NavDisplay`s
 * are not supported and can result in duplicate or interleaved data and undefined transaction
 * behavior.
 *
 * @param backStack The navigation backstack to observe.
 * @param backStackEntryMapper Maps each entry of the [backStack] to a name and optional arguments
 *   for display in Sentry. See the [BackStackEntryMapper] KDoc for best practices.
 * @param options The kinds of navigation info this effect should record.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
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
    coordinator = sharedNavLeaseCoordinator,
  )
}

@Composable
internal fun <T : Any> SentryNavEffect(
  backStack: List<T>,
  backStackEntryMapper: BackStackEntryMapper<T>,
  options: SentryNavOptions,
  scopes: IScopes,
  coordinator: NavLeaseCoordinator,
) {
  val currentBackStackEntryMapper = rememberUpdatedState(backStackEntryMapper)
  val lifecycle = LocalLifecycleOwner.current.lifecycle

  val navScopes =
    remember(scopes, options, lifecycle, coordinator) { NavIScopes(scopes, coordinator) }

  val observer =
    remember(navScopes, options) {
      BackStackObserver(
        scopes = navScopes,
        options = options,
        entryMapper = ForwardingBackStackEntryMapper { currentBackStackEntryMapper.value },
      )
    }

  // Attach first so the initial back stack update can start its navigation transaction.
  DisposableEffect(observer, lifecycle) {
    navScopes.attach(lifecycle)
    lifecycle.addObserver(navScopes)

    onDispose {
      lifecycle.removeObserver(navScopes)
      observer.cleanup()
      navScopes.dispose()
    }
  }

  // Intentionally don't remember this copy. Snapshot-backed lists mutate in place, so
  // remember(backStack) { backStack.toList() } would cache a stale copy. (The key reference
  // retained by remember() and the backStack reference passed to this effect would point to the
  // same instance, causing remember() to always return the originally copied list.) Making a fresh
  // copy ensures a stable snapshot for the duration of each update and lets BackStackKey compare
  // it with the previous one.
  val copy = backStack.toList()

  DisposableEffect(observer, BackStackKey(copy)) {
    navScopes.runBackStackUpdate {
      observer.onBackStackChanged(backStack = copy)
    }
    onDispose {}
  }
}

/**
 * A key for distinguishing back stacks over time.
 *
 * Lets `*Effect`s restart when either the identity of a stack entry changes or the stack's entries
 * are reordered.
 */
internal class BackStackKey<T : Any>(private val backStack: List<T>) {

  override fun equals(other: Any?): Boolean {
    // Use of identity rather than structural equality frees us from entries' equals() and
    // hashCode() implementations, which are provided by the host app and may be incomplete,
    // expensive, or incorrect for our purposes.
    if (this === other) {
      return true
    }
    if (other !is BackStackKey<*>) {
      return false
    }
    if (backStack.size != other.backStack.size) {
      return false
    }

    return backStack.indices.all { index -> backStack[index] === other.backStack[index] }
  }

  override fun hashCode(): Int {
    var result = backStack.size
    for (entry in backStack) {
      result = 31 * result + System.identityHashCode(entry)
    }
    return result
  }
}
