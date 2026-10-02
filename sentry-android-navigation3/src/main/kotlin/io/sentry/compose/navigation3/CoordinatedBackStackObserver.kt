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

  private val navScopes = NavScopes(scopes, coordinator)
  private val observer = BackStackObserver(navScopes, options, entryMapper)

  fun attach(lifecycle: Lifecycle) {
    navScopes.attach(lifecycle)
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

/** The [NavLeaseCoordinator] shared by default by all [SentryNavEffect]s. */
internal val defaultNavLeaseCoordinator: NavLeaseCoordinator = NavLeaseCoordinator()
