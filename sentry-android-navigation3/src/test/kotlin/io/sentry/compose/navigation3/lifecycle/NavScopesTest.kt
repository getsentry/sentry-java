package io.sentry.compose.navigation3.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.common.truth.Truth.assertThat
import io.sentry.Breadcrumb
import io.sentry.Hint
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
import org.mockito.kotlin.verify
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
    val incomingOwner = TestLifecycleOwner(Lifecycle.State.RESUMED)
    incoming.attach(incomingOwner.lifecycle)
    incoming.applyFirstBackStackUpdate()
    assertThat(fixture.hasLease(incoming)).isTrue()

    incoming.dispose()

    assertThat(fixture.hasLease(outgoing)).isTrue()
  }

  @Test
  fun `onBackStackUpdate() - an update skipped by the scope does not count as the first update`() {
    val fixture = Fixture()
    val navScopes = fixture.navScopes()
    val owner = TestLifecycleOwner()
    navScopes.attach(owner.lifecycle)
    fixture.isEnabled = false
    navScopes.applyFirstBackStackUpdate()
    fixture.isEnabled = true

    navScopes.applyFirstBackStackUpdate()

    assertThat(fixture.hasLease(navScopes)).isTrue()
  }

  @Test
  fun `addBreadcrumb() - records breadcrumbs immediately without the nav lease`() {
    val fixture = Fixture()
    val leaseholder = fixture.navScopes()
    leaseholder.attach(TestLifecycleOwner().lifecycle)
    leaseholder.applyFirstBackStackUpdate()
    val other = fixture.navScopes()
    other.attach(TestLifecycleOwner().lifecycle)
    val breadcrumb = Breadcrumb()
    val hint = Hint()

    other.addBreadcrumb(breadcrumb, hint)

    assertThat(fixture.hasLease(other)).isFalse()
    verify(fixture.scopes).addBreadcrumb(breadcrumb, hint)
  }

  @Test
  fun `startTransaction() - returns a no-op transaction without the nav lease`() {
    val fixture = Fixture()
    val covered = fixture.navScopes()
    covered.attach(TestLifecycleOwner(Lifecycle.State.RESUMED).lifecycle)
    covered.applyFirstBackStackUpdate()
    val incoming = fixture.navScopes()
    incoming.attach(TestLifecycleOwner(Lifecycle.State.RESUMED).lifecycle)
    incoming.applyFirstBackStackUpdate()

    val transaction =
      covered.startTransaction(TransactionContext("name", "op"), TransactionOptions())

    assertThat(fixture.hasLease(covered)).isFalse()
    assertThat(transaction.isNoOp).isTrue()
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `startTransaction() - declining without the nav lease defers a propagation context rotation`() {
    val fixture = Fixture()
    val covered = fixture.navScopes()
    covered.attach(TestLifecycleOwner(Lifecycle.State.RESUMED).lifecycle)
    covered.applyFirstBackStackUpdate()
    val incoming = fixture.navScopes()
    incoming.attach(TestLifecycleOwner(Lifecycle.State.RESUMED).lifecycle)
    incoming.applyFirstBackStackUpdate()
    val incomingContext = fixture.scope.propagationContext

    covered.startTransaction(TransactionContext("name", "op"), TransactionOptions())
    assertThat(fixture.scope.propagationContext).isSameInstanceAs(incomingContext)

    incoming.dispose()

    assertThat(fixture.hasLease(covered)).isTrue()
    assertThat(fixture.scope.propagationContext).isNotSameInstanceAs(incomingContext)
  }

  @Test
  fun `startTransaction() - returns a no-op transaction after dispose`() {
    val fixture = Fixture()
    val navScopes = fixture.navScopes()
    navScopes.attach(TestLifecycleOwner().lifecycle)
    navScopes.applyFirstBackStackUpdate()
    navScopes.dispose()

    val transaction =
      navScopes.startTransaction(TransactionContext("name", "op"), TransactionOptions())

    assertThat(transaction.isNoOp).isTrue()
  }

  @Test
  fun `rotatePropagationContext() - a rotation requested without the nav lease happens once the lease is acquired`() {
    val fixture = Fixture()
    val covered = fixture.navScopes()
    covered.attach(TestLifecycleOwner(Lifecycle.State.RESUMED).lifecycle)
    covered.applyFirstBackStackUpdate()
    val incoming = fixture.navScopes()
    incoming.attach(TestLifecycleOwner(Lifecycle.State.RESUMED).lifecycle)
    incoming.applyFirstBackStackUpdate()
    val incomingContext = fixture.scope.propagationContext

    covered.configureScope { it.rotatePropagationContext() }
    assertThat(fixture.scope.propagationContext).isSameInstanceAs(incomingContext)

    incoming.dispose()

    assertThat(fixture.hasLease(covered)).isTrue()
    assertThat(fixture.scope.propagationContext).isNotSameInstanceAs(incomingContext)
  }

  private fun NavScopes.applyFirstBackStackUpdate() {
    onBackStackUpdate()
  }

  private class TestLifecycleOwner(initialState: Lifecycle.State = Lifecycle.State.CREATED) :
    LifecycleOwner {
    override val lifecycle: LifecycleRegistry =
      LifecycleRegistry.createUnsafe(this).apply { currentState = initialState }
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

    private var leaseProbeCount = 0

    /**
     * Whether [navScopes] holds the nav lease, observed through whether a screen name it sets
     * reaches the real [scope]. Overwrites the scope's screen.
     */
    fun hasLease(navScopes: NavScopes): Boolean {
      val probe = "lease probe ${++leaseProbeCount}"
      navScopes.configureScope { it.screen = probe }
      return scope.screen == probe
    }
  }
}
