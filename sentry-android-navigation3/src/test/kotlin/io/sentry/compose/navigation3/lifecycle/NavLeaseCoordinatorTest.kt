package io.sentry.compose.navigation3.lifecycle

import androidx.lifecycle.Lifecycle
import com.google.common.truth.Truth.assertThat
import io.sentry.IScope
import io.sentry.ISentryClient
import io.sentry.ITransaction
import io.sentry.Scope
import io.sentry.SentryOptions
import io.sentry.SpanStatus
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@Suppress("LargeClass")
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

    navLeaseCoordinator.registry.find(owner)?.start()
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

    navLeaseCoordinator.registry.find(owner)?.start()
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
  fun `an owner that isn't resumed doesn't take the nav lease from a resumed leaseholder`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(incumbent)

    fixture.acquireLease(owner, state = Lifecycle.State.STARTED)

    assertThat(navLeaseCoordinator.hasLease(incumbent)).isTrue()
    assertThat(fixture.published).containsExactly(incumbent)
  }

  @Test
  fun `an owner kept from the nav lease by a resumed leaseholder acquires it when it resumes`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(incumbent)
    fixture.acquireLease(owner, state = Lifecycle.State.STARTED)

    navLeaseCoordinator.resumeCandidate(owner, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(owner)).isTrue()
    assertThat(fixture.published).containsExactly(incumbent, owner).inOrder()
  }

  @Test
  fun `an owner that isn't resumed takes the nav lease from a paused leaseholder`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(incumbent)
    navLeaseCoordinator.pauseCandidate(incumbent, fixture.scope)

    fixture.acquireLease(owner, state = Lifecycle.State.CREATED)

    assertThat(navLeaseCoordinator.hasLease(owner)).isTrue()
  }

  @Test
  fun `an owner that isn't resumed takes the nav lease from a stopped leaseholder`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(incumbent)
    navLeaseCoordinator.pauseCandidate(incumbent, fixture.scope)
    navLeaseCoordinator.stopCandidate(incumbent, fixture.scope)

    fixture.acquireLease(owner, state = Lifecycle.State.CREATED)

    assertThat(navLeaseCoordinator.hasLease(owner)).isTrue()
    assertThat(fixture.published).containsExactly(incumbent, owner).inOrder()
  }

  @Test
  fun `a resumed owner takes the nav lease from a resumed leaseholder`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(incumbent)

    fixture.acquireLease(owner)

    assertThat(navLeaseCoordinator.hasLease(owner)).isTrue()
  }

  @Test
  fun `acquiring the nav lease clears the previous owner's published nav data before publishing`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    val calls = mutableListOf<String>()
    navLeaseCoordinator.registry.register(
      previous,
      Lifecycle.State.RESUMED,
      onPublishData = { calls += "publish previous" },
      onClearPublishedData = { calls += "clear previous" },
    )
    navLeaseCoordinator.onBackStackUpdate(previous, fixture.scope)
    navLeaseCoordinator.registry.register(
      incoming,
      Lifecycle.State.RESUMED,
      onPublishData = { calls += "publish incoming" },
      onClearPublishedData = { calls += "clear incoming" },
    )

    navLeaseCoordinator.onBackStackUpdate(incoming, fixture.scope)

    assertThat(calls)
      .containsExactly("publish previous", "clear previous", "publish incoming")
      .inOrder()
  }

  @Test
  fun `regaining the nav lease doesn't clear the owner's own published nav data`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(previous)
    fixture.acquireLease(owner)
    navLeaseCoordinator.pauseCandidate(owner, null)
    fixture.cleared.clear()

    navLeaseCoordinator.resumeCandidate(owner, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(owner)).isTrue()
    assertThat(fixture.published).containsExactly(previous, owner, owner).inOrder()
    assertThat(fixture.cleared).isEmpty()
  }

  @Test
  fun `releasing the nav lease leaves the owner's published nav data in place`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(previous)
    fixture.acquireLease(owner)
    fixture.cleared.clear()

    navLeaseCoordinator.pauseCandidate(owner, null)

    assertThat(navLeaseCoordinator.hasLease(owner)).isFalse()
    assertThat(fixture.cleared).isEmpty()
  }

  /**
   * An owner that leaves must not leave its nav data behind on the shared scope.
   *
   * The coordinator records which owner last published nav data. Each handoff uses that record to
   * clear the previous owner's screen and back stack, then publishes the incoming owner's. If the
   * clear attempt throws, the coordinator must still record the incoming owner as the one that last
   * published.
   *
   * This test runs two handoffs over three owners. The first owner publishes; the second takes the
   * lease; and then the first owner's clear attempt throws. The third then takes the lease. The
   * test verifies that third handoff clears the second owner's data, and not the first's. That lets
   * us ensure the second problem listed below doesn't happen.
   *
   * In the setup above, if the coordinator didn't record the second owner's data after the first
   * owner's clear attempt threw, two things would happen when handing off to the third owner:
   *
   * 1. Harmless: The handoff logic would try to clear the first owner's data again. That'd remove
   *    nothing because the scope no longer holds the values the first owner published.
   * 2. Problematic: The handoff would never clear the second owner's data. Instead, the second
   *    owner's data would stay on the scope, so later events would report stale nav data.
   */
  @Test
  fun `if a clear attempt throws during a handoff, the next handoff clears the new leaseholder's nav data`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    val failure = IllegalStateException("clear failed")
    var failClear = true
    val firstOwner = previous
    val secondOwner = incoming
    val thirdOwner = owner
    fixture.acquireLease(
      firstOwner,
      onClearPublishedData = {
        if (failClear) {
          failClear = false
          throw failure
        }
        fixture.cleared += firstOwner
      },
    )
    fixture.register(secondOwner, Lifecycle.State.RESUMED)

    val thrown =
      assertThrows(IllegalStateException::class.java) {
        navLeaseCoordinator.onBackStackUpdate(secondOwner, fixture.scope)
      }

    assertThat(thrown).isSameInstanceAs(failure)
    assertThat(navLeaseCoordinator.hasLease(secondOwner)).isTrue()

    fixture.acquireLease(thirdOwner)

    assertThat(fixture.cleared).containsExactly(secondOwner)
  }

  /**
   * The same guarantee as
   * [if a clear attempt throws during a handoff, the next handoff clears the new leaseholder's nav data],
   * but for a failure while publishing instead of while clearing.
   *
   * This case already worked before the fix, because the coordinator records the incoming owner
   * before it publishes. The test exists to catch a future change that moves the record after the
   * publish.
   */
  @Test
  fun `if a publish attempt throws during a handoff, the next handoff clears the new leaseholder's nav data`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    val failure = IllegalStateException("publish failed")
    val firstOwner = previous
    val secondOwner = incoming
    val thirdOwner = owner
    fixture.acquireLease(firstOwner)
    navLeaseCoordinator.registry.register(
      secondOwner,
      Lifecycle.State.RESUMED,
      onPublishData = { throw failure },
      onClearPublishedData = { fixture.cleared += secondOwner },
    )

    val thrown =
      assertThrows(IllegalStateException::class.java) {
        navLeaseCoordinator.onBackStackUpdate(secondOwner, fixture.scope)
      }

    assertThat(thrown).isSameInstanceAs(failure)
    assertThat(navLeaseCoordinator.hasLease(secondOwner)).isTrue()

    fixture.acquireLease(thirdOwner)

    assertThat(fixture.cleared).containsExactly(firstOwner, secondOwner).inOrder()
  }

  @Test
  fun `unregistering an owner that threw while clearing its nav data preserves the new leaseholder's nav data`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    val failure = IllegalStateException("clear failed")
    var failClear = true
    fixture.acquireLease(
      previous,
      onClearPublishedData = {
        if (failClear) {
          failClear = false
          throw failure
        }
        fixture.cleared += previous
      },
    )
    fixture.register(incoming, Lifecycle.State.RESUMED)
    assertThrows(IllegalStateException::class.java) {
      navLeaseCoordinator.onBackStackUpdate(incoming, fixture.scope)
    }

    navLeaseCoordinator.unregisterCandidate(previous, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(incoming)).isTrue()
    assertThat(fixture.cleared).isEmpty()
  }

  @Test
  fun `later back stack updates do not republish nav data or reclaim the nav lease`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    val first = Any()
    val second = Any()
    fixture.acquireLease(first)
    fixture.acquireLease(second, state = Lifecycle.State.RESUMED)

    navLeaseCoordinator.onBackStackUpdate(first, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(second)).isTrue()
    assertThat(fixture.published).containsExactly(first, second).inOrder()
  }

  @Test
  fun `pausing the leaseholder hands the nav lease to the most recently resumed owner that's still resumed`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    val older = Any()
    val recent = Any()
    fixture.acquireLease(older)
    fixture.acquireLease(recent)
    fixture.acquireLease(incoming, state = Lifecycle.State.RESUMED)

    navLeaseCoordinator.pauseCandidate(incoming, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(recent)).isTrue()
    assertThat(fixture.published.last()).isSameInstanceAs(recent)
  }

  @Test
  fun `pausing the leaseholder keeps the nav lease when other eligible owners are only paused`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(owner)
    navLeaseCoordinator.pauseCandidate(owner, fixture.scope)
    fixture.acquireLease(incoming)

    navLeaseCoordinator.pauseCandidate(incoming, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(incoming)).isTrue()
    assertThat(fixture.published).containsExactly(owner, incoming).inOrder()
  }

  @Test
  fun `a displaced paused owner regains the nav lease when it resumes`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(owner)
    navLeaseCoordinator.pauseCandidate(owner, fixture.scope)
    fixture.acquireLease(incoming)
    navLeaseCoordinator.pauseCandidate(incoming, fixture.scope)

    navLeaseCoordinator.resumeCandidate(owner, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(owner)).isTrue()
    assertThat(fixture.published).containsExactly(owner, incoming, owner).inOrder()
  }

  @Test
  fun `pausing the leaseholder skips a resumed owner without a back stack update`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    val eligible = Any()
    val uninitialized = Any()
    fixture.acquireLease(eligible)
    fixture.register(uninitialized, Lifecycle.State.RESUMED)
    fixture.acquireLease(incoming, state = Lifecycle.State.RESUMED)

    navLeaseCoordinator.pauseCandidate(incoming, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(eligible)).isTrue()
    assertThat(navLeaseCoordinator.hasLease(uninitialized)).isFalse()
  }

  @Test
  fun `pausing the only eligible owner keeps the nav lease`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(owner)

    navLeaseCoordinator.pauseCandidate(owner, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(owner)).isTrue()
    assertThat(fixture.published).containsExactly(owner)
  }

  @Test
  fun `pausing the only eligible owner without a scope keeps the nav lease`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(owner)

    navLeaseCoordinator.pauseCandidate(owner, null)

    assertThat(navLeaseCoordinator.hasLease(owner)).isTrue()
  }

  @Test
  fun `resuming a paused leaseholder does not republish its nav data`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(owner)
    navLeaseCoordinator.pauseCandidate(owner, fixture.scope)

    navLeaseCoordinator.resumeCandidate(owner, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(owner)).isTrue()
    assertThat(fixture.published).containsExactly(owner)
  }

  @Test
  fun `stopping the leaseholder restores a resumed owner`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(previous)
    fixture.acquireLease(owner, state = Lifecycle.State.RESUMED)

    navLeaseCoordinator.stopCandidate(owner, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(previous)).isTrue()
    assertThat(fixture.published.last()).isSameInstanceAs(previous)
  }

  @Test
  fun `stopping the only eligible owner keeps the nav lease`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(owner)
    navLeaseCoordinator.pauseCandidate(owner, fixture.scope)

    navLeaseCoordinator.stopCandidate(owner, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(owner)).isTrue()
    assertThat(fixture.published).containsExactly(owner)
  }

  @Test
  fun `stopping the only eligible owner without a scope keeps the nav lease`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(owner)
    navLeaseCoordinator.pauseCandidate(owner, null)

    navLeaseCoordinator.stopCandidate(owner, null)

    assertThat(navLeaseCoordinator.hasLease(owner)).isTrue()
  }

  @Test
  fun `stopping the leaseholder doesn't hand the nav lease to a paused owner`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(previous)
    navLeaseCoordinator.pauseCandidate(previous, fixture.scope)
    fixture.acquireLease(owner)
    navLeaseCoordinator.pauseCandidate(owner, fixture.scope)

    navLeaseCoordinator.stopCandidate(owner, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(owner)).isTrue()
    assertThat(navLeaseCoordinator.hasLease(previous)).isFalse()
    assertThat(fixture.published).containsExactly(previous, owner).inOrder()
    assertThat(fixture.cleared).containsExactly(previous)
  }

  @Test
  fun `a paused owner passed over when the leaseholder stopped gets the nav lease when it resumes`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(previous)
    navLeaseCoordinator.pauseCandidate(previous, fixture.scope)
    fixture.acquireLease(owner)
    navLeaseCoordinator.pauseCandidate(owner, fixture.scope)
    navLeaseCoordinator.stopCandidate(owner, fixture.scope)

    navLeaseCoordinator.resumeCandidate(previous, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(previous)).isTrue()
    assertThat(fixture.published).containsExactly(previous, owner, previous).inOrder()
  }

  @Test
  fun `restarting a stopped leaseholder keeps the nav lease`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(owner)
    navLeaseCoordinator.pauseCandidate(owner, fixture.scope)
    navLeaseCoordinator.stopCandidate(owner, fixture.scope)

    navLeaseCoordinator.registry.find(owner)?.start()

    assertThat(navLeaseCoordinator.hasLease(owner)).isTrue()
    assertThat(fixture.published).containsExactly(owner)
  }

  @Test
  fun `stopping an owner excludes it from later nav lease fallback`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(previous)
    fixture.acquireLease(owner)
    navLeaseCoordinator.pauseCandidate(previous, fixture.scope)
    navLeaseCoordinator.stopCandidate(previous, fixture.scope)

    navLeaseCoordinator.unregisterCandidate(owner, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(previous)).isFalse()
    assertThat(fixture.published).containsExactly(previous, owner).inOrder()
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
    fixture.acquireLease(owner, state = Lifecycle.State.RESUMED)

    navLeaseCoordinator.pauseCandidate(owner, null)

    assertThat(navLeaseCoordinator.hasLease(owner)).isFalse()
    assertThat(navLeaseCoordinator.hasLease(previous)).isFalse()
    assertThat(fixture.published).containsExactly(previous, owner).inOrder()
  }

  @Test
  fun `stopping the leaseholder without a scope releases the nav lease without publishing a fallback`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(previous)
    fixture.acquireLease(owner, state = Lifecycle.State.RESUMED)

    navLeaseCoordinator.stopCandidate(owner, null)

    assertThat(navLeaseCoordinator.hasLease(owner)).isFalse()
    assertThat(navLeaseCoordinator.hasLease(previous)).isFalse()
    assertThat(fixture.published).containsExactly(previous, owner).inOrder()
  }

  @Test
  fun `an unregistered owner's nav transaction is not retained`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    val other = Any()
    fixture.acquireLease(owner)

    navLeaseCoordinator.retainTransaction(other, transaction, fixture.scope)
    navLeaseCoordinator.unregisterCandidate(owner, fixture.scope)

    verifyNoInteractions(transaction)
  }

  @Test
  fun `a nav transaction recorded without the lease is ignored`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(owner)
    fixture.acquireLease(incoming, state = Lifecycle.State.RESUMED)

    navLeaseCoordinator.retainTransaction(owner, transaction, fixture.scope)
    navLeaseCoordinator.unregisterCandidate(incoming, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(owner)).isTrue()
    assertThat(navLeaseCoordinator.registry.retainsTransaction(transaction)).isFalse()
    assertThat(fixture.scope.transaction).isNull()

    navLeaseCoordinator.unregisterCandidate(owner, fixture.scope)

    verifyNoInteractions(transaction)
  }

  @Test
  fun `an unfinished nav transaction is restored when its owner regains the lease`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(owner, transaction = transaction)
    fixture.acquireLease(incoming, state = Lifecycle.State.RESUMED)

    assertThat(fixture.scope.transaction).isNull()
    navLeaseCoordinator.pauseCandidate(incoming, fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(transaction)
    assertThat(navLeaseCoordinator.hasLease(owner)).isTrue()
    verify(transaction, never()).finish(any())
  }

  @Test
  fun `restoring a nav transaction preserves an unrelated scope transaction`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(owner, transaction = transaction)
    fixture.acquireLease(incoming, state = Lifecycle.State.RESUMED)
    fixture.scope.transaction = unrelatedTransaction

    navLeaseCoordinator.pauseCandidate(incoming, fixture.scope)

    assertThat(fixture.scope.transaction).isSameInstanceAs(unrelatedTransaction)
  }

  @Test
  fun `a finished nav transaction is discarded when its owner regains the lease`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(owner, transaction = transaction)
    fixture.acquireLease(incoming, state = Lifecycle.State.RESUMED)
    whenever(transaction.isFinished).thenReturn(true)

    navLeaseCoordinator.pauseCandidate(incoming, fixture.scope)

    assertThat(fixture.scope.transaction).isNull()
    assertThat(navLeaseCoordinator.hasLease(owner)).isTrue()
    assertThat(navLeaseCoordinator.registry.retainsTransaction(transaction)).isFalse()
  }

  @Test
  fun `a nav transaction from a disabled client is discarded when its owner regains the lease`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(owner, transaction = transaction)
    fixture.acquireLease(incoming)
    whenever(fixture.client.isEnabled).thenReturn(false)

    navLeaseCoordinator.pauseCandidate(incoming, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(owner)).isTrue()
    assertThat(fixture.scope.transaction).isNull()
    assertThat(navLeaseCoordinator.registry.retainsTransaction(transaction)).isFalse()
    verify(transaction, never()).finish(any())
  }

  @Test
  fun `a nav transaction from a replaced client is discarded when its owner regains the lease`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(owner, transaction = transaction)
    fixture.acquireLease(incoming)
    fixture.scope.bindClient(mock<ISentryClient> { on { isEnabled }.thenReturn(true) })

    navLeaseCoordinator.pauseCandidate(incoming, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(owner)).isTrue()
    assertThat(fixture.scope.transaction).isNull()
    assertThat(navLeaseCoordinator.registry.retainsTransaction(transaction)).isFalse()
    verify(transaction, never()).finish(any())
  }

  @Test
  fun `a released unfinished nav transaction is not restored when its owner regains the lease`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(owner, transaction = transaction)
    fixture.acquireLease(incoming, state = Lifecycle.State.RESUMED)

    navLeaseCoordinator.registry.find(owner)?.releaseTransaction(transaction)
    navLeaseCoordinator.pauseCandidate(incoming, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(owner)).isTrue()
    assertThat(fixture.scope.transaction).isNull()
    assertThat(navLeaseCoordinator.registry.retainsTransaction(transaction)).isFalse()
  }

  @Test
  fun `releasing a nav transaction other than the retained one keeps the retained one restorable`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(owner, transaction = transaction)
    fixture.acquireLease(incoming, state = Lifecycle.State.RESUMED)

    navLeaseCoordinator.registry.find(owner)?.releaseTransaction(unrelatedTransaction)
    navLeaseCoordinator.pauseCandidate(incoming, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(owner)).isTrue()
    assertThat(fixture.scope.transaction).isSameInstanceAs(transaction)
  }

  @Test
  fun `a displaced owner's retained nav transaction remains identifiable until unregistering`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(owner, transaction = transaction)
    fixture.acquireLease(incoming, state = Lifecycle.State.RESUMED)

    assertThat(navLeaseCoordinator.registry.retainsTransaction(transaction)).isTrue()
    assertThat(navLeaseCoordinator.registry.retainsTransaction(unrelatedTransaction)).isFalse()

    navLeaseCoordinator.unregisterCandidate(owner, fixture.scope)

    assertThat(navLeaseCoordinator.registry.retainsTransaction(transaction)).isFalse()
  }

  @Test
  fun `acquiring the nav lease binds its retained transaction before publishing nav data`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    val transactionAtPublication = mutableListOf<ITransaction?>()
    navLeaseCoordinator.registry.register(
      owner,
      Lifecycle.State.RESUMED,
      onPublishData = { transactionAtPublication += it.transaction },
      onClearPublishedData = {},
    )
    navLeaseCoordinator.onBackStackUpdate(owner, fixture.scope)
    navLeaseCoordinator.retainTransaction(owner, transaction, fixture.scope)
    fixture.scope.transaction = transaction
    fixture.acquireLease(incoming, state = Lifecycle.State.RESUMED)

    navLeaseCoordinator.pauseCandidate(incoming, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(owner)).isTrue()
    assertThat(transactionAtPublication).containsExactly(null, transaction).inOrder()
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
  fun `unregistering an owner does not finish a nav transaction from a replaced client`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(owner, transaction = transaction)
    fixture.scope.bindClient(mock<ISentryClient> { on { isEnabled }.thenReturn(true) })

    navLeaseCoordinator.unregisterCandidate(owner, fixture.scope)

    verify(transaction, never()).finish(any())
    assertThat(navLeaseCoordinator.registry.retainsTransaction(transaction)).isFalse()
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `unregistering an owner does not finish a nav transaction from a disabled client`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(owner, transaction = transaction)
    whenever(fixture.client.isEnabled).thenReturn(false)

    navLeaseCoordinator.unregisterCandidate(owner, fixture.scope)

    verify(transaction, never()).finish(any())
    assertThat(navLeaseCoordinator.registry.retainsTransaction(transaction)).isFalse()
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `unregistering a former leaseholder preserves the current owner's published nav data`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(previous)
    fixture.acquireLease(owner, state = Lifecycle.State.RESUMED)
    navLeaseCoordinator.pauseCandidate(owner, fixture.scope)
    fixture.cleared.clear()

    navLeaseCoordinator.unregisterCandidate(owner, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(previous)).isTrue()
    assertThat(fixture.cleared).isEmpty()
  }

  @Test
  fun `unregistering the leaseholder hands the nav lease to the most recently resumed eligible owner`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    val older = Any()
    fixture.acquireLease(older)
    navLeaseCoordinator.pauseCandidate(older, fixture.scope)
    fixture.acquireLease(previous)
    navLeaseCoordinator.pauseCandidate(previous, fixture.scope)
    fixture.acquireLease(owner)

    navLeaseCoordinator.unregisterCandidate(owner, fixture.scope)

    assertThat(navLeaseCoordinator.hasLease(previous)).isTrue()
    assertThat(fixture.published.last()).isSameInstanceAs(previous)
    assertThat(fixture.cleared).containsExactly(older, previous, owner).inOrder()
  }

  @Test
  fun `unregistering the leaseholder without a scope releases the nav lease`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(previous)
    fixture.acquireLease(owner)
    fixture.cleared.clear()

    navLeaseCoordinator.unregisterCandidate(owner, null)

    assertThat(navLeaseCoordinator.hasLease(owner)).isFalse()
    assertThat(navLeaseCoordinator.hasLease(previous)).isFalse()
    assertThat(fixture.published).containsExactly(previous, owner).inOrder()
    assertThat(fixture.cleared).isEmpty()
  }

  @Test
  fun `unregistering the leaseholder without a scope doesn't finish its nav transaction`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    fixture.acquireLease(owner, transaction = transaction)

    navLeaseCoordinator.unregisterCandidate(owner, null)

    verify(transaction, never()).finish(any())
  }

  @Test
  fun `unregistering a former leaseholder finishes its retained transaction without clearing an unrelated one`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    whenever(transaction.status).thenReturn(SpanStatus.CANCELLED)
    fixture.acquireLease(owner, transaction = transaction)
    fixture.acquireLease(incoming, state = Lifecycle.State.RESUMED)
    fixture.scope.transaction = unrelatedTransaction
    fixture.cleared.clear()

    navLeaseCoordinator.unregisterCandidate(owner, fixture.scope)

    verify(transaction).finish(SpanStatus.CANCELLED)
    assertThat(fixture.scope.transaction).isSameInstanceAs(unrelatedTransaction)
    assertThat(fixture.cleared).isEmpty()
  }

  @Test
  fun `unregistering the leaseholder forgets the owner when finishing its transaction throws`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    val failure = IllegalStateException("finish failed")
    fixture.acquireLease(owner, transaction = transaction)
    doThrow(failure).whenever(transaction).finish(SpanStatus.OK)

    val thrown =
      assertThrows(IllegalStateException::class.java) {
        navLeaseCoordinator.unregisterCandidate(owner, fixture.scope)
      }

    assertThat(thrown).isSameInstanceAs(failure)
    assertThat(navLeaseCoordinator.hasLease(owner)).isFalse()
    assertThat(navLeaseCoordinator.registry.retainsTransaction(transaction)).isFalse()

    fixture.published.clear()
    navLeaseCoordinator.resumeCandidate(owner, fixture.scope)
    fixture.acquireLease(incoming)

    assertThat(fixture.published).containsExactly(incoming)
    assertThat(fixture.cleared).isEmpty()
  }

  @Test
  fun `unregistering the leaseholder forgets the owner when unbinding its transaction throws`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    val failure = IllegalStateException("unbind failed")
    val failingScope = mock<IScope>()
    fixture.acquireLease(owner, transaction = transaction)
    doThrow(failure).whenever(failingScope).withTransaction(any())

    val thrown =
      assertThrows(IllegalStateException::class.java) {
        navLeaseCoordinator.unregisterCandidate(owner, failingScope)
      }

    assertThat(thrown).isSameInstanceAs(failure)
    assertThat(navLeaseCoordinator.hasLease(owner)).isFalse()
    assertThat(navLeaseCoordinator.registry.retainsTransaction(transaction)).isFalse()

    fixture.published.clear()
    navLeaseCoordinator.resumeCandidate(owner, fixture.scope)
    fixture.acquireLease(incoming)

    assertThat(fixture.published).containsExactly(incoming)
    assertThat(fixture.cleared).isEmpty()
  }

  @Test
  fun `unregistering the leaseholder forgets the owner when clearing its published nav data throws`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    val failure = IllegalStateException("clear failed")
    fixture.acquireLease(owner, onClearPublishedData = { throw failure })

    val thrown =
      assertThrows(IllegalStateException::class.java) {
        navLeaseCoordinator.unregisterCandidate(owner, fixture.scope)
      }

    assertThat(thrown).isSameInstanceAs(failure)
    assertThat(navLeaseCoordinator.hasLease(owner)).isFalse()

    fixture.published.clear()
    navLeaseCoordinator.resumeCandidate(owner, fixture.scope)
    fixture.acquireLease(incoming)

    assertThat(fixture.published).containsExactly(incoming)
    assertThat(navLeaseCoordinator.hasLease(incoming)).isTrue()
  }

  @Test
  fun `unregistering the leaseholder forgets the owner when restoring a fallback transaction throws`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    val failure = IllegalStateException("fallback restoration failed")
    val failingScope = mock<IScope>()
    fixture.acquireLease(previous, transaction = transaction)
    fixture.acquireLease(owner)
    doThrow(failure).whenever(failingScope).withTransaction(any())

    val thrown =
      assertThrows(IllegalStateException::class.java) {
        navLeaseCoordinator.unregisterCandidate(owner, failingScope)
      }

    assertThat(thrown).isSameInstanceAs(failure)
    assertThat(navLeaseCoordinator.hasLease(owner)).isFalse()
    assertThat(navLeaseCoordinator.registry.retainsTransaction(transaction)).isTrue()

    fixture.published.clear()
    fixture.cleared.clear()
    navLeaseCoordinator.resumeCandidate(owner, fixture.scope)
    fixture.acquireLease(incoming)

    assertThat(fixture.published).containsExactly(incoming)
    assertThat(fixture.cleared).doesNotContain(owner)
  }

  @Test
  fun `unregistering the leaseholder preserves the fallback lease when publishing fallback nav data throws`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    val failure = IllegalStateException("fallback publication failed")
    var failPublication = false
    navLeaseCoordinator.registry.register(
      previous,
      Lifecycle.State.RESUMED,
      onPublishData = {
        if (failPublication) {
          throw failure
        }
      },
      onClearPublishedData = {},
    )
    navLeaseCoordinator.onBackStackUpdate(previous, fixture.scope)
    fixture.acquireLease(owner, transaction = transaction)
    failPublication = true

    val thrown =
      assertThrows(IllegalStateException::class.java) {
        navLeaseCoordinator.unregisterCandidate(owner, fixture.scope)
      }

    assertThat(thrown).isSameInstanceAs(failure)
    assertThat(navLeaseCoordinator.hasLease(owner)).isFalse()
    assertThat(navLeaseCoordinator.hasLease(previous)).isTrue()
    assertThat(navLeaseCoordinator.registry.retainsTransaction(transaction)).isFalse()

    fixture.published.clear()
    navLeaseCoordinator.resumeCandidate(owner, fixture.scope)

    assertThat(fixture.published).isEmpty()
    assertThat(navLeaseCoordinator.hasLease(previous)).isTrue()
  }

  private class Fixture {
    val navLeaseCoordinator = NavLeaseCoordinator()
    val scope = Scope(SentryOptions())
    val client = mock<ISentryClient> { on { isEnabled }.thenReturn(true) }
    val published = mutableListOf<Any>()
    val cleared = mutableListOf<Any>()

    init {
      scope.bindClient(client)
    }

    fun register(
      owner: Any,
      state: Lifecycle.State,
      onClearPublishedData: (IScope) -> Unit = { cleared += owner },
    ) {
      navLeaseCoordinator.registry.register(
        owner,
        state,
        onPublishData = { published += owner },
        onClearPublishedData = onClearPublishedData,
      )
    }

    fun acquireLease(
      owner: Any,
      state: Lifecycle.State = Lifecycle.State.RESUMED,
      transaction: ITransaction? = null,
      onClearPublishedData: (IScope) -> Unit = { cleared += owner },
    ) {
      register(owner, state, onClearPublishedData)
      navLeaseCoordinator.onBackStackUpdate(owner, scope)
      if (transaction != null) {
        navLeaseCoordinator.retainTransaction(owner, transaction, scope)
        scope.transaction = transaction
      }
    }

    companion object {
      fun create(): Fixture = Fixture()
    }
  }
}
