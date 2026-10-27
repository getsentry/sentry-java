package io.sentry.compose.navigation3.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.common.truth.Truth.assertThat
import io.sentry.ISentryClient
import io.sentry.PropagationContext
import io.sentry.Scope
import io.sentry.ScopeType
import io.sentry.Scopes
import io.sentry.SentryOptions
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Exercises navigation lease publication together with real SDK transaction sampling. */
class NavScopesIntegrationTest {

  @Test
  fun `a deferred nav transaction is sampled after the propagation context rotates`() {
    val fixture = Fixture.create()
    val incumbentOwner = TestLifecycleOwner(Lifecycle.State.RESUMED)
    val incumbent = fixture.navScopes()
    incumbent.attach(incumbentOwner.lifecycle)
    incumbent.onBackStackUpdate()
    val incomingOwner = TestLifecycleOwner(Lifecycle.State.STARTED)
    val incoming = fixture.navScopes()
    incoming.attach(incomingOwner.lifecycle)
    incoming.onBackStackUpdate()
    val contextBeforeResume = fixture.scope.propagationContext
    val contextsAtSampling = mutableListOf<PropagationContext>()
    fixture.options.setTracesSampler {
      contextsAtSampling += fixture.scope.propagationContext
      1.0
    }
    incoming.configureScope {
      it.startNavTransaction(TransactionContext("/Checkout", "navigation"), TransactionOptions())
    }
    assertThat(contextsAtSampling).isEmpty()
    assertThat(fixture.scope.propagationContext).isSameInstanceAs(contextBeforeResume)

    incomingOwner.lifecycle.currentState = Lifecycle.State.RESUMED

    assertThat(contextsAtSampling).hasSize(1)
    assertThat(contextsAtSampling.single()).isNotSameInstanceAs(contextBeforeResume)
    assertThat(contextsAtSampling.single()).isSameInstanceAs(fixture.scope.propagationContext)
    assertThat(fixture.scope.transaction?.name).isEqualTo("/Checkout")
    incoming.dispose()
    incumbent.dispose()
  }

  private class TestLifecycleOwner(initialState: Lifecycle.State) : LifecycleOwner {
    override val lifecycle: LifecycleRegistry =
      LifecycleRegistry.createUnsafe(this).apply { currentState = initialState }
  }

  private class Fixture private constructor() {
    val options =
      SentryOptions().apply {
        dsn = "http://key@localhost/1"
        defaultScopeType = ScopeType.CURRENT
      }
    val scope = Scope(options)
    private val scopes = Scopes(scope, Scope(options), Scope(options), "navigation sampling test")
    private val coordinator = NavLeaseCoordinator()

    init {
      val client = mock<ISentryClient>()
      whenever(client.isEnabled).thenReturn(true)
      scopes.bindClient(client)
    }

    fun navScopes(): NavScopes = NavScopes(scopes, coordinator)

    companion object {
      fun create(): Fixture = Fixture()
    }
  }
}
