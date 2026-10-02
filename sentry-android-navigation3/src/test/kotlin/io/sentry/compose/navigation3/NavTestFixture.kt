package io.sentry.compose.navigation3

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.sentry.ISentryClient
import io.sentry.ITransaction
import io.sentry.Scope
import io.sentry.ScopeType
import io.sentry.Scopes
import io.sentry.SentryOptions
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Real SDK state with only the event delivery boundary stubbed. No timers or global SDK state. */
internal class NavTestFixture {

  val options =
    SentryOptions().apply {
      dsn = "http://key@localhost/1"
      defaultScopeType = ScopeType.CURRENT
      setTracesSampleRate(1.0)
      idleTimeout = null
      deadlineTimeout = 0
    }

  val scope = Scope(options)
  val scopes = Scopes(scope, Scope(options), Scope(options), "navigation test")

  val coordinator = NavLeaseCoordinator()

  init {
    val client = mock<ISentryClient>()
    whenever(client.isEnabled).thenReturn(true)
    scopes.bindClient(client)
  }

  fun navScopes(): INavScopes = INavScopes(scopes, coordinator)

  fun session(navOptions: SentryNavOptions = SentryNavOptions()): NavSession<String> =
    NavSession(
      scopes = scopes,
      options = navOptions,
      coordinator = coordinator,
      entryMapper =
        ForwardingBackStackEntryMapper { BackStackEntryMapper { SentryBackStackEntry(it) } },
    )

  fun transaction(name: String = "host"): ITransaction =
    scopes.startTransaction(TransactionContext(name, "navigation"), TransactionOptions())
}

internal class NavTestLifecycle(state: Lifecycle.State = Lifecycle.State.CREATED) : LifecycleOwner {

  override val lifecycle =
    LifecycleRegistry.createUnsafe(this).apply {
      if (state == Lifecycle.State.DESTROYED) currentState = Lifecycle.State.CREATED
      currentState = state
    }

  fun resume() {
    lifecycle.currentState = Lifecycle.State.RESUMED
  }

  fun pause() {
    lifecycle.currentState = Lifecycle.State.STARTED
  }

  fun destroy() {
    lifecycle.currentState = Lifecycle.State.DESTROYED
  }
}
