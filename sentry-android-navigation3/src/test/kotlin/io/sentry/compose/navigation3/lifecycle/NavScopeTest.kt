package io.sentry.compose.navigation3.lifecycle

import androidx.lifecycle.Lifecycle
import com.google.common.truth.Truth.assertThat
import io.sentry.ITransaction
import io.sentry.Scope
import io.sentry.SentryOptions
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NavScopeTest {

  @Test
  fun `getScreen() - reads the real screen with the nav lease`() {
    val fixture = Fixture.create(ownsLease = true)
    val navScope = fixture.navScope
    navScope.screen = "Staged"
    fixture.realScope.screen = "Host"

    val screen = navScope.screen

    assertThat(screen).isEqualTo("Host")
  }

  @Test
  fun `getScreen() - reads the staged screen without the nav lease`() {
    val fixture = Fixture.create()
    val navScope = fixture.navScope
    fixture.realScope.screen = "Host"
    fixture.stagedNavData.stageScreen("Staged")

    val screen = navScope.screen

    assertThat(screen).isEqualTo("Staged")
  }

  @Test
  fun `setScreen() - publishes the screen with the nav lease`() {
    val fixture = Fixture.create(ownsLease = true)
    val navScope = fixture.navScope

    navScope.screen = "Profile"

    assertThat(fixture.realScope.screen).isEqualTo("Profile")
  }

  @Test
  fun `setScreen() - stages the screen without the nav lease`() {
    val fixture = Fixture.create()
    val navScope = fixture.navScope
    fixture.realScope.screen = "Host"

    navScope.screen = "Profile"

    assertThat(fixture.realScope.screen).isEqualTo("Host")
    assertThat(navScope.screen).isEqualTo("Profile")
  }

  @Test
  fun `setNavigationContext() - publishes the navigation context with the nav lease`() {
    val fixture = Fixture.create(ownsLease = true)
    val navScope = fixture.navScope
    val navigationContext = mapOf("route" to "Profile")

    navScope.setNavigationContext(navigationContext)

    assertThat(fixture.realScope.contexts[NAVIGATION_CONTEXT_KEY])
      .isSameInstanceAs(navigationContext)
  }

  @Test
  fun `setNavigationContext() - stages the navigation context without the nav lease`() {
    val fixture = Fixture.create()
    val navScope = fixture.navScope
    val hostContext = mapOf("route" to "Host")
    val navigationContext = mapOf("route" to "Profile")
    fixture.realScope.setContexts(NAVIGATION_CONTEXT_KEY, hostContext)

    navScope.setNavigationContext(navigationContext)

    assertThat(fixture.realScope.contexts[NAVIGATION_CONTEXT_KEY]).isSameInstanceAs(hostContext)
    fixture.stagedNavData.publishTo(fixture.realScope)
    assertThat(fixture.realScope.contexts[NAVIGATION_CONTEXT_KEY])
      .isSameInstanceAs(navigationContext)
  }

  @Test
  fun `rotatePropagationContext() - replaces the real context with the nav lease`() {
    val fixture = Fixture.create(ownsLease = true)
    val realContext = fixture.realScope.propagationContext

    fixture.navScope.rotatePropagationContext()

    assertThat(fixture.realScope.propagationContext).isNotSameInstanceAs(realContext)
  }

  @Test
  fun `rotatePropagationContext() - defers rotating the real context without the nav lease`() {
    val fixture = Fixture.create()
    val realContext = fixture.realScope.propagationContext

    fixture.navScope.rotatePropagationContext()

    assertThat(fixture.realScope.propagationContext).isSameInstanceAs(realContext)
    fixture.stagedNavData.publishTo(fixture.realScope)
    assertThat(fixture.realScope.propagationContext).isNotSameInstanceAs(realContext)
  }

  @Test
  fun `bindTransaction() - binds to a free scope with the nav lease`() {
    val fixture = Fixture.create(ownsLease = true)
    val transaction = mock<ITransaction>()

    fixture.navScope.bindTransaction(transaction)

    assertThat(fixture.realScope.transaction).isSameInstanceAs(transaction)
  }

  @Test
  fun `bindTransaction() - preserves an already bound transaction`() {
    val fixture = Fixture.create(ownsLease = true)
    val bound = mock<ITransaction>()
    fixture.realScope.transaction = bound

    fixture.navScope.bindTransaction(mock<ITransaction>())

    assertThat(fixture.realScope.transaction).isSameInstanceAs(bound)
  }

  @Test
  fun `bindTransaction() - does nothing without the nav lease`() {
    val fixture = Fixture.create()

    fixture.navScope.bindTransaction(mock<ITransaction>())

    assertThat(fixture.realScope.transaction).isNull()
  }

  @Test
  fun `unbindTransaction() - unbinds the given transaction without the nav lease`() {
    val fixture = Fixture.create()
    val transaction = mock<ITransaction>()
    fixture.realScope.transaction = transaction

    fixture.navScope.unbindTransaction(transaction)

    assertThat(fixture.realScope.transaction).isNull()
  }

  @Test
  fun `unbindTransaction() - releases the owner's retained nav transaction with the nav lease`() {
    val fixture = Fixture.create(ownsLease = true)
    val transaction = mock<ITransaction>()
    fixture.retainTransaction(transaction)

    fixture.navScope.unbindTransaction(transaction)

    assertThat(fixture.coordinator.isNavTransaction(transaction)).isFalse()
  }

  @Test
  fun `unbindTransaction() - releases the owner's retained nav transaction without the nav lease`() {
    val fixture = Fixture.create(ownsLease = true)
    val transaction = mock<ITransaction>()
    fixture.retainTransaction(transaction)
    fixture.giveLeaseToAnotherOwner()

    fixture.navScope.unbindTransaction(transaction)

    assertThat(fixture.coordinator.isNavTransaction(transaction)).isFalse()
  }

  @Test
  fun `unbindTransaction() - preserves a different bound transaction`() {
    val fixture = Fixture.create(ownsLease = true)
    val bound = mock<ITransaction>()
    fixture.realScope.transaction = bound

    fixture.navScope.unbindTransaction(mock<ITransaction>())

    assertThat(fixture.realScope.transaction).isSameInstanceAs(bound)
  }

  @Test
  fun `clearFinishedTransaction() - unbinds a finished transaction with the nav lease`() {
    val fixture = Fixture.create(ownsLease = true)
    fixture.realScope.transaction =
      mock<ITransaction>().also { whenever(it.isFinished).thenReturn(true) }

    fixture.navScope.clearFinishedTransaction()

    assertThat(fixture.realScope.transaction).isNull()
  }

  @Test
  fun `clearFinishedTransaction() - does nothing without the nav lease`() {
    val fixture = Fixture.create()
    val finished = mock<ITransaction>().also { whenever(it.isFinished).thenReturn(true) }
    fixture.realScope.transaction = finished

    fixture.navScope.clearFinishedTransaction()

    assertThat(fixture.realScope.transaction).isSameInstanceAs(finished)
  }

  @Test
  fun `transaction - hides another owner's nav transaction without the nav lease`() {
    val fixture = Fixture.create()
    val other = Any()
    val otherTransaction = mock<ITransaction>()
    fixture.coordinator.registerCandidate(other, Lifecycle.State.RESUMED, {}, {})
    fixture.coordinator.onBackStackUpdate(other, fixture.realScope)
    fixture.coordinator.retainTransaction(other, otherTransaction, fixture.realScope)
    fixture.realScope.transaction = otherTransaction

    val transaction = fixture.navScope.transaction

    assertThat(transaction).isNull()
  }

  @Test
  fun `transaction - reports a non-nav transaction without the nav lease`() {
    val fixture = Fixture.create()
    val hostTransaction = mock<ITransaction>()
    fixture.realScope.transaction = hostTransaction

    val transaction = fixture.navScope.transaction

    assertThat(transaction).isSameInstanceAs(hostTransaction)
  }

  @Test
  fun `transaction - reports the bound transaction with the nav lease`() {
    val fixture = Fixture.create(ownsLease = true)
    val boundTransaction = mock<ITransaction>()
    fixture.realScope.transaction = boundTransaction

    val transaction = fixture.navScope.transaction

    assertThat(transaction).isSameInstanceAs(boundTransaction)
  }

  private class Fixture
  private constructor(
    val realScope: Scope,
    val stagedNavData: StagedNavData,
    val coordinator: NavLeaseCoordinator,
    val navScope: NavScope,
    private val owner: Any,
  ) {

    /** Retains [transaction] as the owner's nav transaction; the owner must hold the nav lease. */
    fun retainTransaction(transaction: ITransaction) {
      coordinator.retainTransaction(owner, transaction, realScope)
      check(coordinator.isNavTransaction(transaction))
    }

    /** Lets another resumed owner take the nav lease from the owner. */
    fun giveLeaseToAnotherOwner() {
      val other = Any()
      coordinator.registerCandidate(other, Lifecycle.State.RESUMED, {}, {})
      coordinator.onBackStackUpdate(other, realScope)
      check(!coordinator.hasLease(owner))
    }

    companion object {

      fun create(ownsLease: Boolean = false): Fixture {
        val realScope = Scope(SentryOptions.empty())
        val stagedNavData = StagedNavData()
        val coordinator = NavLeaseCoordinator()
        val owner = Any()
        coordinator.registerCandidate(
          owner,
          Lifecycle.State.RESUMED,
          stagedNavData::publishTo,
          stagedNavData::clearPublishedStateFrom,
        )
        if (ownsLease) {
          coordinator.onBackStackUpdate(owner, realScope)
        }

        return Fixture(
          realScope,
          stagedNavData,
          coordinator,
          NavScope(realScope, stagedNavData, coordinator, owner),
          owner,
        )
      }
    }
  }
}
