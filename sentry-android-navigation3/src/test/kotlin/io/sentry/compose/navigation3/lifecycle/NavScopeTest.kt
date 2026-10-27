package io.sentry.compose.navigation3.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.common.truth.Truth.assertThat
import io.sentry.IScopes
import io.sentry.ITransaction
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.ScopeObserverAdapter
import io.sentry.SentryOptions
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.protocol.App
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for the [INavScope] that [NavScopes.configureScope] hands out, which is only reachable
 * through it.
 */
class NavScopeTest {

  @Test
  fun `getScreen() - reads the real screen with the nav lease`() {
    val fixture = Fixture.create(ownsLease = true)
    val navScopes = fixture.navScopes
    navScopes.withNavScope { it.screen = "Staged" }
    fixture.scope.screen = "Host"

    val screen = navScopes.withNavScope { it.screen }

    assertThat(screen).isEqualTo("Host")
  }

  @Test
  fun `getScreen() - reads the staged screen without the nav lease`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes
    fixture.scope.screen = "Host"
    navScopes.withNavScope { it.screen = "Staged" }

    val screen = navScopes.withNavScope { it.screen }

    assertThat(screen).isEqualTo("Staged")
  }

  @Test
  fun `setScreen() - publishes the screen with the nav lease`() {
    val fixture = Fixture.create(ownsLease = true)
    val navScopes = fixture.navScopes

    navScopes.withNavScope { it.screen = "Profile" }

    assertThat(fixture.scope.screen).isEqualTo("Profile")
  }

  @Test
  fun `setScreen() - stages the screen without the nav lease until the lease is acquired`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes
    fixture.scope.screen = "Host"

    navScopes.withNavScope { it.screen = "Profile" }
    assertThat(fixture.scope.screen).isEqualTo("Host")

    fixture.acquireLease()
    assertThat(fixture.scope.screen).isEqualTo("Profile")
  }

  @Test
  fun `getApp() - returns a copy of the scope's app context`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes
    val hostApp = App().apply { appName = "Host" }
    fixture.scope.contexts.setApp(hostApp)

    val app = navScopes.withNavScope { it.app }

    assertThat(app?.appName).isEqualTo("Host")
    assertThat(app).isNotSameInstanceAs(hostApp)
  }

  @Test
  fun `getApp() - returns null when the scope has no app context`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes

    val app = navScopes.withNavScope { it.app }

    assertThat(app).isNull()
  }

  @Test
  fun `setNavigationContext() - publishes the navigation context with the nav lease`() {
    val fixture = Fixture.create(ownsLease = true)
    val navScopes = fixture.navScopes

    navScopes.withNavScope { it.setNavigationContext(navigationContext) }

    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY]).isSameInstanceAs(navigationContext)
  }

  @Test
  fun `setNavigationContext() - clears the published navigation context given null with the nav lease`() {
    val fixture = Fixture.create(ownsLease = true)
    val navScopes = fixture.navScopes
    navScopes.withNavScope { it.setNavigationContext(navigationContext) }

    navScopes.withNavScope { it.setNavigationContext(null) }

    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY]).isNull()
  }

  @Test
  fun `setNavigationContext() - stages the navigation context without the nav lease until the lease is acquired`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes
    val hostContext = mapOf("route" to "Host")
    fixture.scope.setContexts(NAVIGATION_CONTEXT_KEY, hostContext)

    navScopes.withNavScope { it.setNavigationContext(navigationContext) }
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY]).isSameInstanceAs(hostContext)

    fixture.acquireLease()
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY]).isSameInstanceAs(navigationContext)
  }

  @Test
  fun `rotatePropagationContext() - replaces the real propagation context with the nav lease`() {
    val fixture = Fixture.create(ownsLease = true)
    val navScopes = fixture.navScopes
    val realContext = fixture.scope.propagationContext

    navScopes.withNavScope { it.rotatePropagationContext() }

    assertThat(fixture.scope.propagationContext).isNotSameInstanceAs(realContext)
  }

  @Test
  fun `rotatePropagationContext() - defers the rotation without the nav lease until the lease is acquired`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes
    val realContext = fixture.scope.propagationContext

    navScopes.withNavScope { it.rotatePropagationContext() }
    assertThat(fixture.scope.propagationContext).isSameInstanceAs(realContext)

    fixture.acquireLease()
    assertThat(fixture.scope.propagationContext).isNotSameInstanceAs(realContext)
  }

  @Test
  fun `startNavTransaction() - binds the nav transaction to a free scope with the nav lease`() {
    val fixture = Fixture.create(ownsLease = true)
    val navScopes = fixture.navScopes

    val navTransaction = navScopes.withNavScope { it.startNavTransaction("/Profile") }

    assertThat(navTransaction).isNotNull()
    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransactions.single())
  }

  @Test
  fun `startNavTransaction() - starts none and keeps a bound host transaction with the nav lease`() {
    val fixture = Fixture.create(ownsLease = true)
    val navScopes = fixture.navScopes
    val hostTransaction = mock<ITransaction>()
    fixture.scope.transaction = hostTransaction

    val navTransaction = navScopes.withNavScope { it.startNavTransaction("/Profile") }

    assertThat(navTransaction).isNull()
    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isSameInstanceAs(hostTransaction)
  }

  @Test
  fun `startNavTransaction() - replaces a finished bound transaction with the nav lease`() {
    val fixture = Fixture.create(ownsLease = true)
    val navScopes = fixture.navScopes
    fixture.scope.transaction =
      mock<ITransaction>().also { whenever(it.isFinished).thenReturn(true) }

    val navTransaction = navScopes.withNavScope { it.startNavTransaction("/Profile") }

    assertThat(navTransaction).isNotNull()
    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransactions.single())
  }

  @Test
  fun `startNavTransaction() - defers the start despite a bound host transaction without the nav lease`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes
    val hostTransaction = mock<ITransaction>()
    fixture.scope.transaction = hostTransaction

    val navTransaction = navScopes.withNavScope { it.startNavTransaction("/Profile") }

    assertThat(navTransaction).isNotNull()
    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isSameInstanceAs(hostTransaction)
  }

  @Test
  fun `startNavTransaction() - starts a deferred transaction once the host's is unbound and the lease acquired`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes
    fixture.scope.transaction = mock<ITransaction>()
    navScopes.withNavScope { it.startNavTransaction("/Profile") }
    fixture.scope.clearTransaction()

    fixture.acquireLease()

    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransactions.single())
  }

  @Test
  fun `startNavTransaction() - defers the start despite another owner's bound nav transaction without the nav lease`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes
    val otherTransaction = fixture.startAnotherOwnersNavTransaction()

    val navTransaction = navScopes.withNavScope { it.startNavTransaction("/Profile") }

    assertThat(navTransaction).isNotNull()
    assertThat(fixture.startedTransactions).containsExactly(otherTransaction)
    assertThat(fixture.scope.transaction).isSameInstanceAs(otherTransaction)
  }

  @Test
  fun `startNavTransaction() - defers the start and keeps a finished bound transaction without the nav lease`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes
    val finished = mock<ITransaction>().also { whenever(it.isFinished).thenReturn(true) }
    fixture.scope.transaction = finished

    val navTransaction = navScopes.withNavScope { it.startNavTransaction("/Profile") }

    assertThat(navTransaction).isNotNull()
    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isSameInstanceAs(finished)
  }

  @Test
  fun `startNavTransaction() - starts none while its unfinished previous nav transaction is bound`() {
    val fixture = Fixture.create(ownsLease = true)
    val navScopes = fixture.navScopes
    navScopes.withNavScope { it.startNavTransaction("/Profile") }
    val previous = fixture.startedTransactions.single()

    val navTransaction = navScopes.withNavScope { it.startNavTransaction("/Settings") }

    assertThat(navTransaction).isNull()
    verify(previous, never()).finish(any())
    assertThat(fixture.startedTransactions).containsExactly(previous)
    assertThat(fixture.scope.transaction).isSameInstanceAs(previous)
  }

  @Test
  fun `startNavTransaction() - binds the new nav transaction once the previous one is finished with the nav lease`() {
    val fixture = Fixture.create(ownsLease = true)
    val navScopes = fixture.navScopes
    navScopes.withNavScope { it.startNavTransaction("/Profile") }
    navScopes.withNavScope { it.finishNavTransaction() }

    val navTransaction = navScopes.withNavScope { it.startNavTransaction("/Settings") }

    assertThat(navTransaction).isNotNull()
    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransactions.last())
  }

  @Test
  fun `startNavTransaction() - replaces the previous deferred transaction without the nav lease`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes
    val previous = navScopes.withNavScope { it.startNavTransaction("/Profile") } as NavTransaction

    val navTransaction = navScopes.withNavScope { it.startNavTransaction("/Settings") }
    fixture.acquireLease()

    assertThat(navTransaction).isNotNull()
    assertThat(previous.transaction).isNull()
    assertThat(fixture.startedTransactions.map { it.name }).containsExactly("/Settings")
    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransactions.single())
  }

  @Test
  fun `startNavTransaction() - starts none while a host transaction is bound after the previous one finishes`() {
    val fixture = Fixture.create(ownsLease = true)
    val navScopes = fixture.navScopes
    navScopes.withNavScope { it.startNavTransaction("/Profile") }
    val previous = fixture.startedTransactions.single()
    val hostTransaction = mock<ITransaction>()
    fixture.scope.transaction = hostTransaction
    navScopes.withNavScope { it.finishNavTransaction() }

    val navTransaction = navScopes.withNavScope { it.startNavTransaction("/Settings") }

    assertThat(navTransaction).isNull()
    verify(previous).finish(SpanStatus.OK)
    assertThat(fixture.startedTransactions).containsExactly(previous)
    assertThat(fixture.scope.transaction).isSameInstanceAs(hostTransaction)
  }

  @Test
  fun `startNavTransaction() - replaces the previous deferred transaction while a host transaction is bound`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes
    val previous = navScopes.withNavScope { it.startNavTransaction("/Profile") } as NavTransaction
    fixture.scope.transaction = mock<ITransaction>()

    val navTransaction = navScopes.withNavScope { it.startNavTransaction("/Settings") }
    fixture.scope.clearTransaction()
    fixture.acquireLease()

    assertThat(navTransaction).isNotNull()
    assertThat(previous.transaction).isNull()
    assertThat(fixture.startedTransactions.map { it.name }).containsExactly("/Settings")
    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransactions.single())
  }

  @Test
  fun `finishNavTransaction() - finishes and unbinds the nav transaction`() {
    val fixture = Fixture.create(ownsLease = true)
    val navScopes = fixture.navScopes
    navScopes.withNavScope { it.startNavTransaction("/Profile") }
    val started = fixture.startedTransactions.single()

    navScopes.withNavScope { it.finishNavTransaction() }

    verify(started).finish(SpanStatus.OK)
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `finishNavTransaction() - finishes it without the nav lease and keeps the leaseholder's bound`() {
    val fixture = Fixture.create(ownsLease = true)
    val navScopes = fixture.navScopes
    navScopes.withNavScope { it.startNavTransaction("/Profile") }
    val started = fixture.startedTransactions.single()
    val otherTransaction = fixture.startAnotherOwnersNavTransaction()

    navScopes.withNavScope { it.finishNavTransaction() }

    verify(started).finish(SpanStatus.OK)
    assertThat(fixture.scope.transaction).isSameInstanceAs(otherTransaction)
  }

  @Test
  fun `finishNavTransaction() - forgets the nav transaction if unbinding throws`() {
    val fixture = Fixture.create(ownsLease = true)
    val navScopes = fixture.navScopes
    navScopes.withNavScope { it.startNavTransaction("/Profile") }
    val started = fixture.startedTransactions.single()
    // Registered after the start, so that only unbinding the transaction reaches it.
    fixture.scope.options.addScopeObserver(
      object : ScopeObserverAdapter() {
        override fun setTransaction(transaction: String?) {
          throw ThrowingScopeObserverException()
        }
      }
    )

    assertThrows(ThrowingScopeObserverException::class.java) {
      navScopes.withNavScope { it.finishNavTransaction() }
    }
    navScopes.withNavScope { it.finishNavTransaction() }

    // Finished once: the second call has no nav transaction left to finish.
    verify(started).finish(SpanStatus.OK)
  }

  @Test
  fun `finishNavTransaction() - keeps a different bound transaction`() {
    val fixture = Fixture.create(ownsLease = true)
    val navScopes = fixture.navScopes
    navScopes.withNavScope { it.startNavTransaction("/Profile") }
    val hostTransaction = mock<ITransaction>()
    fixture.scope.transaction = hostTransaction

    navScopes.withNavScope { it.finishNavTransaction() }

    assertThat(fixture.scope.transaction).isSameInstanceAs(hostTransaction)
  }

  @Test
  fun `finishNavTransaction() - keeps the bound transaction when the nav transaction has not started`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes
    val otherTransaction = fixture.startAnotherOwnersNavTransaction()
    navScopes.withNavScope { it.startNavTransaction("/Profile") }

    navScopes.withNavScope { it.finishNavTransaction() }

    assertThat(fixture.scope.transaction).isSameInstanceAs(otherTransaction)
    verify(otherTransaction, never()).finish(any())
  }

  private val navigationContext = mapOf("route" to "Profile")

  /** Runs [block] with the [INavScope] handed out by [NavScopes.configureScope]. */
  private fun <T> NavScopes.withNavScope(block: (INavScope) -> T): T {
    val results = mutableListOf<T>()
    configureScope { results += block(it) }
    return results.single()
  }

  private fun INavScope.startNavTransaction(name: String): INavTransaction? =
    startNavTransaction(TransactionContext(name, "navigation"), TransactionOptions())

  /** Thrown by a scope observer, to stand in for one the host app registered. */
  private class ThrowingScopeObserverException : RuntimeException()

  private class TestLifecycleOwner : LifecycleOwner {
    override val lifecycle: LifecycleRegistry =
      LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
  }

  private class Fixture private constructor() {
    val scope = Scope(SentryOptions())
    val coordinator = NavLeaseCoordinator()
    val startedTransactions = mutableListOf<ITransaction>()

    /**
     * `LifecycleRegistry` holds its owner weakly and throws once it's collected, so the fixture
     * keeps the owners it creates alive for the test.
     */
    private val lifecycleOwners = mutableListOf<LifecycleOwner>()

    private val scopes =
      mock<IScopes>().also { scopes ->
        whenever(scopes.options).thenReturn(SentryOptions())
        doAnswer {
            (it.arguments[0] as ScopeCallback).run(scope)
            null
          }
          .whenever(scopes)
          .configureScope(any())
        whenever(scopes.startTransaction(any<TransactionContext>(), any<TransactionOptions>()))
          .thenAnswer {
            val name = (it.arguments[0] as TransactionContext).name
            mock<ITransaction>()
              .also { transaction -> whenever(transaction.name).thenReturn(name) }
              .also(startedTransactions::add)
          }
      }

    /** Attached to a resumed owner, but without the nav lease until [acquireLease]. */
    val navScopes = resumedNavScopes()

    /** Lets [navScopes] acquire the nav lease by applying its first back stack update. */
    fun acquireLease() {
      navScopes.onBackStackUpdate()
    }

    /**
     * Lets another resumed owner acquire the nav lease and start a nav transaction, which is
     * returned.
     */
    fun startAnotherOwnersNavTransaction(): ITransaction {
      val other = resumedNavScopes()
      other.onBackStackUpdate()
      other.configureScope {
        it.startNavTransaction(TransactionContext("/Other", "navigation"), TransactionOptions())
      }
      return startedTransactions.single { it.name == "/Other" }
    }

    private fun resumedNavScopes(): NavScopes =
      NavScopes(scopes, coordinator).also {
        it.attach(TestLifecycleOwner().also(lifecycleOwners::add).lifecycle)
      }

    companion object {
      fun create(ownsLease: Boolean = false): Fixture =
        Fixture().also {
          if (ownsLease) {
            it.acquireLease()
          }
        }
    }
  }
}
