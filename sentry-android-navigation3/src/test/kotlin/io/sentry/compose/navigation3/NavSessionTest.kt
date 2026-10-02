package io.sentry.compose.navigation3

import androidx.lifecycle.Lifecycle
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NavSessionTest {

  @Test
  fun `the initial destination can start a navigation transaction before the owner enters a resumed state`() {
    val fixture = NavTestFixture()
    val session = fixture.session()
    val owner = NavTestLifecycle(Lifecycle.State.CREATED)
    session.attach(owner.lifecycle)

    session.onBackStackChanged(listOf("Home"))

    val transaction = requireNotNull(fixture.scope.transaction)
    assertThat(transaction.name).isEqualTo("/Home")
    assertThat(transaction.isFinished).isFalse()
    assertThat(fixture.scope.screen).isNull()
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY]).isNull()
    assertThat(fixture.scope.breadcrumbs).isEmpty()
  }

  @Test
  fun `resuming the owner publishes the latest pending destination without replaying earlier breadcrumbs`() {
    val fixture = NavTestFixture()
    val session = fixture.session()
    val owner = NavTestLifecycle(Lifecycle.State.CREATED)
    session.attach(owner.lifecycle)
    session.onBackStackChanged(listOf("Home"))
    session.onBackStackChanged(listOf("Home", "Profile"))
    assertThat(fixture.scope.breadcrumbs).isEmpty()

    owner.resume()

    assertThat(fixture.scope.screen).isEqualTo("/Profile")
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY])
      .isEqualTo(
        mapOf("Back Stack" to listOf(mapOf("entry" to "/Profile"), mapOf("entry" to "/Home")))
      )
    assertThat(fixture.scope.breadcrumbs.map { it.data["to"] }).containsExactly("/Profile")
    assertThat(fixture.scope.transaction?.name).isEqualTo("/Profile")
  }

  @Test
  fun `changing the visible destination publishes navigation data and replaces the navigation transaction`() {
    val fixture = NavTestFixture()
    val session = fixture.session()
    session.attach(NavTestLifecycle(Lifecycle.State.RESUMED).lifecycle)
    session.onBackStackChanged(listOf("Home"))
    val previousTransaction = requireNotNull(fixture.scope.transaction)

    session.onBackStackChanged(listOf("Home", "Profile"))

    assertThat(previousTransaction.isFinished).isTrue()
    val currentTransaction = requireNotNull(fixture.scope.transaction)
    assertThat(currentTransaction).isNotSameInstanceAs(previousTransaction)
    assertThat(currentTransaction.name).isEqualTo("/Profile")
    assertThat(currentTransaction.isFinished).isFalse()
    assertThat(fixture.scope.screen).isEqualTo("/Profile")
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY])
      .isEqualTo(
        mapOf("Back Stack" to listOf(mapOf("entry" to "/Profile"), mapOf("entry" to "/Home")))
      )
    assertThat(fixture.scope.breadcrumbs.map { it.data["to"] })
      .containsExactly("/Home", "/Profile")
      .inOrder()
    assertThat(fixture.scope.breadcrumbs.last().data["from"]).isEqualTo("/Home")
  }

  @Test
  fun `incoming session replaces previous txn before resume without overwriting published navigation data`() {
    val fixture = NavTestFixture()
    val previousSession = fixture.session()
    previousSession.attach(NavTestLifecycle(Lifecycle.State.RESUMED).lifecycle)
    previousSession.onBackStackChanged(listOf("Home"))
    val previousTransaction = requireNotNull(fixture.scope.transaction)
    val incomingSession = fixture.session()
    incomingSession.attach(NavTestLifecycle(Lifecycle.State.CREATED).lifecycle)
    assertThat(previousTransaction.isFinished).isFalse()

    incomingSession.onBackStackChanged(listOf("Settings"))

    assertThat(previousTransaction.isFinished).isTrue()
    assertThat(fixture.scope.transaction?.name).isEqualTo("/Settings")
    assertThat(fixture.scope.screen).isEqualTo("/Home")
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY])
      .isEqualTo(mapOf("Back Stack" to listOf(mapOf("entry" to "/Home"))))
    assertThat(fixture.scope.breadcrumbs.map { it.data["to"] }).containsExactly("/Home")
  }

  @Test
  fun `previous session destination changes do not overwrite the current sessions navigation data and transaction`() {
    val fixture = NavTestFixture()
    val previousSession = fixture.session()
    previousSession.attach(NavTestLifecycle(Lifecycle.State.RESUMED).lifecycle)
    previousSession.onBackStackChanged(listOf("Home"))
    val currentSession = fixture.session()
    currentSession.attach(NavTestLifecycle(Lifecycle.State.RESUMED).lifecycle)
    currentSession.onBackStackChanged(listOf("Settings"))
    val currentTransaction = requireNotNull(fixture.scope.transaction)

    previousSession.onBackStackChanged(listOf("Profile"))

    assertThat(fixture.scope.screen).isEqualTo("/Settings")
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY])
      .isEqualTo(mapOf("Back Stack" to listOf(mapOf("entry" to "/Settings"))))
    assertThat(fixture.scope.transaction).isSameInstanceAs(currentTransaction)
    assertThat(currentTransaction.isFinished).isFalse()
    assertThat(fixture.scope.breadcrumbs.map { it.data["to"] })
      .containsExactly("/Home", "/Settings")
      .inOrder()
  }

  @Test
  fun `disposing the previous session does not modify the current sessions navigation data and transaction`() {
    val fixture = NavTestFixture()
    val previousSession = fixture.session()
    previousSession.attach(NavTestLifecycle(Lifecycle.State.RESUMED).lifecycle)
    previousSession.onBackStackChanged(listOf("Home"))
    val currentSession = fixture.session()
    currentSession.attach(NavTestLifecycle(Lifecycle.State.RESUMED).lifecycle)
    currentSession.onBackStackChanged(listOf("Settings"))
    val currentTransaction = requireNotNull(fixture.scope.transaction)

    previousSession.dispose()

    assertThat(fixture.scope.screen).isEqualTo("/Settings")
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY])
      .isEqualTo(mapOf("Back Stack" to listOf(mapOf("entry" to "/Settings"))))
    assertThat(fixture.scope.transaction).isSameInstanceAs(currentTransaction)
    assertThat(currentTransaction.isFinished).isFalse()
    assertThat(fixture.scope.breadcrumbs.map { it.data["to"] })
      .containsExactly("/Home", "/Settings")
      .inOrder()
  }

  @Test
  fun `disposing an incoming session before its first update lets the previous session continue navigation`() {
    val fixture = NavTestFixture()
    val previousSession = fixture.session()
    previousSession.attach(NavTestLifecycle(Lifecycle.State.RESUMED).lifecycle)
    previousSession.onBackStackChanged(listOf("Home"))
    val previousTransaction = requireNotNull(fixture.scope.transaction)
    val incomingSession = fixture.session()
    incomingSession.attach(NavTestLifecycle(Lifecycle.State.CREATED).lifecycle)

    incomingSession.dispose()

    assertThat(previousTransaction.isFinished).isFalse()
    assertThat(fixture.scope.transaction).isSameInstanceAs(previousTransaction)

    previousSession.onBackStackChanged(listOf("Profile"))

    assertThat(previousTransaction.isFinished).isTrue()
    assertThat(fixture.scope.transaction?.name).isEqualTo("/Profile")
    assertThat(fixture.scope.screen).isEqualTo("/Profile")
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY])
      .isEqualTo(mapOf("Back Stack" to listOf(mapOf("entry" to "/Profile"))))
    assertThat(fixture.scope.breadcrumbs.map { it.data["to"] })
      .containsExactly("/Home", "/Profile")
      .inOrder()
  }

  @Test
  fun `an incoming session with an empty back stack finishes the previous transaction without starting another`() {
    val fixture = NavTestFixture()
    val previousSession = fixture.session()
    previousSession.attach(NavTestLifecycle(Lifecycle.State.CREATED).lifecycle)
    previousSession.onBackStackChanged(listOf("Home"))
    val previousTransaction = requireNotNull(fixture.scope.transaction)
    val incomingSession = fixture.session()
    incomingSession.attach(NavTestLifecycle(Lifecycle.State.CREATED).lifecycle)

    incomingSession.onBackStackChanged(emptyList())

    assertThat(previousTransaction.isFinished).isTrue()
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `incoming session with transactions disabled finishes the previous txn and publishes navigation data`() {
    val fixture = NavTestFixture()
    val previousSession = fixture.session()
    previousSession.attach(NavTestLifecycle(Lifecycle.State.CREATED).lifecycle)
    previousSession.onBackStackChanged(listOf("Home"))
    val previousTransaction = requireNotNull(fixture.scope.transaction)
    val incomingSession = fixture.session(SentryNavOptions { enableNavigationTransactions = false })
    incomingSession.attach(NavTestLifecycle(Lifecycle.State.RESUMED).lifecycle)

    incomingSession.onBackStackChanged(listOf("Settings"))

    assertThat(previousTransaction.isFinished).isTrue()
    assertThat(fixture.scope.transaction).isNull()
    assertThat(fixture.scope.screen).isEqualTo("/Settings")
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY])
      .isEqualTo(mapOf("Back Stack" to listOf(mapOf("entry" to "/Settings"))))
    assertThat(fixture.scope.breadcrumbs.map { it.data["to"] }).containsExactly("/Settings")
  }

  @Test
  fun `session updates and disposal preserve an active host transaction`() {
    val fixture = NavTestFixture()
    val hostTransaction = fixture.transaction()
    fixture.scope.transaction = hostTransaction
    val session = fixture.session()
    session.attach(NavTestLifecycle(Lifecycle.State.RESUMED).lifecycle)

    session.onBackStackChanged(listOf("Home"))

    assertThat(fixture.scope.screen).isEqualTo("/Home")
    assertThat(fixture.scope.transaction).isSameInstanceAs(hostTransaction)

    session.dispose()

    assertThat(hostTransaction.isFinished).isFalse()
    assertThat(fixture.scope.transaction).isSameInstanceAs(hostTransaction)
    assertThat(fixture.scope.screen).isNull()
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY]).isNull()
  }

  @Test
  fun `disposing current session clears nav state and finishes its txn while retaining breadcrumbs`() {
    val fixture = NavTestFixture()
    val session = fixture.session()
    session.attach(NavTestLifecycle(Lifecycle.State.RESUMED).lifecycle)
    session.onBackStackChanged(listOf("Home"))
    val transaction = requireNotNull(fixture.scope.transaction)

    session.dispose()

    assertThat(transaction.isFinished).isTrue()
    assertThat(fixture.scope.transaction).isNull()
    assertThat(fixture.scope.screen).isNull()
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY]).isNull()
    assertThat(fixture.scope.breadcrumbs.map { it.data["to"] }).containsExactly("/Home")
  }

  @Test
  fun `disposing an already disposed session preserves navigation state published afterwards`() {
    val fixture = NavTestFixture()
    val session = fixture.session()
    session.attach(NavTestLifecycle(Lifecycle.State.RESUMED).lifecycle)
    session.onBackStackChanged(listOf("Home"))
    session.dispose()
    val hostBackStack = mapOf("Back Stack" to listOf(mapOf("entry" to "/Host")))
    fixture.scope.screen = "Host"
    fixture.scope.setContexts(NAVIGATION_CONTEXT_KEY, hostBackStack)
    val hostTransaction = fixture.transaction()
    fixture.scope.transaction = hostTransaction

    session.dispose()

    assertThat(fixture.scope.screen).isEqualTo("Host")
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY]).isEqualTo(hostBackStack)
    assertThat(fixture.scope.transaction).isSameInstanceAs(hostTransaction)
    assertThat(hostTransaction.isFinished).isFalse()
    assertThat(fixture.scope.breadcrumbs.map { it.data["to"] }).containsExactly("/Home")
  }

  @Test
  fun `destroying the owner finishes the navigation transaction while retaining navigation data for crash reporting`() {
    val fixture = NavTestFixture()
    val session = fixture.session()
    val owner = NavTestLifecycle(Lifecycle.State.RESUMED)
    session.attach(owner.lifecycle)
    session.onBackStackChanged(listOf("Home"))
    val transaction = requireNotNull(fixture.scope.transaction)

    owner.destroy()

    assertThat(transaction.isFinished).isTrue()
    assertThat(fixture.scope.transaction).isNull()
    assertThat(fixture.scope.screen).isEqualTo("/Home")
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY])
      .isEqualTo(mapOf("Back Stack" to listOf(mapOf("entry" to "/Home"))))
    assertThat(fixture.scope.breadcrumbs.map { it.data["to"] }).containsExactly("/Home")
  }
}
