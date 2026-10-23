package io.sentry.compose.navigation3.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.common.truth.Truth.assertThat
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.ITransaction
import io.sentry.NoOpTransaction
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.ScopeObserverAdapter
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.protocol.Contexts
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NavScopesTest {

  @Test
  fun `attach() - lets the first back stack update acquire the nav lease`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes()
    navScopes.attach(fixture.lifecycle())

    navScopes.applyFirstBackStackUpdate()

    assertThat(fixture.hasLease(navScopes)).isTrue()
  }

  @Test
  fun `attach() - does nothing for the lifecycle that's already attached`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes()
    val lifecycle = fixture.lifecycle()
    navScopes.attach(lifecycle)

    navScopes.attach(lifecycle)

    assertThat(lifecycle.observerCount).isEqualTo(1)
  }

  @Test
  fun `attach() - ignores a second, different lifecycle`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes()
    val first = TestLifecycleOwner()
    val second = TestLifecycleOwner()
    navScopes.attach(first.lifecycle)

    navScopes.attach(second.lifecycle)

    assertThat(first.lifecycle.observerCount).isEqualTo(1)
    assertThat(second.lifecycle.observerCount).isEqualTo(0)
  }

  @Test
  fun `attach() - ignores a destroyed lifecycle`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes()
    val lifecycle = fixture.lifecycle(Lifecycle.State.DESTROYED)

    navScopes.attach(lifecycle)
    navScopes.applyFirstBackStackUpdate()

    assertThat(fixture.hasLease(navScopes)).isFalse()
  }

  @Test
  fun `attach() - accepts a new lifecycle after the attached one is destroyed`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes()
    val first = fixture.lifecycle(Lifecycle.State.RESUMED)
    navScopes.attach(first)
    navScopes.applyFirstBackStackUpdate()
    first.currentState = Lifecycle.State.DESTROYED
    val second = fixture.lifecycle(Lifecycle.State.RESUMED)

    navScopes.attach(second)
    navScopes.applyFirstBackStackUpdate()

    assertThat(second.observerCount).isEqualTo(1)
    assertThat(fixture.hasLease(navScopes)).isTrue()
  }

  @Test
  fun `onBackStackUpdate() - doesn't acquire the nav lease before attach`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes()

    navScopes.applyFirstBackStackUpdate()

    assertThat(fixture.hasLease(navScopes)).isFalse()
  }

  @Test
  fun `destroying the attached lifecycle finishes the nav transaction`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes()
    val lifecycle = fixture.lifecycle(Lifecycle.State.RESUMED)
    navScopes.attach(lifecycle)
    navScopes.applyFirstBackStackUpdate()
    navScopes.startNavTransaction("/Home")
    val started = fixture.startedTransaction("/Home")

    lifecycle.currentState = Lifecycle.State.DESTROYED

    verify(started).finish(SpanStatus.OK)
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `destroying the attached lifecycle without a scope doesn't finish the nav transaction`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes()
    val lifecycle = fixture.lifecycle(Lifecycle.State.RESUMED)
    navScopes.attach(lifecycle)
    navScopes.applyFirstBackStackUpdate()
    navScopes.startNavTransaction("/Home")
    val started = fixture.startedTransaction("/Home")
    fixture.isEnabled = false

    lifecycle.currentState = Lifecycle.State.DESTROYED

    verify(started, never()).finish(any())
  }

  @Test
  fun `addBreadcrumb() - records breadcrumbs immediately without the nav lease`() {
    val fixture = Fixture.create()
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
  fun `dispose() - finishes a displaced owner's nav transaction and keeps the leaseholder's bound`() {
    val fixture = Fixture.create()
    val covered = fixture.coveredNavScopes(startedRoute = "/Home")
    fixture.incoming.startNavTransaction("/Incoming")
    val coveredTransaction = fixture.startedTransaction("/Home")
    val incomingTransaction = fixture.startedTransaction("/Incoming")

    covered.dispose()

    verify(coveredTransaction).finish(SpanStatus.OK)
    assertThat(fixture.scope.transaction).isSameInstanceAs(incomingTransaction)
  }

  @Test
  fun `dispose() - finishes the nav transaction when handing off the nav lease throws`() {
    val fixture = Fixture.create()
    val covered = fixture.coveredNavScopes()
    covered.configureScope { it.screen = "/Result" }
    fixture.incoming.startNavTransaction("/Incoming")
    val incomingTransaction = fixture.startedTransaction("/Incoming")
    val failure = IllegalStateException("observer failure")
    fixture.scope.options.addScopeObserver(
      object : ScopeObserverAdapter() {
        override fun setContexts(contexts: Contexts) {
          throw failure
        }
      }
    )

    val thrown = assertThrows(IllegalStateException::class.java) { fixture.incoming.dispose() }

    assertThat(thrown).isSameInstanceAs(failure)
    verify(incomingTransaction).finish(SpanStatus.OK)
  }

  @Test
  fun `dispose() - doesn't finish the nav transaction without a scope`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes()
    navScopes.attach(fixture.lifecycle(Lifecycle.State.RESUMED))
    navScopes.applyFirstBackStackUpdate()
    navScopes.startNavTransaction("/Home")
    val started = fixture.startedTransaction("/Home")
    fixture.isEnabled = false

    navScopes.dispose()

    verify(started, never()).finish(any())
  }

  @Test
  fun `dispose() - forgets a nav transaction staged while never attached`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes()
    navScopes.startNavTransaction("/Home")

    navScopes.dispose()
    navScopes.attach(fixture.lifecycle(Lifecycle.State.RESUMED))
    navScopes.applyFirstBackStackUpdate()

    assertThat(fixture.hasLease(navScopes)).isTrue()
    assertThat(fixture.startedTransactions).isEmpty()
  }

  @Test
  fun `startNavTransaction() - starts and owns a nav transaction with the nav lease`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes()
    navScopes.attach(fixture.lifecycle(Lifecycle.State.RESUMED))
    navScopes.applyFirstBackStackUpdate()

    val transaction = navScopes.startNavTransaction("/Home")

    val started = fixture.startedTransaction("/Home")
    assertThat(transaction).isNotNull()
    assertThat(fixture.coordinator.isNavTransaction(started)).isTrue()
  }

  @Test
  fun `startNavTransaction() - returns null when the delegate declines with the nav lease`() {
    val fixture = Fixture.create(declinesTransactions = true)
    val navScopes = fixture.navScopes()
    navScopes.attach(fixture.lifecycle(Lifecycle.State.RESUMED))
    navScopes.applyFirstBackStackUpdate()

    val transaction = navScopes.startNavTransaction("/Home")

    assertThat(transaction).isNull()
  }

  @Test
  fun `startNavTransaction() - logs the route when another transaction blocks the start`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes()
    navScopes.attach(fixture.lifecycle(Lifecycle.State.RESUMED))
    navScopes.applyFirstBackStackUpdate()
    fixture.scope.transaction = mock<ITransaction>()

    navScopes.startNavTransaction("/Home")

    verify(fixture.logger).log(eq(SentryLevel.DEBUG), any<String>(), eq("/Home"))
  }

  @Test
  fun `startNavTransaction() - defers the nav transaction start without the nav lease`() {
    val fixture = Fixture.create()
    val covered = fixture.coveredNavScopes()

    val transaction = covered.startNavTransaction("/Result")

    assertThat(fixture.hasLease(covered)).isFalse()
    assertThat(transaction).isNotNull()
    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `startNavTransaction() - defers the propagation context rotation without the nav lease`() {
    val fixture = Fixture.create()
    val covered = fixture.coveredNavScopes()
    val incomingContext = fixture.scope.propagationContext

    covered.startNavTransaction("/Result")
    assertThat(fixture.scope.propagationContext).isSameInstanceAs(incomingContext)

    fixture.incoming.dispose()

    assertThat(fixture.hasLease(covered)).isTrue()
    assertThat(fixture.scope.propagationContext).isNotSameInstanceAs(incomingContext)
  }

  @Test
  fun `startNavTransaction() - starts no nav transaction after dispose`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes()
    navScopes.attach(fixture.lifecycle())
    navScopes.applyFirstBackStackUpdate()
    navScopes.dispose()

    navScopes.startNavTransaction("/Home")

    assertThat(fixture.startedTransactions).isEmpty()
  }

  @Test
  fun `finishNavTransaction() - finishes the started nav transaction`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes()
    navScopes.attach(fixture.lifecycle(Lifecycle.State.RESUMED))
    navScopes.applyFirstBackStackUpdate()
    navScopes.startNavTransaction("/Home")
    val started = fixture.startedTransaction("/Home")

    navScopes.finishNavTransaction()

    verify(started).finish(SpanStatus.OK)
  }

  @Test
  fun `finishNavTransaction() - finishes the started nav transaction only once`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes()
    navScopes.attach(fixture.lifecycle(Lifecycle.State.RESUMED))
    navScopes.applyFirstBackStackUpdate()
    navScopes.startNavTransaction("/Home")
    val started = fixture.startedTransaction("/Home")
    navScopes.finishNavTransaction()

    navScopes.finishNavTransaction()

    verify(started, times(1)).finish(any())
  }

  @Test
  fun `finishNavTransaction() - does nothing without a nav transaction`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes()
    navScopes.attach(fixture.lifecycle(Lifecycle.State.RESUMED))
    navScopes.applyFirstBackStackUpdate()
    val hostTransaction = mock<ITransaction>()
    fixture.scope.transaction = hostTransaction

    navScopes.finishNavTransaction()

    verify(hostTransaction, never()).finish(any())
    assertThat(fixture.scope.transaction).isSameInstanceAs(hostTransaction)
  }

  @Test
  fun `a nav transaction starts and is bound after the previous one is finished`() {
    val fixture = Fixture.create()
    val navScopes = fixture.navScopes()
    navScopes.attach(fixture.lifecycle(Lifecycle.State.RESUMED))
    navScopes.applyFirstBackStackUpdate()
    navScopes.startNavTransaction("/Home")
    navScopes.finishNavTransaction()

    val transaction = navScopes.startNavTransaction("/Profile")

    assertThat(transaction).isNotNull()
    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransaction("/Profile"))
  }

  @Test
  fun `losing the nav lease unbinds the nav transaction without finishing it`() {
    val fixture = Fixture.create()

    fixture.coveredNavScopes(startedRoute = "/Home")

    val started = fixture.startedTransaction("/Home")
    assertThat(fixture.scope.transaction).isNull()
    verify(started, never()).finish(any())
  }

  @Test
  fun `regaining the nav lease rebinds the unfinished nav transaction`() {
    val fixture = Fixture.create()
    fixture.coveredNavScopes(startedRoute = "/Home")

    fixture.incoming.dispose()

    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransaction("/Home"))
  }

  @Test
  fun `regaining the nav lease republishes the nav data before rebinding the nav transaction`() {
    val fixture = Fixture.create()
    val covered = fixture.coveredNavScopes(startedRoute = "/Home")
    covered.configureScope { it.screen = "Home" }
    val screensAtBinding = mutableListOf<String?>()
    fixture.scope.options.addScopeObserver(
      object : ScopeObserverAdapter() {
        override fun setTransaction(transaction: String?) {
          if (transaction == "/Home") screensAtBinding += fixture.scope.screen
        }
      }
    )

    fixture.incoming.dispose()

    assertThat(screensAtBinding).containsExactly("Home")
  }

  @Test
  fun `a finished nav transaction is discarded when the nav lease is regained`() {
    val fixture = Fixture.create()
    val covered = fixture.coveredNavScopes(startedRoute = "/Home")
    val started = fixture.startedTransaction("/Home")
    whenever(started.isFinished).thenReturn(true)

    fixture.incoming.dispose()

    assertThat(fixture.hasLease(covered)).isTrue()
    assertThat(fixture.scope.transaction).isNull()
    assertThat(fixture.coordinator.isNavTransaction(started)).isFalse()
  }

  @Test
  fun `a nav transaction finished without the nav lease is not bound when the nav lease is regained`() {
    val fixture = Fixture.create()
    val covered = fixture.coveredNavScopes(startedRoute = "/Home")
    // The mock keeps reporting itself unfinished, like a transaction waiting for its child spans.
    covered.finishNavTransaction()

    fixture.incoming.dispose()

    assertThat(fixture.hasLease(covered)).isTrue()
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `a deferred transaction starts and is bound when the nav lease is acquired`() {
    val fixture = Fixture.create()
    val covered = fixture.coveredNavScopes()
    covered.startNavTransaction("/Result")

    fixture.incoming.dispose()

    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransaction("/Result"))
  }

  @Test
  fun `a deferred transaction receives the data recorded on its handle once it starts`() {
    val fixture = Fixture.create()
    val covered = fixture.coveredNavScopes()
    val transaction = covered.startNavTransaction("/Result")
    transaction?.setData("arguments", "id=1")

    fixture.incoming.dispose()

    verify(fixture.startedTransaction("/Result")).setData("arguments", "id=1")
  }

  @Test
  fun `a preloaded owner's deferred transaction starts when the owner resumes`() {
    val fixture = Fixture.create()
    val current = fixture.navScopes()
    current.attach(fixture.lifecycle(Lifecycle.State.RESUMED))
    current.applyFirstBackStackUpdate()
    val preloaded = fixture.navScopes()
    val preloadedLifecycle = fixture.lifecycle(Lifecycle.State.STARTED)
    preloaded.attach(preloadedLifecycle)
    preloaded.applyFirstBackStackUpdate()
    preloaded.startNavTransaction("/PageB")
    check(!fixture.hasLease(preloaded))

    preloadedLifecycle.currentState = Lifecycle.State.RESUMED

    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransaction("/PageB"))
  }

  @Test
  fun `a started deferred transaction is restored when the nav lease is regained`() {
    val fixture = Fixture.create()
    val covered = fixture.coveredNavScopes()
    covered.startNavTransaction("/Result")
    fixture.incoming.dispose()
    val started = fixture.startedTransaction("/Result")
    val nextIncoming = fixture.navScopes()
    nextIncoming.attach(fixture.lifecycle(Lifecycle.State.RESUMED))
    nextIncoming.applyFirstBackStackUpdate()
    check(fixture.scope.transaction == null)

    nextIncoming.dispose()

    assertThat(fixture.scope.transaction).isSameInstanceAs(started)
    assertThat(fixture.startedTransactions).containsExactly(started)
  }

  @Test
  fun `regaining the nav lease with a started nav transaction logs no blocked nav transaction start`() {
    val fixture = Fixture.create()
    val covered = fixture.coveredNavScopes()
    covered.startNavTransaction("/Result")
    fixture.incoming.dispose()
    val nextIncoming = fixture.navScopes()
    nextIncoming.attach(fixture.lifecycle(Lifecycle.State.RESUMED))
    nextIncoming.applyFirstBackStackUpdate()

    nextIncoming.dispose()

    verify(fixture.logger, never()).log(eq(SentryLevel.DEBUG), any<String>(), eq("/Result"))
  }

  @Test
  fun `a started deferred transaction is finished on dispose`() {
    val fixture = Fixture.create()
    val covered = fixture.coveredNavScopes()
    covered.startNavTransaction("/Result")
    fixture.incoming.dispose()
    val started = fixture.startedTransaction("/Result")

    covered.dispose()

    verify(started).finish(any())
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `a deferred transaction does not start while a host transaction is bound`() {
    val fixture = Fixture.create()
    val covered = fixture.coveredNavScopes()
    covered.startNavTransaction("/Result")
    val hostTransaction = mock<ITransaction>()
    fixture.scope.transaction = hostTransaction

    fixture.incoming.dispose()

    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isSameInstanceAs(hostTransaction)
  }

  @Test
  fun `a deferred transaction blocked by a host transaction logs its route`() {
    val fixture = Fixture.create()
    val covered = fixture.coveredNavScopes()
    covered.startNavTransaction("/Result")
    fixture.scope.transaction = mock<ITransaction>()

    fixture.incoming.dispose()

    verify(fixture.logger).log(eq(SentryLevel.DEBUG), any<String>(), eq("/Result"))
  }

  @Test
  fun `a deferred transaction starts in place of a finished bound transaction`() {
    val fixture = Fixture.create()
    val covered = fixture.coveredNavScopes()
    covered.startNavTransaction("/Result")
    fixture.scope.transaction =
      mock<ITransaction>().also { whenever(it.isFinished).thenReturn(true) }

    fixture.incoming.dispose()

    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransaction("/Result"))
  }

  @Test
  fun `a deferred transaction requested while a finished transaction is bound starts in its place`() {
    val fixture = Fixture.create()
    val covered = fixture.coveredNavScopes()
    fixture.scope.transaction =
      mock<ITransaction>().also { whenever(it.isFinished).thenReturn(true) }
    covered.startNavTransaction("/Result")

    fixture.incoming.dispose()

    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransaction("/Result"))
  }

  @Test
  fun `a finished deferred transaction does not start when the nav lease is acquired`() {
    val fixture = Fixture.create()
    val covered = fixture.coveredNavScopes()
    covered.startNavTransaction("/Result")
    covered.finishNavTransaction()

    fixture.incoming.dispose()

    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `only the most recently deferred transaction starts when the nav lease is acquired`() {
    val fixture = Fixture.create()
    val covered = fixture.coveredNavScopes()
    covered.startNavTransaction("/Result")
    covered.startNavTransaction("/Receipt")

    fixture.incoming.dispose()

    assertThat(fixture.startedTransactions.map { it.name }).containsExactly("/Receipt")
    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransaction("/Receipt"))
  }

  @Test
  fun `a deferred transaction interrupted by a failed nav lease acquisition starts on the next one`() {
    val fixture = Fixture.create()
    val covered = fixture.coveredNavScopes()
    covered.configureScope { it.screen = "/Result" }
    covered.startNavTransaction("/Result")
    val failure = IllegalStateException("observer failure")
    var observerThrows = true
    fixture.scope.options.addScopeObserver(
      object : ScopeObserverAdapter() {
        override fun setContexts(contexts: Contexts) {
          if (observerThrows) throw failure
        }
      }
    )
    val thrown = assertThrows(IllegalStateException::class.java) { fixture.incoming.dispose() }
    assertThat(thrown).isSameInstanceAs(failure)
    check(fixture.startedTransactions.isEmpty())
    observerThrows = false
    val nextIncoming = fixture.navScopes()
    nextIncoming.attach(fixture.lifecycle(Lifecycle.State.RESUMED))
    nextIncoming.applyFirstBackStackUpdate()
    check(!fixture.hasLease(covered))

    nextIncoming.dispose()

    assertThat(fixture.hasLease(covered)).isTrue()
    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransaction("/Result"))
  }

  @Test
  fun `a deferred transaction does not start after dispose`() {
    val fixture = Fixture.create()
    val covered = fixture.coveredNavScopes()
    covered.startNavTransaction("/Result")

    covered.dispose()
    fixture.incoming.dispose()

    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `a declined deferred start binds no transaction but still rotates the propagation context`() {
    val fixture = Fixture.create()
    val covered = fixture.coveredNavScopes()
    val incomingContext = fixture.scope.propagationContext
    covered.startNavTransaction("/Result")
    fixture.declinesTransactions = true

    fixture.incoming.dispose()

    assertThat(fixture.scope.transaction).isNull()
    assertThat(fixture.scope.propagationContext).isNotSameInstanceAs(incomingContext)
  }

  /** Starts a nav transaction for [name] through the [INavScope] handed out by [NavScopes]. */
  private fun NavScopes.startNavTransaction(name: String): INavTransaction? {
    var transaction: INavTransaction? = null
    configureScope {
      transaction = it.startNavTransaction(TransactionContext(name, "op"), TransactionOptions())
    }
    return transaction
  }

  /** Finishes the nav transaction through the [INavScope] handed out by [NavScopes]. */
  private fun NavScopes.finishNavTransaction() {
    configureScope { it.finishNavTransaction() }
  }

  private fun NavScopes.applyFirstBackStackUpdate() {
    onBackStackUpdate()
  }

  private class TestLifecycleOwner(initialState: Lifecycle.State = Lifecycle.State.CREATED) :
    LifecycleOwner {
    override val lifecycle: LifecycleRegistry =
      LifecycleRegistry.createUnsafe(this).apply {
        // A lifecycle must be created before it can be destroyed.
        currentState = Lifecycle.State.CREATED
        currentState = initialState
      }
  }

  private class Fixture private constructor() {
    var isEnabled = true
    val scope = Scope(SentryOptions())
    val coordinator = NavLeaseCoordinator()
    val logger = mock<ILogger>()
    val scopes =
      mock<IScopes>().also { scopes ->
        whenever(scopes.options)
          .thenReturn(
            SentryOptions().apply {
              setLogger(this@Fixture.logger)
              isDebug = true
            }
          )
        doAnswer {
            if (isEnabled) {
              (it.arguments[0] as ScopeCallback).run(scope)
            }
            null
          }
          .whenever(scopes)
          .configureScope(any())
        whenever(scopes.startTransaction(any<TransactionContext>(), any<TransactionOptions>()))
          .thenAnswer {
            if (declinesTransactions) {
              NoOpTransaction.getInstance()
            } else {
              val name = (it.arguments[0] as TransactionContext).name
              mock<ITransaction>()
                .also { transaction -> whenever(transaction.name).thenReturn(name) }
                .also(startedTransactions::add)
            }
          }
      }

    /** Whether [scopes] declines to start transactions, returning a no-op transaction instead. */
    var declinesTransactions = false
    val startedTransactions = mutableListOf<ITransaction>()

    /** The resumed leaseholder that covers the [NavScopes] returned by [coveredNavScopes]. */
    lateinit var incoming: NavScopes

    /**
     * `LifecycleRegistry` holds its owner weakly and throws once it's collected, so the fixture
     * keeps the owners created by [lifecycle] alive for the test.
     */
    private val lifecycleOwners = mutableListOf<LifecycleOwner>()

    fun navScopes(): NavScopes = NavScopes(scopes, coordinator)

    fun lifecycle(state: Lifecycle.State = Lifecycle.State.CREATED): LifecycleRegistry =
      TestLifecycleOwner(state).also(lifecycleOwners::add).lifecycle

    fun startedTransaction(name: String): ITransaction = startedTransactions.single {
      it.name == name
    }

    /**
     * Returns a resumed [NavScopes] that applied its first back stack update, started a nav
     * transaction for [startedRoute] if given, and then lost the nav lease to the resumed
     * [incoming] one. It regains the lease when [incoming] is disposed.
     */
    fun coveredNavScopes(startedRoute: String? = null): NavScopes {
      val covered = navScopes()
      covered.attach(lifecycle(Lifecycle.State.RESUMED))
      covered.onBackStackUpdate()
      if (startedRoute != null) {
        covered.configureScope {
          it.startNavTransaction(TransactionContext(startedRoute, "op"), TransactionOptions())
        }
      }
      incoming = navScopes()
      incoming.attach(lifecycle(Lifecycle.State.RESUMED))
      incoming.onBackStackUpdate()
      check(!hasLease(covered))
      return covered
    }

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

    companion object {
      /**
       * [declinesTransactions] can also be changed later, e.g., once a transaction was deferred.
       */
      fun create(declinesTransactions: Boolean = false): Fixture =
        Fixture().also { it.declinesTransactions = declinesTransactions }
    }
  }
}
