package io.sentry.compose.navigation3

import androidx.lifecycle.Lifecycle
import io.sentry.IScope
import io.sentry.IScopes

/**
 * Coordinates the production and publishing of navigation data for one [SentryNavEffect].
 *
 * [BackStackObserver] translates back stack changes into Sentry data, while [NavScopes] controls
 * whether a given `SentryNavEffect` may publish that data or whether it should defer to another
 * currently active `SentryNavEffect`.
 *
 * **Usage**
 *
 * Call [attach] before the first [onBackStackChanged], and call [dispose] when observation ends.
 *
 * Sessions targeting the same [IScope] must share the same [NavLeaseCoordinator] instance.
 *
 * **Thread safety**
 *
 * This class is ***not*** thread-safe. Underlying [NavScopes] lifecycle callbacks and invocations
 * of this class's methods should be confined to the same thread.
 */
internal class NavSession<T : Any>(
  scopes: IScopes,
  options: SentryNavOptions,
  coordinator: NavLeaseCoordinator,
  entryMapper: ForwardingBackStackEntryMapper<T>,
  private val navScopes: NavScopes = NavScopes(scopes, coordinator),
  private val observer: BackStackObserver<T> = BackStackObserver(navScopes, options, entryMapper),
) {

  fun attach(lifecycle: Lifecycle) {
    navScopes.attach(lifecycle)
  }

  fun onBackStackChanged(backStack: List<T>) {
    navScopes.runBackStackUpdate { observer.onBackStackChanged(backStack) }
  }

  fun dispose() {
    // Clear only data still belonging to this session and restore any eligible previous owner.
    // Observer cleanup then runs without the lease, so it cannot clear host or restored state.
    navScopes.dispose()
    observer.cleanup()
  }
}
