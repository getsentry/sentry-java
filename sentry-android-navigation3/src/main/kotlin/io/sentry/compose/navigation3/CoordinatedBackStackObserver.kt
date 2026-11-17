package io.sentry.compose.navigation3

import androidx.lifecycle.LifecycleEventObserver
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.compose.navigation3.lifecycle.NavLeaseCoordinator
import io.sentry.compose.navigation3.lifecycle.NavRootInfo
import io.sentry.compose.navigation3.lifecycle.NavScopes

/**
 * A [BackStackObserver] whose updates to a shared mutable [IScope] are coordinated with those of
 * other `BackStackObserver`s.
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
 * [LifecycleEventObserver] callbacks through which the [NavLeaseCoordinator] observes the attached
 * lifecycle.
 */
internal class CoordinatedBackStackObserver<T : Any>(
  scopes: IScopes,
  options: SentryNavOptions,
  entryMapper: ForwardingBackStackEntryMapper<T>,
  coordinator: NavLeaseCoordinator,
) {

  private val navScopes = NavScopes(scopes, coordinator)
  private val observer = BackStackObserver(navScopes, options, entryMapper)

  fun attach(rootInfo: NavRootInfo) {
    navScopes.attach(rootInfo)
  }

  fun onBackStackChanged(backStack: List<T>) {
    navScopes.onBackStackUpdate()
    observer.onBackStackChanged(backStack)
  }

  fun dispose() {
    // Call disposal on both the observer and navScopes in case their (currently overlapping)
    // implementations drift over time.
    observer.dispose()
    navScopes.dispose()
  }
}

/**
 * The [NavLeaseCoordinator] shared by default by all [CoordinatedBackStackObserver]s.
 *
 * **Lifetime**
 *
 * The default coordinator lives as long as the process, since lease state follows nav root
 * lifecycles rather than the SDK's. Closing and re-initializing the SDK doesn't require a reset
 * because:
 *
 * - `SentryNavEffect` resolves the current scopes on every call;
 *
 * - while the SDK is closed, bookkeeping continues without a scope; and
 *
 * - unpublishing compares by reference, so values published to an earlier scope don't affect the
 *   current one.
 *
 * Limitation: A nav transaction that's still running when the SDK is closed and re-initialized can
 * be bound to the new scope the next time it's published, although its client has been closed.
 * Spans then attach to it, and are lost, until it times out or the next navigation finishes it.
 */
internal val defaultNavLeaseCoordinator: NavLeaseCoordinator = NavLeaseCoordinator()
