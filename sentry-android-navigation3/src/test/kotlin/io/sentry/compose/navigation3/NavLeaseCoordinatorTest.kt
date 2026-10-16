package io.sentry.compose.navigation3

import androidx.lifecycle.Lifecycle
import com.google.common.truth.Truth.assertThat
import io.sentry.ITransaction
import io.sentry.Scope
import io.sentry.SentryOptions
import io.sentry.SpanStatus
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class NavLeaseCoordinatorTest {

  private val owner = Any()
  private val incoming = Any()
  private val incumbent = Any()
  private val previous = Any()

  private val transaction = mock<ITransaction>()
  private val unrelatedTransaction = mock<ITransaction>()

  @Test
  fun `a registered created owner acquires the nav lease on its first back stack update`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.register(owner, Lifecycle.State.CREATED)

    navLeaseCoordinator.onBackStackUpdate(owner, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(owner)).isTrue()
    assertThat(fixture.published).containsExactly(owner)
  }

  @Test
  fun `registering the same owner twice preserves its first publication callback`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.register(owner, Lifecycle.State.CREATED)

    navLeaseCoordinator.registerCandidate(
      owner,
      Lifecycle.State.RESUMED,
      { fixture.published += "duplicate" },
    )
    navLeaseCoordinator.onBackStackUpdate(owner, fixture.scope)

    assertThat(fixture.published).containsExactly(owner)
  }

  @Test
  fun `registering a destroyed owner does not make it eligible for the nav lease`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator

    fixture.register(owner, Lifecycle.State.DESTROYED)
    navLeaseCoordinator.onBackStackUpdate(owner, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(owner)).isFalse()
    assertThat(fixture.published).isEmpty()
  }

  @Test
  fun `equal but distinct owners do not share the nav lease`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    val registeredOwner = String(charArrayOf('a'))
    val equalOwner = String(charArrayOf('a'))
    fixture.acquireLease(registeredOwner)

    val result = navLeaseCoordinator.hasLease(equalOwner)

    assertThat(result).isFalse()
    assertThat(navLeaseCoordinator.hasLease(registeredOwner)).isTrue()
  }

  @Test
  fun `starting a created owner leaves it eligible to acquire the nav lease on its first back stack update`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.register(owner, Lifecycle.State.CREATED)

    navLeaseCoordinator.startCandidate(owner)
    navLeaseCoordinator.onBackStackUpdate(owner, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(owner)).isTrue()
  }

  @Test
  fun `restarting a stopped owner does not take the nav lease on its first back stack update`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.register(owner, Lifecycle.State.CREATED)
    navLeaseCoordinator.stopCandidate(owner, fixture.scope)
    fixture.acquireLease(incumbent)

    navLeaseCoordinator.startCandidate(owner)
    navLeaseCoordinator.onBackStackUpdate(owner, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(incumbent)).isTrue()
    assertThat(fixture.published).containsExactly(incumbent)
  }

  @Test
  fun `resuming an owner before its first back stack update leaves the nav lease unassigned`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.register(owner, Lifecycle.State.CREATED)

    navLeaseCoordinator.resumeCandidate(owner, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(owner)).isFalse()
    assertThat(fixture.published).isEmpty()
  }

  @Test
  fun `resuming a stopped owner after its first back stack update transfers the nav lease`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(incumbent)
    fixture.register(owner, Lifecycle.State.CREATED)
    navLeaseCoordinator.stopCandidate(owner, fixture.scope)
    navLeaseCoordinator.onBackStackUpdate(owner, fixture.scope)

    navLeaseCoordinator.resumeCandidate(owner, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(owner)).isTrue()
    assertThat(fixture.published).containsExactly(incumbent, owner).inOrder()
  }

  @Test
  fun `a paused owner cannot acquire the nav lease on its first back stack update`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.register(owner, Lifecycle.State.RESUMED)
    navLeaseCoordinator.pauseCandidate(owner, fixture.scope)

    navLeaseCoordinator.onBackStackUpdate(owner, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(owner)).isFalse()
    assertThat(fixture.published).isEmpty()
  }

  @Test
  fun `later back stack updates do not republish nav data or reclaim the nav lease`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    val first = Any()
    val second = Any()
    fixture.acquireLease(first)
    fixture.acquireLease(second, state = Lifecycle.State.CREATED)

    navLeaseCoordinator.onBackStackUpdate(first, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(second)).isTrue()
    assertThat(fixture.published).containsExactly(first, second).inOrder()
  }

  @Test
  fun `pausing the leaseholder restores the most recently resumed eligible owner`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    val older = Any()
    val recent = Any()
    fixture.acquireLease(older)
    fixture.acquireLease(recent)
    fixture.acquireLease(incoming, state = Lifecycle.State.CREATED)

    navLeaseCoordinator.pauseCandidate(incoming, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(recent)).isTrue()
    assertThat(fixture.published.last()).isSameInstanceAs(recent)
  }

  @Test
  fun `pausing the leaseholder skips a resumed owner without a back stack update`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    val eligible = Any()
    val uninitialized = Any()
    fixture.acquireLease(eligible)
    fixture.register(uninitialized, Lifecycle.State.RESUMED)
    fixture.acquireLease(incoming, state = Lifecycle.State.CREATED)

    navLeaseCoordinator.pauseCandidate(incoming, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(eligible)).isTrue()
    assertThat(navLeaseCoordinator.hasLease(uninitialized)).isFalse()
  }

  @Test
  fun `pausing the only eligible owner releases the nav lease`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(owner)

    navLeaseCoordinator.pauseCandidate(owner, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(owner)).isFalse()
    assertThat(fixture.published).containsExactly(owner)
  }

  @Test
  fun `stopping the leaseholder restores a resumed owner`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(previous)
    fixture.acquireLease(owner, state = Lifecycle.State.CREATED)

    navLeaseCoordinator.stopCandidate(owner, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(previous)).isTrue()
    assertThat(fixture.published.last()).isSameInstanceAs(previous)
  }

  @Test
  fun `stopping an owner excludes it from later nav lease fallback`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(previous)
    navLeaseCoordinator.stopCandidate(previous, fixture.scope)
    fixture.acquireLease(owner, state = Lifecycle.State.CREATED)

    navLeaseCoordinator.stopCandidate(owner, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(previous)).isFalse()
    assertThat(navLeaseCoordinator.hasLease(owner)).isFalse()
  }

  @Test
  fun `resuming without a scope keeps the owner eligible for nav lease fallback`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.register(owner, Lifecycle.State.CREATED)
    navLeaseCoordinator.resumeCandidate(owner, null)
    navLeaseCoordinator.onBackStackUpdate(owner, fixture.scope)
    fixture.acquireLease(incoming, state = Lifecycle.State.CREATED)

    navLeaseCoordinator.pauseCandidate(incoming, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(owner)).isTrue()
  }

  @Test
  fun `resuming without a scope does not transfer the nav lease`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(incumbent)
    fixture.register(owner, Lifecycle.State.CREATED)
    navLeaseCoordinator.stopCandidate(owner, fixture.scope)
    navLeaseCoordinator.onBackStackUpdate(owner, fixture.scope)

    navLeaseCoordinator.resumeCandidate(owner, null)

    assertThat(navLeaseCoordinator.hasLease(incumbent)).isTrue()
    assertThat(fixture.published).containsExactly(incumbent)
  }

  @Test
  fun `pausing the leaseholder without a scope releases the nav lease without publishing a fallback`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(previous)
    fixture.acquireLease(owner, state = Lifecycle.State.CREATED)

    navLeaseCoordinator.pauseCandidate(owner, null)

    assertThat(navLeaseCoordinator.hasLease(owner)).isFalse()
    assertThat(navLeaseCoordinator.hasLease(previous)).isFalse()
    assertThat(fixture.published).containsExactly(previous, owner).inOrder()
  }

  @Test
  fun `stopping the leaseholder without a scope releases the nav lease`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(owner)

    navLeaseCoordinator.stopCandidate(owner, null)

    assertThat(navLeaseCoordinator.hasLease(owner)).isFalse()
  }

  @Test
  fun `a non-leaseholder cannot bind a nav transaction`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    val other = Any()
    fixture.acquireLease(owner)

    navLeaseCoordinator.bindTransaction(other, transaction)
    navLeaseCoordinator.unregisterCandidate(owner, fixture.scope)

    verifyNoInteractions(transaction)
  }

  @Test
  fun `an unfinished nav transaction is restored when its owner regains the lease`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(owner, transaction = transaction)
    fixture.acquireLease(incoming, state = Lifecycle.State.CREATED)

    assertThat(fixture.scope.transaction).isNull()
    navLeaseCoordinator.pauseCandidate(incoming, fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(transaction)
    assertThat(navLeaseCoordinator.hasLease(owner)).isTrue()
    verify(transaction, org.mockito.kotlin.never()).finish(org.mockito.kotlin.any())
  }

  @Test
  fun `restoring a nav transaction preserves an unrelated scope transaction`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(owner, transaction = transaction)
    fixture.acquireLease(incoming, state = Lifecycle.State.CREATED)
    fixture.scope.transaction = unrelatedTransaction

    navLeaseCoordinator.pauseCandidate(incoming, fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(unrelatedTransaction)
  }

  @Test
  fun `a finished nav transaction is discarded when its owner regains the lease`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(owner, transaction = transaction)
    fixture.acquireLease(incoming, state = Lifecycle.State.CREATED)
    whenever(transaction.isFinished).thenReturn(true)

    navLeaseCoordinator.pauseCandidate(incoming, fixture.scope)

    assertThat(fixture.scope.transaction).isNull()
    assertThat(navLeaseCoordinator.hasLease(owner)).isTrue()
  }

  @Test
  fun `unregistering the leaseholder finishes its transaction and clears its published nav data`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(owner, transaction = transaction)

    navLeaseCoordinator.unregisterCandidate(owner, fixture.scope)

    verify(transaction).finish(SpanStatus.OK)
    assertThat(fixture.scope.transaction).isNull()
    assertThat(fixture.cleared).containsExactly(owner)
    assertThat(navLeaseCoordinator.hasLease(owner)).isFalse()
  }

  @Test
  fun `unregistering a former leaseholder preserves the current owner's published nav data`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(previous)
    fixture.acquireLease(owner, state = Lifecycle.State.CREATED)
    navLeaseCoordinator.pauseCandidate(owner, fixture.scope)

    navLeaseCoordinator.unregisterCandidate(owner, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(previous)).isTrue()
    assertThat(fixture.cleared).isEmpty()
  }

  @Test
  fun `unregistering the leaseholder without a scope releases the nav lease`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(owner)

    navLeaseCoordinator.unregisterCandidate(owner, null)

    assertThat(navLeaseCoordinator.hasLease(owner)).isFalse()
    assertThat(fixture.cleared).isEmpty()
  }

  @Test
  fun `unregistering a former leaseholder finishes its retained transaction without clearing an unrelated one`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    whenever(transaction.status).thenReturn(SpanStatus.CANCELLED)
    fixture.acquireLease(owner, transaction = transaction)
    fixture.acquireLease(incoming, state = Lifecycle.State.CREATED)
    fixture.scope.transaction = unrelatedTransaction

    navLeaseCoordinator.unregisterCandidate(owner, fixture.scope)

    verify(transaction).finish(SpanStatus.CANCELLED)
    assertThat(fixture.scope.transaction).isSameInstanceAs(unrelatedTransaction)
    assertThat(fixture.cleared).isEmpty()
  }

  private class Fixture {
    val navLeaseCoordinator = NavLeaseCoordinator()
    val scope = Scope(SentryOptions())
    val published = mutableListOf<Any>()
    val cleared = mutableListOf<Any>()

    fun register(owner: Any, state: Lifecycle.State) {
      navLeaseCoordinator.registerCandidate(
        owner,
        state,
        onPublishData = { published += owner },
        onClearPublishedData = { cleared += owner },
      )
    }

    fun acquireLease(
      owner: Any,
      state: Lifecycle.State = Lifecycle.State.RESUMED,
      transaction: ITransaction? = null,
    ) {
      register(owner, state)
      navLeaseCoordinator.onBackStackUpdate(owner, scope)
      if (transaction != null) {
        navLeaseCoordinator.bindTransaction(owner, transaction)
        scope.transaction = transaction
      }
    }

    companion object {
      fun create(): Fixture = Fixture()
    }
  }
}
