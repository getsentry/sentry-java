package io.sentry.compose.navigation3

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.common.truth.Truth.assertThat
import io.sentry.IScopes
import io.sentry.ITransaction
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryOptions
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NavScopesTest {

  @Test
  fun `attach() - ignores a second, different lifecycle`() {
    val fixture = Fixture()
    val navScopes = fixture.navScopes()
    val first = TestLifecycleOwner()
    val second = TestLifecycleOwner()
    navScopes.attach(first.lifecycle)

    navScopes.attach(second.lifecycle)

    assertThat(first.lifecycle.observerCount).isEqualTo(1)
    assertThat(second.lifecycle.observerCount).isEqualTo(0)
  }

  @Test
  fun `lifecycle callbacks are recorded while scope callbacks are skipped`() {
    val fixture = Fixture()
    val outgoing = fixture.navScopes()
    val outgoingOwner = TestLifecycleOwner()
    outgoing.attach(outgoingOwner.lifecycle)
    fixture.isEnabled = false
    outgoingOwner.lifecycle.currentState = Lifecycle.State.RESUMED
    fixture.isEnabled = true
    outgoing.applyFirstBackStackUpdate()

    val incoming = fixture.navScopes()
    val incomingOwner = TestLifecycleOwner()
    incoming.attach(incomingOwner.lifecycle)
    incoming.applyFirstBackStackUpdate()
    assertThat(incoming.hasLease()).isTrue()

    incoming.dispose()

    assertThat(outgoing.hasLease()).isTrue()
  }

  private fun NavScopes.applyFirstBackStackUpdate() {
    runBackStackUpdate { configureScope {} }
  }

  private fun NavScopes.hasLease(): Boolean =
    !startTransaction(TransactionContext("name", "op"), TransactionOptions()).isNoOp

  private class TestLifecycleOwner : LifecycleOwner {
    override val lifecycle: LifecycleRegistry =
      LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.CREATED }
  }

  private class Fixture {
    var isEnabled = true
    val scope = Scope(SentryOptions())
    val coordinator = NavLeaseCoordinator()
    val scopes =
      mock<IScopes>().also { scopes ->
        whenever(scopes.options).thenReturn(SentryOptions())
        doAnswer {
            if (isEnabled) {
              (it.arguments[0] as ScopeCallback).run(scope)
            }
            null
          }
          .whenever(scopes)
          .configureScope(any())
        whenever(scopes.startTransaction(any<TransactionContext>(), any<TransactionOptions>()))
          .thenAnswer { mock<ITransaction>() }
      }

    fun navScopes(): NavScopes = NavScopes(scopes, coordinator)
  }
}
