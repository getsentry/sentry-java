package io.sentry.compose.navigation3

import androidx.lifecycle.Lifecycle
import com.google.common.truth.Truth.assertThat
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.ITransaction
import io.sentry.PropagationContext
import io.sentry.ScopeType
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import org.junit.Test

/**
 * The tests in this suite use the following terminology:
 *
 * - **Navigation data**: The non-transaction Sentry data emitted by [SentryNavEffect], i.e., screen
 *   names, back stack context, and breadcrumbs.
 *
 * - **Navigation state**: Navigation data that has a single, variable-like value, i.e., screen
 *   names and back stack context.
 *
 * - **Navigation events**: Navigation data that has accumulated, event-like values, i.e.,
 *   breadcrumbs.
 */
@Suppress("LargeClass")
class INavScopesTest {

  @Test
  fun `navigation state is staged before lifecycle enters a resumed state`() {
    val fixture = NavTestFixture()
    val navScopes = fixture.navScopes()
    val owner = NavTestLifecycle(state = Lifecycle.State.CREATED)
    fixture.scope.screen = "visible"

    navScopes.attach(owner.lifecycle)
    navScopes.configureScope {
      it.screen = "pending"
      it.setContexts(NAVIGATION_CONTEXT_KEY, mapOf("route" to "pending"))
    }

    assertThat(fixture.scope.screen).isEqualTo("visible")
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY]).isNull()
    navScopes.configureScope { assertThat(it.screen).isEqualTo("pending") }

    owner.resume()

    assertThat(fixture.scope.screen).isEqualTo("pending")
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY])
      .isEqualTo(mapOf("route" to "pending"))
  }

  @Test
  fun `navigation state is staged after lifecycle leaves a resumed state`() {
    val fixture = NavTestFixture()
    val navScopes = fixture.navScopes()
    val owner = NavTestLifecycle(Lifecycle.State.RESUMED)

    navScopes.attach(owner.lifecycle)
    navScopes.configureScope { it.screen = "Home" }
    assertThat(fixture.scope.screen).isEqualTo("Home")

    owner.pause()

    navScopes.configureScope { it.screen = "Profile" }
    assertThat(fixture.scope.screen).isEqualTo("Home")

    owner.resume()

    assertThat(fixture.scope.screen).isEqualTo("Profile")
  }

  @Test
  fun `navigation state is published immediately when lifecycle begins in a resumed state`() {
    val fixture = NavTestFixture()
    val navScopes = fixture.navScopes()

    navScopes.attach(NavTestLifecycle(Lifecycle.State.RESUMED).lifecycle)
    navScopes.configureScope {
      it.screen = "Home"
      it.setContexts(NAVIGATION_CONTEXT_KEY, mapOf("route" to "Home"))
    }

    assertThat(fixture.scope.screen).isEqualTo("Home")
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY]).isEqualTo(mapOf("route" to "Home"))
  }

  @Test
  fun `incoming owner stages nav state updates until lifecycle resumes so long as previous owner is visible`() {
    val fixture = NavTestFixture()
    val firstNavScopes = fixture.navScopes()
    firstNavScopes.attach(NavTestLifecycle(Lifecycle.State.RESUMED).lifecycle)
    val homeBackStack = mapOf("Back Stack" to listOf(mapOf("entry" to "/Home")))
    firstNavScopes.configureScope {
      it.screen = "Home"
      it.setContexts(NAVIGATION_CONTEXT_KEY, homeBackStack)
    }
    val secondNavScopes = fixture.navScopes()
    val secondOwner = NavTestLifecycle(Lifecycle.State.CREATED)
    secondNavScopes.attach(secondOwner.lifecycle)
    val settingsBackStack = mapOf("Back Stack" to listOf(mapOf("entry" to "/Settings")))

    secondNavScopes.configureScope {
      it.screen = "Settings"
      it.setContexts(NAVIGATION_CONTEXT_KEY, settingsBackStack)
    }

    assertThat(fixture.scope.screen).isEqualTo("Home")
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY]).isEqualTo(homeBackStack)

    secondOwner.resume()

    assertThat(fixture.scope.screen).isEqualTo("Settings")
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY]).isEqualTo(settingsBackStack)
  }

  @Test
  fun `cleaning up the previous owner doesn't modify the current navigation state`() {
    val fixture = NavTestFixture()
    val firstNavScopes = fixture.navScopes()
    val firstOwner = NavTestLifecycle(Lifecycle.State.RESUMED)
    firstNavScopes.attach(firstOwner.lifecycle)
    firstNavScopes.configureScope {
      it.screen = "Home"
      it.setContexts(
        NAVIGATION_CONTEXT_KEY,
        mapOf("Back Stack" to listOf(mapOf("entry" to "/Home"))),
      )
    }
    val secondNavScopes = fixture.navScopes()
    secondNavScopes.attach(NavTestLifecycle(Lifecycle.State.RESUMED).lifecycle)
    val settingsBackStack = mapOf("Back Stack" to listOf(mapOf("entry" to "/Settings")))
    secondNavScopes.configureScope {
      it.screen = "Settings"
      it.setContexts(NAVIGATION_CONTEXT_KEY, settingsBackStack)
    }

    firstNavScopes.configureScope {
      it.screen = "unseen"
      it.setContexts(
        NAVIGATION_CONTEXT_KEY,
        mapOf("Back Stack" to listOf(mapOf("entry" to "/Unseen"))),
      )
    }
    assertThat(fixture.scope.screen).isEqualTo("Settings")
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY]).isEqualTo(settingsBackStack)

    firstOwner.pause()
    assertThat(fixture.scope.screen).isEqualTo("Settings")
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY]).isEqualTo(settingsBackStack)

    firstNavScopes.dispose()
    assertThat(fixture.scope.screen).isEqualTo("Settings")
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY]).isEqualTo(settingsBackStack)

    val profileBackStack = mapOf("Back Stack" to listOf(mapOf("entry" to "/Profile")))
    secondNavScopes.configureScope {
      it.screen = "Profile"
      it.setContexts(NAVIGATION_CONTEXT_KEY, profileBackStack)
    }

    assertThat(fixture.scope.screen).isEqualTo("Profile")
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY]).isEqualTo(profileBackStack)
  }

  @Test
  fun `a new owner clears any navigation state it didn't stage`() {
    val fixture = NavTestFixture()
    fixture.scope.screen = "old"
    fixture.scope.setContexts(NAVIGATION_CONTEXT_KEY, mapOf("route" to "old"))
    fixture.scope.setContexts("host", mapOf("value" to "retained"))

    fixture.navScopes().attach(NavTestLifecycle(Lifecycle.State.RESUMED).lifecycle)

    assertThat(fixture.scope.screen).isNull()
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY]).isNull()
    assertThat(fixture.scope.contexts["host"]).isEqualTo(mapOf("value" to "retained"))
  }

  @Test
  fun `cleared navigation state is published as empty state when lifecycle is resumed`() {
    val fixture = NavTestFixture()
    val navScopes = fixture.navScopes()
    val owner = NavTestLifecycle()

    navScopes.attach(owner.lifecycle)
    navScopes.configureScope {
      it.screen = "Home"
      it.setContexts(NAVIGATION_CONTEXT_KEY, mapOf("route" to "Home"))
    }
    navScopes.configureScope {
      it.screen = null
      it.setContexts(NAVIGATION_CONTEXT_KEY, null as Any?)
    }

    owner.resume()

    assertThat(fixture.scope.screen).isNull()
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY]).isNull()
  }

  @Test
  fun `the latest initial breadcrumb is published when lifecycle is resumed`() {
    val fixture = NavTestFixture()
    val navScopes = fixture.navScopes()
    val owner = NavTestLifecycle()

    navScopes.attach(owner.lifecycle)
    navScopes.addBreadcrumb(Breadcrumb().apply { message = "Home" })
    navScopes.addBreadcrumb(Breadcrumb().apply { message = "Profile" }, Hint())
    assertThat(fixture.scope.breadcrumbs).isEmpty()

    owner.resume()

    assertThat(fixture.scope.breadcrumbs.map { it.message }).containsExactly("Profile")
  }

  @Test
  fun `breadcrumbs received after publication are discarded if lifecycle is not resumed`() {
    val fixture = NavTestFixture()
    val navScopes = fixture.navScopes()
    val owner = NavTestLifecycle(Lifecycle.State.RESUMED)

    navScopes.attach(owner.lifecycle)
    navScopes.addBreadcrumb(Breadcrumb().apply { message = "Home" }, Hint())
    assertThat(fixture.scope.breadcrumbs.map { it.message }).containsExactly("Home").inOrder()

    owner.pause()

    navScopes.addBreadcrumb(Breadcrumb().apply { message = "first_unseen" })
    navScopes.addBreadcrumb(Breadcrumb().apply { message = "second_unseen" })
    assertThat(fixture.scope.breadcrumbs.map { it.message }).containsExactly("Home").inOrder()
  }

  @Test
  fun `breadcrumbs received after publication are published if lifecycle is resumed`() {
    val fixture = NavTestFixture()
    val navScopes = fixture.navScopes()
    val owner = NavTestLifecycle(Lifecycle.State.RESUMED)

    navScopes.attach(owner.lifecycle)
    navScopes.addBreadcrumb(Breadcrumb().apply { message = "Home" }, Hint())
    assertThat(fixture.scope.breadcrumbs.map { it.message }).containsExactly("Home").inOrder()

    owner.pause()

    navScopes.addBreadcrumb(Breadcrumb().apply { message = "unseen" })
    assertThat(fixture.scope.breadcrumbs.map { it.message }).containsExactly("Home").inOrder()

    owner.resume()

    navScopes.addBreadcrumb(Breadcrumb().apply { message = "Profile" })
    assertThat(fixture.scope.breadcrumbs.map { it.message })
      .containsExactly("Home", "Profile")
      .inOrder()
  }

  @Test
  fun `only the current owner of the data lease publishes breadcrumbs during overlap`() {
    val fixture = NavTestFixture()
    val firstNavScopes = fixture.navScopes()
    val firstOwner = NavTestLifecycle(Lifecycle.State.RESUMED)
    firstNavScopes.attach(firstOwner.lifecycle)
    firstNavScopes.addBreadcrumb(Breadcrumb().apply { message = "Home" })
    val secondNavScopes = fixture.navScopes()
    val secondOwner = NavTestLifecycle(Lifecycle.State.RESUMED)
    secondNavScopes.attach(secondOwner.lifecycle)

    firstNavScopes.addBreadcrumb(Breadcrumb().apply { message = "unseen" }, Hint())
    secondNavScopes.addBreadcrumb(Breadcrumb().apply { message = "Settings" }, Hint())

    assertThat(fixture.scope.breadcrumbs.map { it.message })
      .containsExactly("Home", "Settings")
      .inOrder()

    firstOwner.pause()
    secondOwner.pause()
    firstOwner.resume()

    assertThat(fixture.scope.breadcrumbs.map { it.message })
      .containsExactly("Home", "Settings")
      .inOrder()
    firstNavScopes.addBreadcrumb(Breadcrumb().apply { message = "Profile" })
    assertThat(fixture.scope.breadcrumbs.map { it.message })
      .containsExactly("Home", "Settings", "Profile")
      .inOrder()
  }

  @Test
  fun `each incoming data lease owner publishes only its own latest pending initial breadcrumb`() {
    val fixture = NavTestFixture()
    val firstNavScopes = fixture.navScopes()
    val firstOwner = NavTestLifecycle(Lifecycle.State.CREATED)
    firstNavScopes.attach(firstOwner.lifecycle)
    val secondNavScopes = fixture.navScopes()
    val secondOwner = NavTestLifecycle(Lifecycle.State.CREATED)
    secondNavScopes.attach(secondOwner.lifecycle)

    firstNavScopes.addBreadcrumb(Breadcrumb().apply { message = "first initial" })
    secondNavScopes.addBreadcrumb(Breadcrumb().apply { message = "second initial" }, Hint())
    firstNavScopes.addBreadcrumb(Breadcrumb().apply { message = "first latest" }, Hint())
    secondNavScopes.addBreadcrumb(Breadcrumb().apply { message = "second latest" })
    assertThat(fixture.scope.breadcrumbs).isEmpty()

    firstOwner.resume()

    assertThat(fixture.scope.breadcrumbs.map { it.message }).containsExactly("first latest")

    firstOwner.pause()
    secondOwner.resume()

    assertThat(fixture.scope.breadcrumbs.map { it.message })
      .containsExactly("first latest", "second latest")
      .inOrder()
  }

  @Test
  fun `navigation transactions cannot start without the transaction lease`() {
    val fixture = NavTestFixture()
    val navScopes = fixture.navScopes()

    val transaction =
      navScopes.startTransaction(TransactionContext("Home", "navigation"), TransactionOptions())

    assertThat(transaction.isNoOp).isTrue()
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `navigation transactions can start before lifecycle is resumed`() {
    val fixture = NavTestFixture()
    val navScopes = fixture.navScopes()
    navScopes.attach(NavTestLifecycle(Lifecycle.State.CREATED).lifecycle)

    val transaction =
      navScopes.startTransaction(TransactionContext("Home", "navigation"), TransactionOptions())

    assertThat(transaction.isNoOp).isFalse()
    assertThat(transaction.name).isEqualTo("Home")
    assertThat(transaction.operation).isEqualTo("navigation")
    assertThat(transaction.isFinished).isFalse()
  }

  @Test
  fun `navigation transactions can still start while lifecycle is paused`() {
    val fixture = NavTestFixture()
    val navScopes = fixture.navScopes()
    val owner = NavTestLifecycle(Lifecycle.State.RESUMED)
    navScopes.attach(owner.lifecycle)

    owner.pause()
    val transaction =
      navScopes.startTransaction(TransactionContext("Profile", "navigation"), TransactionOptions())

    assertThat(transaction.isNoOp).isFalse()
    assertThat(transaction.name).isEqualTo("Profile")
    assertThat(transaction.isFinished).isFalse()
  }

  @Test
  fun `navigation transactions cannot start after another instance takes the transaction lease`() {
    val fixture = NavTestFixture()
    val firstNavScopes = fixture.navScopes()
    firstNavScopes.attach(NavTestLifecycle().lifecycle)
    val secondNavScopes = fixture.navScopes()
    secondNavScopes.attach(NavTestLifecycle().lifecycle)
    val currentTransaction =
      secondNavScopes.startTransaction(
        TransactionContext("Settings", "navigation"),
        TransactionOptions(),
      )
    secondNavScopes.configureScope { it.transaction = currentTransaction }

    val rejectedTransaction =
      firstNavScopes.startTransaction(
        TransactionContext("Profile", "navigation"),
        TransactionOptions(),
      )

    assertThat(rejectedTransaction.isNoOp).isTrue()
    assertThat(currentTransaction.isNoOp).isFalse()
    assertThat(currentTransaction.isFinished).isFalse()
    assertThat(fixture.scope.transaction).isSameInstanceAs(currentTransaction)
  }

  @Test
  fun `navigation state updates from the previous owner don't modify the current transaction`() {
    val fixture = NavTestFixture()
    val firstNavScopes = fixture.navScopes()
    firstNavScopes.attach(NavTestLifecycle().lifecycle)
    val previousTransaction =
      firstNavScopes.startTransaction(
        TransactionContext("Home", "navigation"),
        TransactionOptions(),
      )
    firstNavScopes.configureScope { it.transaction = previousTransaction }
    val secondNavScopes = fixture.navScopes()
    secondNavScopes.attach(NavTestLifecycle().lifecycle)
    secondNavScopes.runBackStackUpdate { secondNavScopes.configureScope {} }
    val currentTransaction =
      secondNavScopes.startTransaction(
        TransactionContext("Settings", "navigation"),
        TransactionOptions(),
      )
    secondNavScopes.configureScope { it.transaction = currentTransaction }

    firstNavScopes.runBackStackUpdate {
      firstNavScopes.configureScope { it.screen = "unseen" }
    }

    assertThat(currentTransaction.isNoOp).isFalse()
    assertThat(currentTransaction.isFinished).isFalse()
    assertThat(fixture.scope.transaction).isSameInstanceAs(currentTransaction)
  }

  @Test
  fun `navigation transaction handoff waits for a back stack update`() {
    val fixture = NavTestFixture()
    val firstNavScopes = fixture.navScopes()
    firstNavScopes.attach(NavTestLifecycle().lifecycle)
    val transaction =
      firstNavScopes.startTransaction(
        TransactionContext("Home", "navigation"),
        TransactionOptions(),
      )
    firstNavScopes.configureScope { it.transaction = transaction }
    val secondNavScopes = fixture.navScopes()

    secondNavScopes.attach(NavTestLifecycle().lifecycle)
    secondNavScopes.configureScope { it.setTag("host", "value") }

    assertThat(transaction.isNoOp).isFalse()
    assertThat(transaction.isFinished).isFalse()
    assertThat(fixture.scope.transaction).isSameInstanceAs(transaction)
  }

  @Test
  fun `navigation transaction handoff finishes and unbinds the previous transaction before applying the update`() {
    val fixture = NavTestFixture()
    val firstNavScopes = fixture.navScopes()
    firstNavScopes.attach(NavTestLifecycle().lifecycle)
    val previousTransaction =
      firstNavScopes.startTransaction(
        TransactionContext("Home", "navigation"),
        TransactionOptions(),
      )
    previousTransaction.status = SpanStatus.CANCELLED
    firstNavScopes.configureScope { it.transaction = previousTransaction }
    val secondNavScopes = fixture.navScopes()
    secondNavScopes.attach(NavTestLifecycle().lifecycle)
    var previousWasFinishedDuringUpdate = false
    var scopeWasEmptyDuringUpdate = false
    var replacementTransaction: ITransaction? = null

    secondNavScopes.runBackStackUpdate {
      secondNavScopes.configureScope {
        previousWasFinishedDuringUpdate = previousTransaction.isFinished
        scopeWasEmptyDuringUpdate = it.transaction == null
        replacementTransaction =
          secondNavScopes.startTransaction(
            TransactionContext("Settings", "navigation"),
            TransactionOptions(),
          )
        it.transaction = replacementTransaction
      }
    }

    assertThat(previousWasFinishedDuringUpdate).isTrue()
    assertThat(scopeWasEmptyDuringUpdate).isTrue()
    assertThat(previousTransaction.status).isEqualTo(SpanStatus.CANCELLED)
    val replacement = requireNotNull(replacementTransaction)
    assertThat(replacement.isNoOp).isFalse()
    assertThat(replacement.isFinished).isFalse()
    assertThat(fixture.scope.transaction).isSameInstanceAs(replacement)
  }

  @Test
  fun `navigation transaction disposal finishes and unbinds the owned transaction`() {
    val fixture = NavTestFixture()
    val navScopes = fixture.navScopes()
    navScopes.attach(NavTestLifecycle().lifecycle)
    val transaction =
      navScopes.startTransaction(TransactionContext("Home", "navigation"), TransactionOptions())
    navScopes.configureScope { it.transaction = transaction }

    navScopes.dispose()

    assertThat(transaction.isNoOp).isFalse()
    assertThat(transaction.isFinished).isTrue()
    assertThat(transaction.status).isEqualTo(SpanStatus.OK)
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `navigation transaction disposal preserves a host transaction that replaced it`() {
    val fixture = NavTestFixture()
    val navScopes = fixture.navScopes()
    navScopes.attach(NavTestLifecycle().lifecycle)
    val navigationTransaction =
      navScopes.startTransaction(TransactionContext("Home", "navigation"), TransactionOptions())
    navScopes.configureScope { it.transaction = navigationTransaction }
    val hostTransaction = fixture.transaction("Host")
    fixture.scope.transaction = hostTransaction

    navScopes.dispose()

    assertThat(navigationTransaction.isFinished).isTrue()
    assertThat(hostTransaction.isNoOp).isFalse()
    assertThat(hostTransaction.isFinished).isFalse()
    assertThat(fixture.scope.transaction).isSameInstanceAs(hostTransaction)
  }

  @Test
  fun `navigation transaction disposal by the former owner preserves the current owners transaction`() {
    val fixture = NavTestFixture()
    val firstNavScopes = fixture.navScopes()
    firstNavScopes.attach(NavTestLifecycle().lifecycle)
    val previousTransaction =
      firstNavScopes.startTransaction(
        TransactionContext("Home", "navigation"),
        TransactionOptions(),
      )
    firstNavScopes.configureScope { it.transaction = previousTransaction }
    val secondNavScopes = fixture.navScopes()
    secondNavScopes.attach(NavTestLifecycle().lifecycle)
    secondNavScopes.runBackStackUpdate { secondNavScopes.configureScope {} }
    val currentTransaction =
      secondNavScopes.startTransaction(
        TransactionContext("Settings", "navigation"),
        TransactionOptions(),
      )
    secondNavScopes.configureScope { it.transaction = currentTransaction }

    firstNavScopes.dispose()

    assertThat(currentTransaction.isNoOp).isFalse()
    assertThat(currentTransaction.isFinished).isFalse()
    assertThat(fixture.scope.transaction).isSameInstanceAs(currentTransaction)
  }

  @Test
  fun `navigation transaction ownership returns to the active owner if the incoming owner disposes before an update`() {
    val fixture = NavTestFixture()
    val firstNavScopes = fixture.navScopes()
    firstNavScopes.attach(NavTestLifecycle().lifecycle)
    val activeTransaction =
      firstNavScopes.startTransaction(
        TransactionContext("Home", "navigation"),
        TransactionOptions(),
      )
    firstNavScopes.configureScope { it.transaction = activeTransaction }
    val secondNavScopes = fixture.navScopes()
    secondNavScopes.attach(NavTestLifecycle().lifecycle)

    secondNavScopes.dispose()

    assertThat(activeTransaction.isFinished).isFalse()
    assertThat(fixture.scope.transaction).isSameInstanceAs(activeTransaction)
    val nextTransaction =
      firstNavScopes.startTransaction(
        TransactionContext("Profile", "navigation"),
        TransactionOptions(),
      )
    assertThat(nextTransaction.isNoOp).isFalse()
    assertThat(nextTransaction.name).isEqualTo("Profile")
  }

  @Test
  fun `the previous owner's transaction remains active when a navigation update fails`() {
    val fixture = NavTestFixture()

    val firstNavScopesInstance = fixture.navScopes()
    firstNavScopesInstance.attach(NavTestLifecycle().lifecycle)
    val tx =
      firstNavScopesInstance.startTransaction(
        TransactionContext("Home", "navigation"),
        TransactionOptions(),
      )
    fixture.scope.transaction = tx

    val secondNavScopesInstance = fixture.navScopes()
    secondNavScopesInstance.attach(NavTestLifecycle().lifecycle)

    val exception =
      try {
        secondNavScopesInstance.runBackStackUpdate { throw IllegalArgumentException("mapper") }
        null
      } catch (e: IllegalArgumentException) {
        e
      }
    assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)

    secondNavScopesInstance.configureScope { it.setTag("host", "value") }

    assertThat(tx.isFinished).isFalse()
    assertThat(fixture.scope.transaction).isSameInstanceAs(tx)
  }

  @Test
  fun `navigation transaction lease survives a no-op returned while tracing is disabled`() {
    val fixture = NavTestFixture()
    val navScopes = fixture.navScopes()
    navScopes.attach(NavTestLifecycle().lifecycle)
    fixture.options.setTracesSampleRate(null)

    val disabledTransaction =
      navScopes.startTransaction(TransactionContext("Home", "navigation"), TransactionOptions())
    assertThat(disabledTransaction.isNoOp).isTrue()

    fixture.options.setTracesSampleRate(1.0)
    val enabledTransaction =
      navScopes.startTransaction(TransactionContext("Profile", "navigation"), TransactionOptions())
    assertThat(enabledTransaction.isNoOp).isFalse()
    assertThat(enabledTransaction.name).isEqualTo("Profile")
  }

  @Test
  fun `propagation changes are not published without the transaction lease`() {
    val fixture = NavTestFixture()
    val navScopes = fixture.navScopes()
    navScopes.attach(NavTestLifecycle().lifecycle)
    fixture.navScopes().attach(NavTestLifecycle().lifecycle)
    val shared = fixture.scope.propagationContext.traceId
    val privateContext = PropagationContext()

    navScopes.configureScope { scope ->
      scope.propagationContext = privateContext
      assertThat(scope.propagationContext.traceId).isEqualTo(privateContext.traceId)
      scope.withPropagationContext { assertThat(it.traceId).isEqualTo(privateContext.traceId) }
    }

    assertThat(fixture.scope.propagationContext.traceId).isEqualTo(shared)
  }

  @Test
  fun `propagation changes are published if transaction lease is held`() {
    val fixture = NavTestFixture()
    val navScopes = fixture.navScopes()
    navScopes.attach(NavTestLifecycle().lifecycle)
    val context = PropagationContext()

    navScopes.configureScope { scope ->
      scope.withPropagationContext { scope.propagationContext = context }
    }

    assertThat(fixture.scope.propagationContext.traceId).isEqualTo(context.traceId)
  }

  @Test
  fun `transaction lease is not required for unrelated scope writes`() {
    val fixture = NavTestFixture()
    val navScopes = fixture.navScopes()
    navScopes.attach(NavTestLifecycle(Lifecycle.State.RESUMED).lifecycle)
    fixture.navScopes().attach(NavTestLifecycle(Lifecycle.State.CREATED).lifecycle)

    navScopes.configureScope { it.screen = "Home" }
    assertThat(fixture.scope.screen).isEqualTo("Home")
    val transaction =
      navScopes.startTransaction(TransactionContext("Home", "navigation"), TransactionOptions())
    assertThat(transaction.isNoOp).isTrue()

    navScopes.configureScope(ScopeType.ISOLATION) {
      it.setTag("host", "value")
      it.setContexts("host", mapOf("id" to 42))
    }

    assertThat(fixture.scopes.isolationScope).isNotSameInstanceAs(fixture.scope)
    assertThat(fixture.scopes.isolationScope.tags["host"]).isEqualTo("value")
    assertThat(fixture.scopes.isolationScope.contexts["host"]).isEqualTo(mapOf("id" to 42))
  }

  @Test
  fun `data lease is not required for unrelated scope writes`() {
    val fixture = NavTestFixture()
    val navScopes = fixture.navScopes()

    navScopes.configureScope(ScopeType.ISOLATION) {
      it.setTag("host", "value")
      it.setContexts("host", mapOf("id" to 42))
    }

    assertThat(fixture.scopes.isolationScope).isNotSameInstanceAs(fixture.scope)
    assertThat(fixture.scopes.isolationScope.tags["host"]).isEqualTo("value")
    assertThat(fixture.scopes.isolationScope.contexts["host"]).isEqualTo(mapOf("id" to 42))
  }

  @Test
  fun `current owner keeps data lease when previous owner lifecycle is paused and navScopes instance is disposed`() {
    val fixture = NavTestFixture()
    val firstNavScopesInstance = fixture.navScopes()
    val owner = NavTestLifecycle(Lifecycle.State.RESUMED)
    firstNavScopesInstance.attach(owner.lifecycle)

    val secondNavScopesInstance = fixture.navScopes()
    secondNavScopesInstance.attach(NavTestLifecycle(Lifecycle.State.RESUMED).lifecycle)

    owner.pause()
    firstNavScopesInstance.dispose()

    secondNavScopesInstance.configureScope { it.screen = "second" }
    firstNavScopesInstance.configureScope { it.screen = "stale" }
    assertThat(fixture.scope.screen).isEqualTo("second")
  }

  @Test
  fun `a disposed navScopes instance ignores later lifecycle events and does not re-acquire leases`() {
    val fixture = NavTestFixture()
    val navScopes = fixture.navScopes()
    val owner = NavTestLifecycle(Lifecycle.State.RESUMED)

    navScopes.attach(owner.lifecycle)
    navScopes.configureScope { it.screen = "Home" }
    assertThat(fixture.scope.screen).isEqualTo("Home")

    owner.pause()
    navScopes.dispose()
    fixture.scope.screen = "host"

    owner.resume()

    assertThat(fixture.scope.screen).isEqualTo("host")
    assertThat(
        navScopes
          .startTransaction(TransactionContext("Home", "navigation"), TransactionOptions())
          .isNoOp
      )
      .isTrue()
  }

  @Test
  fun `a destroyed lifecycle cannot publish navigation data`() {
    val fixture = NavTestFixture()
    val navScopes = fixture.navScopes()
    val owner = NavTestLifecycle(Lifecycle.State.DESTROYED)
    val publishedBackStack = mapOf("Back Stack" to listOf(mapOf("entry" to "/Host")))
    fixture.scope.screen = "Host"
    fixture.scope.setContexts(NAVIGATION_CONTEXT_KEY, publishedBackStack)
    fixture.scopes.addBreadcrumb(Breadcrumb().apply { message = "Host" })

    navScopes.configureScope {
      it.screen = "Home"
      it.setContexts(
        NAVIGATION_CONTEXT_KEY,
        mapOf("Back Stack" to listOf(mapOf("entry" to "/Home"))),
      )
    }
    navScopes.addBreadcrumb(Breadcrumb().apply { message = "Home" })

    navScopes.attach(owner.lifecycle)

    navScopes.configureScope {
      it.screen = "Profile"
      it.setContexts(
        NAVIGATION_CONTEXT_KEY,
        mapOf("Back Stack" to listOf(mapOf("entry" to "/Profile"))),
      )
    }
    navScopes.addBreadcrumb(Breadcrumb().apply { message = "Profile" }, Hint())

    assertThat(fixture.scope.screen).isEqualTo("Host")
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY]).isEqualTo(publishedBackStack)
    assertThat(fixture.scope.breadcrumbs.map { it.message }).containsExactly("Host")
  }

  @Test
  fun `a destroyed lifecycle cannot start navigation transactions`() {
    val fixture = NavTestFixture()
    val navScopes = fixture.navScopes()

    navScopes.attach(NavTestLifecycle(Lifecycle.State.DESTROYED).lifecycle)

    assertThat(
        navScopes
          .startTransaction(TransactionContext("Home", "navigation"), TransactionOptions())
          .isNoOp
      )
      .isTrue()
  }

  @Test
  fun `attaching the same lifecycle twice does not republish navigation data`() {
    val fixture = NavTestFixture()
    val navScopes = fixture.navScopes()
    val owner = NavTestLifecycle(Lifecycle.State.RESUMED)

    navScopes.configureScope {
      it.screen = "Home"
      it.setContexts(
        NAVIGATION_CONTEXT_KEY,
        mapOf("Back Stack" to listOf(mapOf("entry" to "/Home"))),
      )
    }
    navScopes.addBreadcrumb(Breadcrumb().apply { message = "Home" })
    navScopes.attach(owner.lifecycle)
    assertThat(fixture.scope.breadcrumbs.map { it.message }).containsExactly("Home")

    val hostBackStack = mapOf("Back Stack" to listOf(mapOf("entry" to "/Host")))
    fixture.scope.screen = "host override"
    fixture.scope.setContexts(NAVIGATION_CONTEXT_KEY, hostBackStack)

    navScopes.attach(owner.lifecycle)

    assertThat(fixture.scope.screen).isEqualTo("host override")
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY]).isEqualTo(hostBackStack)
    assertThat(fixture.scope.breadcrumbs.map { it.message }).containsExactly("Home")
  }

  @Test
  fun `attaching the same lifecycle twice does not start a new navigation transaction`() {
    val fixture = NavTestFixture()
    val navScopes = fixture.navScopes()
    val owner = NavTestLifecycle(Lifecycle.State.RESUMED)

    navScopes.attach(owner.lifecycle)
    val transaction =
      navScopes.startTransaction(TransactionContext("Home", "navigation"), TransactionOptions())
    assertThat(transaction.isNoOp).isFalse()
    navScopes.configureScope { it.transaction = transaction }

    navScopes.attach(owner.lifecycle)

    assertThat(fixture.scope.transaction).isSameInstanceAs(transaction)
    assertThat(transaction.isFinished).isFalse()
  }

  @Test
  fun `trying to attach multiple lifecycles throws`() {
    val fixture = NavTestFixture()
    val navScopes = fixture.navScopes()

    navScopes.attach(NavTestLifecycle().lifecycle)

    val exception =
      try {
        navScopes.attach(NavTestLifecycle().lifecycle)
        null
      } catch (e: IllegalStateException) {
        e
      }
    assertThat(exception).isInstanceOf(IllegalStateException::class.java)
  }
}
