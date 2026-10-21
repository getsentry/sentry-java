package io.sentry.compose.navigation3.lifecycle

import androidx.lifecycle.Lifecycle
import com.google.common.truth.Truth.assertThat
import io.sentry.IScope
import io.sentry.ITransaction
import org.junit.Test
import org.mockito.kotlin.mock

class NavCandidateRegistryTest {

  private val owner = Any()
  private val other = Any()
  private val registry = NavCandidateRegistry()

  @Test
  fun `register() - stores the owner and its publication callbacks`() {
    val published = mutableListOf<String>()
    val cleared = mutableListOf<String>()
    val scope = mock<IScope>()

    registry.register(
      owner,
      Lifecycle.State.CREATED,
      { published += "owner" },
      { cleared += "owner" },
    )
    val candidate = registry.find(owner)
    candidate?.onPublishData?.invoke(scope)
    candidate?.onClearPublishedData?.invoke(scope)

    assertThat(candidate?.owner).isSameInstanceAs(owner)
    assertThat(published).containsExactly("owner")
    assertThat(cleared).containsExactly("owner")
  }

  @Test
  fun `register() - keeps the first registration for the same owner`() {
    val published = mutableListOf<String>()
    registry.register(owner, Lifecycle.State.CREATED, { published += "first" }, {})
    val first = registry.find(owner)

    registry.register(owner, Lifecycle.State.RESUMED, { published += "second" }, {})
    registry.find(owner)?.onPublishData?.invoke(mock())

    assertThat(registry.find(owner)).isSameInstanceAs(first)
    assertThat(registry.find(owner)?.state).isEqualTo(Lifecycle.State.CREATED)
    assertThat(published).containsExactly("first")
  }

  @Test
  fun `register() - ignores a destroyed owner`() {
    registry.register(owner, Lifecycle.State.DESTROYED, {}, {})

    assertThat(registry.find(owner)).isNull()
  }

  @Test
  fun `find() - distinguishes equal but distinct owners`() {
    val registeredOwner = String(charArrayOf('a'))
    val equalOwner = String(charArrayOf('a'))
    registry.register(registeredOwner, Lifecycle.State.CREATED, {}, {})

    assertThat(registry.find(equalOwner)).isNull()
    assertThat(registry.find(registeredOwner)).isNotNull()
  }

  @Test
  fun `remove() - forgets the candidate and permits registering its owner again`() {
    val candidate = register(owner)

    registry.remove(candidate)
    registry.register(owner, Lifecycle.State.RESUMED, {}, {})

    assertThat(registry.find(owner)).isNotSameInstanceAs(candidate)
    assertThat(registry.find(owner)?.state).isEqualTo(Lifecycle.State.RESUMED)
  }

  @Test
  fun `resume() - records the transition and returns the registered candidate`() {
    val candidate = register(owner, Lifecycle.State.CREATED)

    val resumed = registry.resume(owner)

    assertThat(resumed).isSameInstanceAs(candidate)
    assertThat(candidate.state).isEqualTo(Lifecycle.State.RESUMED)
    assertThat(candidate.resumeOrder).isGreaterThan(0)
  }

  @Test
  fun `pause() and stop() - update registered candidates and ignore unknown owners`() {
    val candidate = register(owner, Lifecycle.State.RESUMED)

    assertThat(registry.pause(owner)).isSameInstanceAs(candidate)
    assertThat(candidate.state).isEqualTo(Lifecycle.State.STARTED)
    assertThat(registry.stop(owner)).isSameInstanceAs(candidate)
    assertThat(candidate.state).isEqualTo(Lifecycle.State.CREATED)
    assertThat(registry.pause(other)).isNull()
    assertThat(registry.stop(other)).isNull()
    assertThat(registry.resume(other)).isNull()
  }

  @Test
  fun `firstBackStackUpdate() - reports a registered owner's first update only`() {
    val candidate = register(owner)

    assertThat(registry.firstBackStackUpdate(owner)).isSameInstanceAs(candidate)
    assertThat(candidate.hasAppliedInitialBackStackUpdate).isTrue()
    assertThat(registry.firstBackStackUpdate(owner)).isNull()
    assertThat(registry.firstBackStackUpdate(other)).isNull()
  }

  @Test
  fun `mostRecentlyResumedCandidate() - selects the last resumed eligible owner`() {
    val first = register(owner, Lifecycle.State.RESUMED)
    val second = register(other, Lifecycle.State.RESUMED)
    registry.firstBackStackUpdate(owner)
    registry.firstBackStackUpdate(other)

    assertThat(registry.mostRecentlyResumedCandidate()).isSameInstanceAs(second)
    registry.resume(owner)
    assertThat(registry.mostRecentlyResumedCandidate()).isSameInstanceAs(first)
  }

  @Test
  fun `mostRecentlyResumedCandidate() - includes started owners but resumedOnly excludes them`() {
    val resumed = register(owner, Lifecycle.State.RESUMED)
    val started = register(other, Lifecycle.State.RESUMED)
    registry.firstBackStackUpdate(owner)
    registry.firstBackStackUpdate(other)
    registry.pause(other)

    assertThat(registry.mostRecentlyResumedCandidate()).isSameInstanceAs(started)
    assertThat(registry.mostRecentlyResumedCandidate(resumedOnly = true)).isSameInstanceAs(resumed)
  }

  @Test
  fun `mostRecentlyResumedCandidate() - skips owners without a back stack update or prior resume`() {
    val eligible = register(owner, Lifecycle.State.RESUMED)
    registry.firstBackStackUpdate(owner)
    register(other, Lifecycle.State.RESUMED)
    val neverResumed = register(Any(), Lifecycle.State.STARTED)
    registry.firstBackStackUpdate(neverResumed.owner)

    assertThat(registry.mostRecentlyResumedCandidate()).isSameInstanceAs(eligible)
  }

  @Test
  fun `mostRecentlyResumedCandidate() - excludes stopped and explicitly excluded owners`() {
    val first = register(owner, Lifecycle.State.RESUMED)
    val second = register(other, Lifecycle.State.RESUMED)
    registry.firstBackStackUpdate(owner)
    registry.firstBackStackUpdate(other)

    assertThat(registry.mostRecentlyResumedCandidate(excluding = second)).isSameInstanceAs(first)
    registry.stop(other)
    assertThat(registry.mostRecentlyResumedCandidate()).isSameInstanceAs(first)
    registry.stop(owner)
    assertThat(registry.mostRecentlyResumedCandidate()).isNull()
  }

  @Test
  fun `retainsTransaction() - reports transactions retained by registered candidates only`() {
    val candidate = register(owner)
    val transaction = mock<ITransaction>()
    candidate.transaction = transaction

    assertThat(registry.retainsTransaction(transaction)).isTrue()
    registry.remove(candidate)
    assertThat(registry.retainsTransaction(transaction)).isFalse()
  }

  @Test
  fun `anotherCandidateRetains() - excludes the given candidate from transaction lookup`() {
    val first = register(owner)
    val second = register(other)
    val transaction = mock<ITransaction>()
    first.transaction = transaction

    assertThat(registry.anotherCandidateRetains(transaction, first)).isFalse()
    assertThat(registry.anotherCandidateRetains(transaction, second)).isTrue()
  }

  private fun register(owner: Any, state: Lifecycle.State = Lifecycle.State.CREATED): NavCandidate {
    registry.register(owner, state, {}, {})
    return checkNotNull(registry.find(owner))
  }
}
