package io.sentry.compose.navigation3

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.compose.navigation3.lifecycle.NavLeaseCoordinator
import io.sentry.compose.navigation3.lifecycle.NavScopes

/**
 * A [BackStackObserver] whose updates to a shared mutable [IScope] are coordinated with other
 * `BackStackObserver`s.
 *
 * Coordination is achieved via [NavScopes].
 *
 * **Usage**
 *
 * Call [attach] before the first [onBackStackChanged], and call [dispose] when observation ends.
 *
 * Instances targeting the same [IScope] must be created with the same [NavLeaseCoordinator].
 *
 * **Thread safety**
 *
 * This class is ***not*** thread-safe. Invocations should be confined to the same thread as the
 * [LifecycleEventObserver] callbacks implemented by [NavScopes].
 */
internal class CoordinatedBackStackObserver<T : Any>(
  scopes: IScopes,
  options: SentryNavOptions,
  entryMapper: ForwardingBackStackEntryMapper<T>,
  coordinator: NavLeaseCoordinator,
) {

  private val navScopes = NavScopes(scopes, coordinator, onLeaseGainedWithEmptySlot = ::reobserve)
  private val observer = BackStackObserver(navScopes, options, entryMapper)

  /** The back stack most recently passed to [observer], replayed by [reobserve]. */
  private var lastBackStack: List<T> = emptyList()

  fun attach(lifecycle: Lifecycle) {
    navScopes.attach(lifecycle)
  }

  fun onBackStackChanged(backStack: List<T>) {
    lastBackStack = backStack
    navScopes.onBackStackUpdate()
    observer.onBackStackChanged(backStack)
  }

  /**
   * Restarts observation of [lastBackStack], so [observer] records it as a first observation: a new
   * top and, with it, a nav transaction for the destination that's showing.
   *
   * Called when this instance gains the nav lease without a back stack update and nothing occupies
   * the scope's transaction slot (e.g., a ViewPager2 page the user just swiped to). Its back stack
   * top didn't change, so [observer] would otherwise start no transaction, and spans would be lost
   * until it did.
   *
   * Bypasses [NavScopes.onBackStackUpdate], as the lease is already held.
   */
  private fun reobserve() {
    val backStack = lastBackStack.takeIf { it.isNotEmpty() } ?: return
    observer.reset()
    observer.onBackStackChanged(backStack)
  }

  fun dispose() {
    lastBackStack = emptyList()
    // Call disposal on both the observer and navScopes in case their (currently overlapping)
    // implementations drift over time.
    observer.reset()
    navScopes.dispose()
  }
}

/** The [NavLeaseCoordinator] shared by default by all [SentryNavEffect]s. */
internal val defaultNavLeaseCoordinator: NavLeaseCoordinator = NavLeaseCoordinator()
