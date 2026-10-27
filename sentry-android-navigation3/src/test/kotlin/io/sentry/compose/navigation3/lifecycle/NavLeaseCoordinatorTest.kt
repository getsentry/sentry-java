package io.sentry.compose.navigation3.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State.CREATED
import androidx.lifecycle.Lifecycle.State.DESTROYED
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.common.truth.Truth.assertThat
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryOptions
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@Suppress("LargeClass")
class NavLeaseCoordinatorTest {

  // region registration

  @Test
  fun `register() - returns no registration for a destroyed lifecycle and doesn't observe it`() {
    val fixture = Fixture.create()
    val navLeaseCoordinator = fixture.navLeaseCoordinator
    val owner = TestOwner(DESTROYED)

    val registration =
      navLeaseCoordinator.register(owner.lifecycle, fixture.scopes, fixture.navState("owner"))

    assertThat(registration).isNull()
    assertThat(owner.lifecycle.observerCount).isEqualTo(0)
  }

  @Test
  fun `separate registrations for the same lifecycle don't share the nav lease`() {
    val fixture = Fixture.create()
    val owner = TestOwner(RESUMED)
    val first = fixture.acquireLease("first", owner)

    val second = fixture.acquireLease("second", owner)

    assertThat(first.hasLease).isFalse()
    assertThat(second.hasLease).isTrue()
  }

  // endregion

  // region acquiring the nav lease on the first back stack update

  @Test
  fun `a created owner acquires the nav lease on its first back stack update`() {
    val fixture = Fixture.create()
    val registration = fixture.register("owner", TestOwner(CREATED))

    registration.onBackStackUpdate()

    assertThat(registration.hasLease).isTrue()
    assertThat(fixture.published).containsExactly("owner")
  }

  @Test
  fun `starting a created owner leaves it eligible to acquire the nav lease on its first back stack update`() {
    val fixture = Fixture.create()
    val owner = TestOwner(CREATED)
    val registration = fixture.register("owner", owner)

    owner.lifecycle.currentState = STARTED
    registration.onBackStackUpdate()

    assertThat(registration.hasLease).isTrue()
  }

  @Test
  fun `restarting a stopped owner does not take the nav lease on its first back stack update`() {
    val fixture = Fixture.create()
    val owner = TestOwner(STARTED)
    val registration = fixture.register("owner", owner)
    owner.lifecycle.currentState = CREATED
    val incumbentOwner = TestOwner(RESUMED)
    val incumbent = fixture.acquireLease("incumbent", incumbentOwner)
    // The incumbent keeps the lease, but no longer blocks a first update from taking it.
    incumbentOwner.lifecycle.currentState = STARTED

    owner.lifecycle.currentState = STARTED
    registration.onBackStackUpdate()

    assertThat(incumbent.hasLease).isTrue()
    assertThat(fixture.published).containsExactly("incumbent")
  }

  @Test
  fun `resuming an owner before its first back stack update leaves the nav lease unassigned`() {
    val fixture = Fixture.create()
    val owner = TestOwner(CREATED)
    val registration = fixture.register("owner", owner)

    owner.lifecycle.currentState = RESUMED

    assertThat(registration.hasLease).isFalse()
    assertThat(fixture.published).isEmpty()
  }

  @Test
  fun `resuming a stopped owner after its first back stack update transfers the nav lease`() {
    val fixture = Fixture.create()
    fixture.acquireLease("incumbent", TestOwner(RESUMED))
    val owner = TestOwner(STARTED)
    val registration = fixture.register("owner", owner)
    owner.lifecycle.currentState = CREATED
    registration.onBackStackUpdate()

    owner.lifecycle.currentState = RESUMED

    assertThat(registration.hasLease).isTrue()
    assertThat(fixture.published).containsExactly("incumbent", "owner").inOrder()
  }

  @Test
  fun `a paused owner cannot acquire the nav lease on its first back stack update`() {
    val fixture = Fixture.create()
    val owner = TestOwner(RESUMED)
    val registration = fixture.register("owner", owner)
    owner.lifecycle.currentState = STARTED

    registration.onBackStackUpdate()

    assertThat(registration.hasLease).isFalse()
    assertThat(fixture.published).isEmpty()
  }

  @Test
  fun `an owner that isn't resumed doesn't take the nav lease from a resumed leaseholder`() {
    val fixture = Fixture.create()
    val incumbent = fixture.acquireLease("incumbent", TestOwner(RESUMED))

    fixture.acquireLease("owner", TestOwner(STARTED))

    assertThat(incumbent.hasLease).isTrue()
    assertThat(fixture.published).containsExactly("incumbent")
  }

  @Test
  fun `an owner kept from the nav lease by a resumed leaseholder acquires it when it resumes`() {
    val fixture = Fixture.create()
    fixture.acquireLease("incumbent", TestOwner(RESUMED))
    val owner = TestOwner(STARTED)
    val registration = fixture.acquireLease("owner", owner)

    owner.lifecycle.currentState = RESUMED

    assertThat(registration.hasLease).isTrue()
    assertThat(fixture.published).containsExactly("incumbent", "owner").inOrder()
  }

  /**
   * `NavScopes` relies on this: it starts a nav transaction deferred without the lease when the
   * lease is acquired, which must not coincide with a back stack update that starts a transaction
   * of its own.
   */
  @Test
  fun `an owner kept from the nav lease on its first back stack update doesn't take it on a later one`() {
    val fixture = Fixture.create()
    val incumbentOwner = TestOwner(RESUMED)
    val incumbent = fixture.acquireLease("incumbent", incumbentOwner)
    val registration = fixture.acquireLease("owner", TestOwner(STARTED))
    // The incumbent keeps the lease, but no longer blocks a first update from taking it.
    incumbentOwner.lifecycle.currentState = STARTED

    registration.onBackStackUpdate()

    assertThat(incumbent.hasLease).isTrue()
    assertThat(fixture.published).containsExactly("incumbent")
  }

  @Test
  fun `an owner that isn't resumed takes the nav lease from a paused leaseholder`() {
    val fixture = Fixture.create()
    val incumbentOwner = TestOwner(RESUMED)
    fixture.acquireLease("incumbent", incumbentOwner)
    incumbentOwner.lifecycle.currentState = STARTED

    val registration = fixture.acquireLease("owner", TestOwner(CREATED))

    assertThat(registration.hasLease).isTrue()
  }

  @Test
  fun `an owner that isn't resumed takes the nav lease from a stopped leaseholder`() {
    val fixture = Fixture.create()
    val incumbentOwner = TestOwner(RESUMED)
    fixture.acquireLease("incumbent", incumbentOwner)
    incumbentOwner.lifecycle.currentState = CREATED

    val registration = fixture.acquireLease("owner", TestOwner(CREATED))

    assertThat(registration.hasLease).isTrue()
    assertThat(fixture.published).containsExactly("incumbent", "owner").inOrder()
  }

  @Test
  fun `a resumed owner takes the nav lease from a resumed leaseholder`() {
    val fixture = Fixture.create()
    fixture.acquireLease("incumbent", TestOwner(RESUMED))

    val registration = fixture.acquireLease("owner", TestOwner(RESUMED))

    assertThat(registration.hasLease).isTrue()
  }

  @Test
  fun `acquiring the nav lease unpublishes the previous publisher's nav state before publishing its own`() {
    val fixture = Fixture.create()
    val calls = mutableListOf<String>()
    fixture.acquireLease(
      "previous",
      TestOwner(RESUMED),
      navState =
        fixture.navState(
          "previous",
          onPublishTo = { calls += "publish previous" },
          onUnpublishFrom = { calls += "unpublish previous" },
        ),
    )
    val incoming =
      fixture.register(
        "incoming",
        TestOwner(RESUMED),
        fixture.navState(
          "incoming",
          onPublishTo = { calls += "publish incoming" },
          onUnpublishFrom = { calls += "unpublish incoming" },
        ),
      )

    incoming.onBackStackUpdate()

    assertThat(calls)
      .containsExactly("publish previous", "unpublish previous", "publish incoming")
      .inOrder()
  }

  @Test
  fun `regaining the nav lease doesn't unpublish the owner's own published nav state`() {
    val fixture = Fixture.create()
    fixture.acquireLease("previous", TestOwner(RESUMED))
    val owner = TestOwner(RESUMED)
    val registration = fixture.acquireLease("owner", owner)
    fixture.withoutScope { owner.lifecycle.currentState = STARTED }
    fixture.unpublished.clear()

    owner.lifecycle.currentState = RESUMED

    assertThat(registration.hasLease).isTrue()
    assertThat(fixture.published).containsExactly("previous", "owner", "owner").inOrder()
    assertThat(fixture.unpublished).isEmpty()
  }

  @Test
  fun `releasing the nav lease leaves the owner's published nav state in place`() {
    val fixture = Fixture.create()
    fixture.acquireLease("previous", TestOwner(RESUMED))
    val owner = TestOwner(RESUMED)
    val registration = fixture.acquireLease("owner", owner)
    fixture.unpublished.clear()

    fixture.withoutScope { owner.lifecycle.currentState = STARTED }

    assertThat(registration.hasLease).isFalse()
    assertThat(fixture.unpublished).isEmpty()
  }

  /**
   * The coordinator records the incoming owner as the publisher before calling into the scope, so
   * unpublishing that throws mid-handoff doesn't strand the incoming owner's nav state: the next
   * handoff still unpublishes it.
   */
  @Test
  fun `if unpublishing throws during a handoff, the next handoff unpublishes the new leaseholder's nav state`() {
    val fixture = Fixture.create()
    val failure = IllegalStateException("unpublish failed")
    fixture.acquireLease(
      "first",
      TestOwner(RESUMED),
      navState = fixture.navState("first", onUnpublishFrom = { throw failure }),
    )
    val second = fixture.register("second", TestOwner(RESUMED))

    val thrown = assertThrows(IllegalStateException::class.java) { second.onBackStackUpdate() }

    assertThat(thrown).isSameInstanceAs(failure)
    assertThat(second.hasLease).isTrue()

    fixture.acquireLease("third", TestOwner(RESUMED))

    assertThat(fixture.unpublished).containsExactly("second")
  }

  @Test
  fun `later back stack updates do not republish nav state or reclaim the nav lease`() {
    val fixture = Fixture.create()
    val first = fixture.acquireLease("first", TestOwner(RESUMED))
    val second = fixture.acquireLease("second", TestOwner(RESUMED))

    first.onBackStackUpdate()

    assertThat(second.hasLease).isTrue()
    assertThat(fixture.published).containsExactly("first", "second").inOrder()
  }

  @Test
  fun `a back stack update skipped by configureScope doesn't acquire the nav lease`() {
    val fixture = Fixture.create()
    val registration = fixture.register("owner", TestOwner(CREATED))

    fixture.withoutScope { registration.onBackStackUpdate() }

    assertThat(registration.hasLease).isFalse()
    assertThat(fixture.published).isEmpty()
  }

  @Test
  fun `a back stack update skipped by configureScope doesn't count as the first back stack update`() {
    val fixture = Fixture.create()
    val registration = fixture.register("owner", TestOwner(CREATED))
    fixture.withoutScope { registration.onBackStackUpdate() }

    registration.onBackStackUpdate()

    assertThat(registration.hasLease).isTrue()
    assertThat(fixture.published).containsExactly("owner")
  }

  // endregion

  // region pausing

  @Test
  fun `pausing the leaseholder hands the nav lease to the most recently resumed owner that's still resumed`() {
    val fixture = Fixture.create()
    fixture.acquireLease("older", TestOwner(RESUMED))
    val recent = fixture.acquireLease("recent", TestOwner(RESUMED))
    val incomingOwner = TestOwner(RESUMED)
    fixture.acquireLease("incoming", incomingOwner)

    incomingOwner.lifecycle.currentState = STARTED

    assertThat(recent.hasLease).isTrue()
    assertThat(fixture.published.last()).isEqualTo("recent")
  }

  @Test
  fun `pausing the leaseholder keeps the nav lease when other eligible owners are only paused`() {
    val fixture = Fixture.create()
    val owner = TestOwner(RESUMED)
    fixture.acquireLease("owner", owner)
    owner.lifecycle.currentState = STARTED
    val incomingOwner = TestOwner(RESUMED)
    val incoming = fixture.acquireLease("incoming", incomingOwner)

    incomingOwner.lifecycle.currentState = STARTED

    assertThat(incoming.hasLease).isTrue()
    assertThat(fixture.published).containsExactly("owner", "incoming").inOrder()
  }

  @Test
  fun `a displaced paused owner regains the nav lease when it resumes`() {
    val fixture = Fixture.create()
    val owner = TestOwner(RESUMED)
    val registration = fixture.acquireLease("owner", owner)
    owner.lifecycle.currentState = STARTED
    val incomingOwner = TestOwner(RESUMED)
    fixture.acquireLease("incoming", incomingOwner)
    incomingOwner.lifecycle.currentState = STARTED

    owner.lifecycle.currentState = RESUMED

    assertThat(registration.hasLease).isTrue()
    assertThat(fixture.published).containsExactly("owner", "incoming", "owner").inOrder()
  }

  @Test
  fun `pausing the leaseholder skips a resumed owner without a back stack update`() {
    val fixture = Fixture.create()
    val eligible = fixture.acquireLease("eligible", TestOwner(RESUMED))
    val uninitialized = fixture.register("uninitialized", TestOwner(RESUMED))
    val incomingOwner = TestOwner(RESUMED)
    fixture.acquireLease("incoming", incomingOwner)

    incomingOwner.lifecycle.currentState = STARTED

    assertThat(eligible.hasLease).isTrue()
    assertThat(uninitialized.hasLease).isFalse()
  }

  @Test
  fun `pausing the only eligible owner keeps the nav lease`() {
    val fixture = Fixture.create()
    val owner = TestOwner(RESUMED)
    val registration = fixture.acquireLease("owner", owner)

    owner.lifecycle.currentState = STARTED

    assertThat(registration.hasLease).isTrue()
    assertThat(fixture.published).containsExactly("owner")
  }

  @Test
  fun `pausing the only eligible owner without a scope keeps the nav lease`() {
    val fixture = Fixture.create()
    val owner = TestOwner(RESUMED)
    val registration = fixture.acquireLease("owner", owner)

    fixture.withoutScope { owner.lifecycle.currentState = STARTED }

    assertThat(registration.hasLease).isTrue()
  }

  @Test
  fun `resuming a paused leaseholder does not republish its nav state`() {
    val fixture = Fixture.create()
    val owner = TestOwner(RESUMED)
    val registration = fixture.acquireLease("owner", owner)
    owner.lifecycle.currentState = STARTED

    owner.lifecycle.currentState = RESUMED

    assertThat(registration.hasLease).isTrue()
    assertThat(fixture.published).containsExactly("owner")
  }

  // endregion

  // region stopping

  /**
   * A real lifecycle pauses before it stops, so the leaseholder here is an owner that acquired the
   * lease without ever resuming, and the other owner resumes without a scope, which records the
   * resume without restoring its lease.
   */
  @Test
  fun `stopping the leaseholder restores a resumed owner`() {
    val fixture = Fixture.create()
    val previousOwner = TestOwner(RESUMED)
    val previous = fixture.acquireLease("previous", previousOwner)
    previousOwner.lifecycle.currentState = STARTED
    val owner = TestOwner(STARTED)
    fixture.acquireLease("owner", owner)
    fixture.withoutScope { previousOwner.lifecycle.currentState = RESUMED }

    owner.lifecycle.currentState = CREATED

    assertThat(previous.hasLease).isTrue()
    assertThat(fixture.published.last()).isEqualTo("previous")
  }

  @Test
  fun `stopping the only eligible owner keeps the nav lease`() {
    val fixture = Fixture.create()
    val owner = TestOwner(RESUMED)
    val registration = fixture.acquireLease("owner", owner)
    owner.lifecycle.currentState = STARTED

    owner.lifecycle.currentState = CREATED

    assertThat(registration.hasLease).isTrue()
    assertThat(fixture.published).containsExactly("owner")
  }

  @Test
  fun `stopping the only eligible owner without a scope keeps the nav lease`() {
    val fixture = Fixture.create()
    val owner = TestOwner(RESUMED)
    val registration = fixture.acquireLease("owner", owner)
    fixture.withoutScope { owner.lifecycle.currentState = STARTED }

    fixture.withoutScope { owner.lifecycle.currentState = CREATED }

    assertThat(registration.hasLease).isTrue()
  }

  @Test
  fun `stopping the leaseholder doesn't hand the nav lease to a paused owner`() {
    val fixture = Fixture.create()
    val previousOwner = TestOwner(RESUMED)
    val previous = fixture.acquireLease("previous", previousOwner)
    previousOwner.lifecycle.currentState = STARTED
    val owner = TestOwner(RESUMED)
    val registration = fixture.acquireLease("owner", owner)
    owner.lifecycle.currentState = STARTED

    owner.lifecycle.currentState = CREATED

    assertThat(registration.hasLease).isTrue()
    assertThat(previous.hasLease).isFalse()
    assertThat(fixture.published).containsExactly("previous", "owner").inOrder()
    assertThat(fixture.unpublished).containsExactly("previous")
  }

  @Test
  fun `a paused owner passed over when the leaseholder stopped gets the nav lease when it resumes`() {
    val fixture = Fixture.create()
    val previousOwner = TestOwner(RESUMED)
    val previous = fixture.acquireLease("previous", previousOwner)
    previousOwner.lifecycle.currentState = STARTED
    val owner = TestOwner(RESUMED)
    fixture.acquireLease("owner", owner)
    owner.lifecycle.currentState = CREATED

    previousOwner.lifecycle.currentState = RESUMED

    assertThat(previous.hasLease).isTrue()
    assertThat(fixture.published).containsExactly("previous", "owner", "previous").inOrder()
  }

  @Test
  fun `restarting a stopped leaseholder keeps the nav lease`() {
    val fixture = Fixture.create()
    val owner = TestOwner(RESUMED)
    val registration = fixture.acquireLease("owner", owner)
    owner.lifecycle.currentState = CREATED

    owner.lifecycle.currentState = STARTED

    assertThat(registration.hasLease).isTrue()
    assertThat(fixture.published).containsExactly("owner")
  }

  @Test
  fun `stopping an owner excludes it from later nav lease fallback`() {
    val fixture = Fixture.create()
    val previousOwner = TestOwner(RESUMED)
    val previous = fixture.acquireLease("previous", previousOwner)
    val registration = fixture.acquireLease("owner", TestOwner(RESUMED))
    previousOwner.lifecycle.currentState = CREATED

    registration.dispose()

    assertThat(previous.hasLease).isFalse()
    assertThat(fixture.published).containsExactly("previous", "owner").inOrder()
  }

  // endregion

  // region lifecycle events without a scope

  @Test
  fun `resuming without a scope keeps the owner eligible for nav lease fallback`() {
    val fixture = Fixture.create()
    val owner = TestOwner(CREATED)
    val registration = fixture.acquireLease("owner", owner)
    val incomingOwner = TestOwner(RESUMED)
    fixture.acquireLease("incoming", incomingOwner)
    fixture.withoutScope { owner.lifecycle.currentState = RESUMED }

    incomingOwner.lifecycle.currentState = STARTED

    assertThat(registration.hasLease).isTrue()
  }

  @Test
  fun `resuming without a scope does not transfer the nav lease`() {
    val fixture = Fixture.create()
    val incumbent = fixture.acquireLease("incumbent", TestOwner(RESUMED))
    val owner = TestOwner(STARTED)
    val registration = fixture.register("owner", owner)
    owner.lifecycle.currentState = CREATED
    registration.onBackStackUpdate()

    fixture.withoutScope { owner.lifecycle.currentState = RESUMED }

    assertThat(incumbent.hasLease).isTrue()
    assertThat(fixture.published).containsExactly("incumbent")
  }

  @Test
  fun `pausing the leaseholder without a scope releases the nav lease without publishing a fallback`() {
    val fixture = Fixture.create()
    val previous = fixture.acquireLease("previous", TestOwner(RESUMED))
    val owner = TestOwner(RESUMED)
    val registration = fixture.acquireLease("owner", owner)

    fixture.withoutScope { owner.lifecycle.currentState = STARTED }

    assertThat(registration.hasLease).isFalse()
    assertThat(previous.hasLease).isFalse()
    assertThat(fixture.published).containsExactly("previous", "owner").inOrder()
  }

  /**
   * A real lifecycle pauses before it stops, so the leaseholder here is an owner that acquired the
   * lease without ever resuming, and the other owner resumes without a scope, which records the
   * resume without restoring its lease.
   */
  @Test
  fun `stopping the leaseholder without a scope releases the nav lease without publishing a fallback`() {
    val fixture = Fixture.create()
    val previousOwner = TestOwner(RESUMED)
    val previous = fixture.acquireLease("previous", previousOwner)
    previousOwner.lifecycle.currentState = STARTED
    val owner = TestOwner(STARTED)
    val registration = fixture.acquireLease("owner", owner)
    fixture.withoutScope { previousOwner.lifecycle.currentState = RESUMED }

    fixture.withoutScope { owner.lifecycle.currentState = CREATED }

    assertThat(registration.hasLease).isFalse()
    assertThat(previous.hasLease).isFalse()
    assertThat(fixture.published).containsExactly("previous", "owner").inOrder()
  }

  // endregion

  // region unregistering

  @Test
  fun `disposing the leaseholder unpublishes its published nav state and releases the nav lease`() {
    val fixture = Fixture.create()
    val registration = fixture.acquireLease("owner", TestOwner(RESUMED))

    registration.dispose()

    assertThat(fixture.unpublished).containsExactly("owner")
    assertThat(registration.hasLease).isFalse()
  }

  @Test
  fun `destroying an owner's lifecycle unregisters its candidate`() {
    val fixture = Fixture.create()
    val owner = TestOwner(RESUMED)
    val registration = fixture.acquireLease("owner", owner)

    owner.lifecycle.currentState = DESTROYED

    assertThat(registration.hasLease).isFalse()
    assertThat(fixture.unpublished).containsExactly("owner")
    assertThat(fixture.unregistered).containsExactly("owner" to fixture.scope)
  }

  @Test
  fun `disposing a former leaseholder preserves the current owner's published nav state`() {
    val fixture = Fixture.create()
    val previous = fixture.acquireLease("previous", TestOwner(RESUMED))
    val owner = TestOwner(RESUMED)
    val registration = fixture.acquireLease("owner", owner)
    owner.lifecycle.currentState = STARTED
    fixture.unpublished.clear()

    registration.dispose()

    assertThat(previous.hasLease).isTrue()
    assertThat(fixture.unpublished).isEmpty()
  }

  @Test
  fun `disposing an owner whose nav state is still published without the nav lease unpublishes it`() {
    val fixture = Fixture.create()
    val previous = fixture.acquireLease("previous", TestOwner(RESUMED))
    val owner = TestOwner(RESUMED)
    val registration = fixture.acquireLease("owner", owner)
    fixture.withoutScope { owner.lifecycle.currentState = STARTED }
    fixture.published.clear()
    fixture.unpublished.clear()

    registration.dispose()

    assertThat(fixture.unpublished).containsExactly("owner")
    assertThat(fixture.published).isEmpty()
    assertThat(previous.hasLease).isFalse()
  }

  @Test
  fun `disposing the leaseholder hands the nav lease to the most recently resumed eligible owner`() {
    val fixture = Fixture.create()
    val olderOwner = TestOwner(RESUMED)
    fixture.acquireLease("older", olderOwner)
    olderOwner.lifecycle.currentState = STARTED
    val previousOwner = TestOwner(RESUMED)
    val previous = fixture.acquireLease("previous", previousOwner)
    previousOwner.lifecycle.currentState = STARTED
    val registration = fixture.acquireLease("owner", TestOwner(RESUMED))

    registration.dispose()

    assertThat(previous.hasLease).isTrue()
    assertThat(fixture.published.last()).isEqualTo("previous")
    assertThat(fixture.unpublished).containsExactly("older", "previous", "owner").inOrder()
  }

  @Test
  fun `disposing the leaseholder without a scope releases the nav lease`() {
    val fixture = Fixture.create()
    val previous = fixture.acquireLease("previous", TestOwner(RESUMED))
    val registration = fixture.acquireLease("owner", TestOwner(RESUMED))
    fixture.unpublished.clear()

    fixture.withoutScope { registration.dispose() }

    assertThat(registration.hasLease).isFalse()
    assertThat(previous.hasLease).isFalse()
    assertThat(fixture.published).containsExactly("previous", "owner").inOrder()
    assertThat(fixture.unpublished).isEmpty()
  }

  @Test
  fun `disposing the leaseholder forgets the owner when unpublishing its nav state throws`() {
    val fixture = Fixture.create()
    val failure = IllegalStateException("unpublish failed")
    val owner = TestOwner(RESUMED)
    val registration =
      fixture.acquireLease(
        "owner",
        owner,
        navState = fixture.navState("owner", onUnpublishFrom = { throw failure }),
      )

    val thrown = assertThrows(IllegalStateException::class.java) { registration.dispose() }

    assertThat(thrown).isSameInstanceAs(failure)
    assertThat(registration.hasLease).isFalse()

    fixture.published.clear()
    owner.lifecycle.currentState = STARTED
    owner.lifecycle.currentState = RESUMED
    fixture.acquireLease("incoming", TestOwner(RESUMED))

    assertThat(fixture.published).containsExactly("incoming")
    assertThat(fixture.unpublished).isEmpty()
  }

  @Test
  fun `disposing an owner before its first back stack update keeps it from acquiring the nav lease`() {
    val fixture = Fixture.create()
    val registration = fixture.register("owner", TestOwner(RESUMED))
    registration.dispose()

    registration.onBackStackUpdate()

    assertThat(registration.hasLease).isFalse()
    assertThat(fixture.published).isEmpty()
  }

  @Test
  fun `dispose() - notifies the nav state that it was unregistered after unpublishing it`() {
    val fixture = Fixture.create()
    val calls = mutableListOf<String>()
    val registration =
      fixture.acquireLease(
        "owner",
        TestOwner(RESUMED),
        navState =
          fixture.navState(
            "owner",
            onUnpublishFrom = { calls += "unpublish" },
            onUnregistered = { calls += "unregistered" },
          ),
      )

    registration.dispose()

    assertThat(calls).containsExactly("unpublish", "unregistered").inOrder()
  }

  @Test
  fun `dispose() - passes the scope to the nav state's onUnregistered`() {
    val fixture = Fixture.create()
    val registration = fixture.register("owner", TestOwner(CREATED))

    registration.dispose()

    assertThat(fixture.unregistered).containsExactly("owner" to fixture.scope)
  }

  @Test
  fun `dispose() - passes no scope to the nav state's onUnregistered when configureScope skips it`() {
    val fixture = Fixture.create()
    val registration = fixture.register("owner", TestOwner(CREATED))

    fixture.withoutScope { registration.dispose() }

    assertThat(fixture.unregistered).containsExactly("owner" to null)
  }

  @Test
  fun `dispose() - notifies the nav state once with the scope when unpublishing it throws`() {
    val fixture = Fixture.create()
    val failure = IllegalStateException("unpublish failed")
    val registration =
      fixture.acquireLease(
        "owner",
        TestOwner(RESUMED),
        navState = fixture.navState("owner", onUnpublishFrom = { throw failure }),
      )

    val thrown = assertThrows(IllegalStateException::class.java) { registration.dispose() }

    assertThat(thrown).isSameInstanceAs(failure)
    assertThat(fixture.unregistered).containsExactly("owner" to fixture.scope)
  }

  @Test
  fun `dispose() - notifies the nav state only once when called again`() {
    val fixture = Fixture.create()
    val registration = fixture.register("owner", TestOwner(CREATED))
    registration.dispose()

    registration.dispose()

    assertThat(fixture.unregistered).hasSize(1)
  }

  @Test
  fun `dispose() - notifies the nav state only once when its lifecycle is destroyed afterward`() {
    val fixture = Fixture.create()
    val owner = TestOwner(RESUMED)
    val registration = fixture.register("owner", owner)
    registration.dispose()

    owner.lifecycle.currentState = DESTROYED

    assertThat(fixture.unregistered).hasSize(1)
  }

  @Test
  fun `dispose() - stops observing the lifecycle`() {
    val fixture = Fixture.create()
    val owner = TestOwner(RESUMED)
    val registration = fixture.register("owner", owner)

    registration.dispose()

    assertThat(owner.lifecycle.observerCount).isEqualTo(0)
  }

  // endregion

  /**
   * A [LifecycleOwner] whose lifecycle starts in [initialState] and can be moved through states by
   * setting [LifecycleRegistry.currentState], which dispatches the events in between.
   */
  private class TestOwner(initialState: Lifecycle.State) : LifecycleOwner {
    override val lifecycle: LifecycleRegistry =
      LifecycleRegistry.createUnsafe(this).apply {
        // A lifecycle must be created before it can be destroyed.
        currentState = CREATED
        currentState = initialState
      }
  }

  private class Fixture private constructor() {
    val navLeaseCoordinator = NavLeaseCoordinator()
    val scope = Scope(SentryOptions())

    /** Whether [scopes] runs `configureScope` callbacks; when false, it skips them. */
    private var providesScope = true

    val scopes =
      mock<IScopes>().also { scopes ->
        doAnswer {
            if (providesScope) {
              (it.arguments[0] as ScopeCallback).run(scope)
            }
            null
          }
          .whenever(scopes)
          .configureScope(any())
      }

    /** Names of the nav states published, in publication order. */
    val published = mutableListOf<String>()

    /** Names of the nav states unpublished, in the order they were unpublished. */
    val unpublished = mutableListOf<String>()

    /** Names of the nav states notified of unregistration, with the scope they were given. */
    val unregistered = mutableListOf<Pair<String, IScope?>>()

    /**
     * `LifecycleRegistry` holds its owner weakly and throws once it's collected, so the fixture
     * keeps registered owners alive for the test.
     */
    private val owners = mutableListOf<LifecycleOwner>()

    /**
     * Nav state named [name] that, by default, records its publication in [published], its
     * unpublishing in [unpublished], and its unregistration in [unregistered].
     */
    fun navState(
      name: String,
      onPublishTo: (IScope) -> Unit = { published += name },
      onUnpublishFrom: (IScope) -> Unit = { unpublished += name },
      onUnregistered: (IScope?) -> Unit = { unregistered += name to it },
    ): LeasedNavState = FakeLeasedNavState(onPublishTo, onUnpublishFrom, onUnregistered)

    /** Registers a candidate for [owner]'s lifecycle with the nav state named [name]. */
    fun register(
      name: String,
      owner: TestOwner,
      navState: LeasedNavState = navState(name),
    ): NavLeaseRegistration {
      owners += owner
      return checkNotNull(navLeaseCoordinator.register(owner.lifecycle, scopes, navState))
    }

    /**
     * Registers a candidate for [owner]'s lifecycle and applies its first back stack update, which
     * acquires the nav lease if the owner's lifecycle permits it.
     */
    fun acquireLease(
      name: String,
      owner: TestOwner,
      navState: LeasedNavState = navState(name),
    ): NavLeaseRegistration = register(name, owner, navState).also { it.onBackStackUpdate() }

    /** Runs [block] while `configureScope` skips its callbacks, e.g., as if the SDK is disabled. */
    fun withoutScope(block: () -> Unit) {
      providesScope = false
      try {
        block()
      } finally {
        providesScope = true
      }
    }

    companion object {
      fun create(): Fixture = Fixture()
    }
  }

  private class FakeLeasedNavState(
    private val onPublishTo: (IScope) -> Unit,
    private val onUnpublishFrom: (IScope) -> Unit,
    private val onUnregistered: (IScope?) -> Unit,
  ) : LeasedNavState {
    override fun publishTo(scope: IScope) = onPublishTo(scope)

    override fun unpublishFrom(scope: IScope) = onUnpublishFrom(scope)

    override fun onUnregistered(scope: IScope?) = onUnregistered.invoke(scope)
  }
}
