package io.sentry.compose.navigation3.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.common.truth.Truth.assertThat
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.ITransaction
import io.sentry.NoOpTransaction
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryOptions
import io.sentry.SpanStatus
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
  fun `startTransaction() - starts and retains a transaction with the nav lease`() {
    val fixture = Fixture()
    val navScopes = fixture.navScopes()
    navScopes.attach(fixture.lifecycle(Lifecycle.State.RESUMED))
    navScopes.applyFirstBackStackUpdate()

    val transaction = navScopes.startTransaction(TransactionContext("/Home", "op"), options)

    val started = fixture.startedTransaction("/Home")
    assertThat(transaction?.backingTransaction).isSameInstanceAs(started)
    assertThat(fixture.coordinator.isNavTransaction(started)).isTrue()
  }

  @Test
  fun `startTransaction() - returns null when the delegate declines with the nav lease`() {
    val fixture = Fixture()
    fixture.declinesTransactions = true
    val navScopes = fixture.navScopes()
    navScopes.attach(fixture.lifecycle(Lifecycle.State.RESUMED))
    navScopes.applyFirstBackStackUpdate()

    val transaction = navScopes.startTransaction(TransactionContext("/Home", "op"), options)

    assertThat(transaction).isNull()
  }

  @Test
  fun `startTransaction() - defers the transaction start without the nav lease`() {
    val fixture = Fixture()
    val covered = fixture.coveredNavScopes()

    val transaction = covered.startTransaction(TransactionContext("/Result", "op"), options)

    assertThat(fixture.hasLease(covered)).isFalse()
    assertThat(transaction).isNotNull()
    assertThat(transaction?.backingTransaction).isNull()
    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `startTransaction() - without the nav lease defers a propagation context rotation until the lease is acquired`() {
    val fixture = Fixture()
    val covered = fixture.coveredNavScopes()
    val incomingContext = fixture.scope.propagationContext

    covered.startTransaction(TransactionContext("/Result", "op"), options)
    assertThat(fixture.scope.propagationContext).isSameInstanceAs(incomingContext)

    fixture.incoming.dispose()

    assertThat(fixture.hasLease(covered)).isTrue()
    assertThat(fixture.scope.propagationContext).isNotSameInstanceAs(incomingContext)
  }

  @Test
  fun `startTransaction() - starts no transaction after dispose`() {
    val fixture = Fixture()
    val navScopes = fixture.navScopes()
    navScopes.attach(fixture.lifecycle())
    navScopes.applyFirstBackStackUpdate()
    navScopes.dispose()

    val transaction = navScopes.startTransaction(TransactionContext("/Home", "op"), options)

    assertThat(transaction?.backingTransaction).isNull()
    assertThat(fixture.startedTransactions).isEmpty()
  }

  @Test
  fun `a deferred transaction starts and is bound when the nav lease is acquired`() {
    val fixture = Fixture()
    val covered = fixture.coveredNavScopes()
    val transaction = covered.startTransaction(TransactionContext("/Result", "op"), options)

    fixture.incoming.dispose()

    val started = fixture.startedTransaction("/Result")
    assertThat(fixture.scope.transaction).isSameInstanceAs(started)
    assertThat(transaction?.backingTransaction).isSameInstanceAs(started)
  }

  @Test
  fun `a preloaded owner's deferred transaction starts when the owner resumes`() {
    val fixture = Fixture()
    val current = fixture.navScopes()
    current.attach(fixture.lifecycle(Lifecycle.State.RESUMED))
    current.applyFirstBackStackUpdate()
    val preloaded = fixture.navScopes()
    val preloadedLifecycle = fixture.lifecycle(Lifecycle.State.STARTED)
    preloaded.attach(preloadedLifecycle)
    preloaded.applyFirstBackStackUpdate()
    preloaded.startTransaction(TransactionContext("/PageB", "op"), options)
    check(!fixture.hasLease(preloaded))

    preloadedLifecycle.currentState = Lifecycle.State.RESUMED

    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransaction("/PageB"))
  }

  @Test
  fun `a started deferred transaction is restored when the nav lease is regained`() {
    val fixture = Fixture()
    val covered = fixture.coveredNavScopes()
    covered.startTransaction(TransactionContext("/Result", "op"), options)
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
  fun `a started deferred transaction is finished on dispose`() {
    val fixture = Fixture()
    val covered = fixture.coveredNavScopes()
    covered.startTransaction(TransactionContext("/Result", "op"), options)
    fixture.incoming.dispose()
    val started = fixture.startedTransaction("/Result")

    covered.dispose()

    verify(started).finish(any())
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `a deferred transaction does not start while a host transaction is bound`() {
    val fixture = Fixture()
    val covered = fixture.coveredNavScopes()
    covered.startTransaction(TransactionContext("/Result", "op"), options)
    val hostTransaction = mock<ITransaction>()
    fixture.scope.transaction = hostTransaction

    fixture.incoming.dispose()

    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isSameInstanceAs(hostTransaction)
  }

  @Test
  fun `a deferred transaction starts in place of a finished bound transaction`() {
    val fixture = Fixture()
    val covered = fixture.coveredNavScopes()
    covered.startTransaction(TransactionContext("/Result", "op"), options)
    fixture.scope.transaction =
      mock<ITransaction>().also { whenever(it.isFinished).thenReturn(true) }

    fixture.incoming.dispose()

    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransaction("/Result"))
  }

  @Test
  fun `a finished deferred transaction does not start when the nav lease is acquired`() {
    val fixture = Fixture()
    val covered = fixture.coveredNavScopes()
    val transaction = covered.startTransaction(TransactionContext("/Result", "op"), options)
    transaction?.finish(SpanStatus.OK)

    fixture.incoming.dispose()

    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `only the most recently deferred transaction starts when the nav lease is acquired`() {
    val fixture = Fixture()
    val covered = fixture.coveredNavScopes()
    covered.startTransaction(TransactionContext("/Result", "op"), options)
    covered.startTransaction(TransactionContext("/Receipt", "op"), options)

    fixture.incoming.dispose()

    assertThat(fixture.startedTransactions.map { it.name }).containsExactly("/Receipt")
    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransaction("/Receipt"))
  }

  @Test
  fun `a deferred transaction does not start after dispose`() {
    val fixture = Fixture()
    val covered = fixture.coveredNavScopes()
    covered.startTransaction(TransactionContext("/Result", "op"), options)

    covered.dispose()
    fixture.incoming.dispose()

    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `a declined deferred start binds no transaction but still rotates the propagation context`() {
    val fixture = Fixture()
    val covered = fixture.coveredNavScopes()
    val incomingContext = fixture.scope.propagationContext
    covered.startTransaction(TransactionContext("/Result", "op"), options)
    fixture.declinesTransactions = true

    fixture.incoming.dispose()

    assertThat(fixture.scope.transaction).isNull()
    assertThat(fixture.scope.propagationContext).isNotSameInstanceAs(incomingContext)
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

  private val options = TransactionOptions()

  private val INavTransaction.backingTransaction: ITransaction?
    get() = (this as NavTransactionHandle).transaction

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
     * Returns a resumed [NavScopes] that applied its first back stack update and then lost the nav
     * lease to the resumed [incoming] one. It regains the lease when [incoming] is disposed.
     */
    fun coveredNavScopes(): NavScopes {
      val covered = navScopes()
      covered.attach(lifecycle(Lifecycle.State.RESUMED))
      covered.onBackStackUpdate()
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
  }
}
