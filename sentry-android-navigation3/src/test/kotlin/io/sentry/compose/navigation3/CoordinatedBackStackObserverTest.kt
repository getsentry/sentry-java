package io.sentry.compose.navigation3

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.common.truth.Truth.assertThat
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IScopes
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.compose.navigation3.lifecycle.NAVIGATION_CONTEXT_KEY
import io.sentry.compose.navigation3.lifecycle.NavLeaseCoordinator
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CoordinatedBackStackObserverTest {

  @Test
  fun `onBackStackChanged() - acquires the nav lease before applying the first update`() {
    val fixture = Fixture()
    fixture.givenPausedLeaseholder()
    val sut = fixture.getSut()

    sut.onBackStackChanged(listOf("Home"))

    // An update applied before the lease is acquired is staged and gets no nav transaction.
    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransactions.single())
  }

  @Test
  fun `a resumed owner doesn't rebind a nav transaction it retired by navigating without the nav lease`() {
    val fixture = Fixture()
    val homeLifecycle = fixture.resumedLifecycle()
    val homeObserver = fixture.getSut(homeLifecycle)
    val pickerObserver = fixture.getSut()
    homeObserver.onBackStackChanged(listOf("Home"))
    val homeTransaction = fixture.startedTransaction("/Home")
    // A pending child span keeps the nav transaction, which waits for its children, unfinished.
    homeTransaction.startChild("http.client")
    homeLifecycle.currentState = Lifecycle.State.STARTED
    pickerObserver.onBackStackChanged(listOf("Picker"))
    homeObserver.onBackStackChanged(listOf("Home", "Result"))
    check(!homeTransaction.isFinished)

    homeLifecycle.currentState = Lifecycle.State.RESUMED

    assertThat(fixture.scope.screen).isEqualTo("/Result")
    // "/Result" was reached without the nav lease, so it gets a new nav transaction when the owner
    // regains the lease, rather than the one retired by navigating to it.
    assertThat(fixture.scope.transaction).isNotSameInstanceAs(homeTransaction)
    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransaction("/Result"))
  }

  @Test
  fun `a page swiped to starts a nav transaction when it resumes`() {
    val fixture = Fixture()
    val pageALifecycle = fixture.resumedLifecycle()
    val pageBLifecycle = fixture.resumedLifecycle().apply { currentState = Lifecycle.State.STARTED }
    val pageA = fixture.getSut(pageALifecycle)
    val pageB = fixture.getSut(pageBLifecycle)
    pageA.onBackStackChanged(listOf("PageA"))
    // Page B is dragged into view while page A is still resumed, so it doesn't get the nav lease.
    pageB.onBackStackChanged(listOf("PageB"))
    check(fixture.startedTransactions.map { it.name } == listOf("/PageA"))

    // The swipe settles: ViewPager2 resumes page B and pauses page A.
    pageBLifecycle.currentState = Lifecycle.State.RESUMED
    pageALifecycle.currentState = Lifecycle.State.STARTED

    val pageBTransaction = fixture.startedTransaction("/PageB")
    assertThat(fixture.scope.transaction).isSameInstanceAs(pageBTransaction)
    assertThat(fixture.scope.screen).isEqualTo("/PageB")
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY])
      .isEqualTo(mapOf("Back Stack" to listOf(mapOf("entry" to "/PageB"))))
    assertThat(fixture.breadcrumbs.last().data).containsExactly("to", "/PageB")
  }

  @Test
  fun `a page swiped to starts a nav transaction when the previous page pauses before it resumes`() {
    val fixture = Fixture()
    val pageALifecycle = fixture.resumedLifecycle()
    val pageBLifecycle = fixture.resumedLifecycle().apply { currentState = Lifecycle.State.STARTED }
    val pageA = fixture.getSut(pageALifecycle)
    val pageB = fixture.getSut(pageBLifecycle)
    pageA.onBackStackChanged(listOf("PageA"))
    pageB.onBackStackChanged(listOf("PageB"))

    pageALifecycle.currentState = Lifecycle.State.STARTED
    pageBLifecycle.currentState = Lifecycle.State.RESUMED

    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransaction("/PageB"))
    assertThat(fixture.scope.screen).isEqualTo("/PageB")
  }

  @Test
  fun `swiping back to a page restores its unfinished nav transaction instead of starting another`() {
    val fixture = Fixture()
    val pageALifecycle = fixture.resumedLifecycle()
    val pageBLifecycle = fixture.resumedLifecycle().apply { currentState = Lifecycle.State.STARTED }
    val pageA = fixture.getSut(pageALifecycle)
    val pageB = fixture.getSut(pageBLifecycle)
    pageA.onBackStackChanged(listOf("PageA"))
    val pageATransaction = fixture.startedTransaction("/PageA")
    pageATransaction.startChild("http.client")
    pageB.onBackStackChanged(listOf("PageB"))
    pageBLifecycle.currentState = Lifecycle.State.RESUMED
    pageALifecycle.currentState = Lifecycle.State.STARTED

    pageALifecycle.currentState = Lifecycle.State.RESUMED
    pageBLifecycle.currentState = Lifecycle.State.STARTED

    assertThat(fixture.scope.transaction).isSameInstanceAs(pageATransaction)
    assertThat(fixture.startedTransactions.map { it.name })
      .containsExactly("/PageA", "/PageB")
      .inOrder()
  }

  @Test
  fun `swiping back to a page whose nav transaction finished starts another`() {
    val fixture = Fixture()
    val pageALifecycle = fixture.resumedLifecycle()
    val pageBLifecycle = fixture.resumedLifecycle().apply { currentState = Lifecycle.State.STARTED }
    val pageA = fixture.getSut(pageALifecycle)
    val pageB = fixture.getSut(pageBLifecycle)
    pageA.onBackStackChanged(listOf("PageA"))
    val pageATransaction = fixture.startedTransaction("/PageA")
    pageB.onBackStackChanged(listOf("PageB"))
    pageBLifecycle.currentState = Lifecycle.State.RESUMED
    pageALifecycle.currentState = Lifecycle.State.STARTED
    pageATransaction.finish()

    pageALifecycle.currentState = Lifecycle.State.RESUMED
    pageBLifecycle.currentState = Lifecycle.State.STARTED

    val transactions = fixture.startedTransactions.map { it.name }
    assertThat(transactions).containsExactly("/PageA", "/PageB", "/PageA").inOrder()
    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.startedTransactions.last())
    assertThat(fixture.scope.screen).isEqualTo("/PageA")
  }

  @Test
  fun `with nav transactions disabled, a page swiped back to records a breadcrumb and a fresh trace`() {
    val fixture = Fixture()
    val options = SentryNavOptions { enableNavigationTransactions = false }
    val pageALifecycle = fixture.resumedLifecycle()
    val pageBLifecycle = fixture.resumedLifecycle().apply { currentState = Lifecycle.State.STARTED }
    val pageA = fixture.getSut(pageALifecycle, options)
    val pageB = fixture.getSut(pageBLifecycle, options)
    // Page A applies its update with the nav lease, so it has no rotation pending when it regains
    // the lease below: any new propagation context then comes from the replay.
    pageA.onBackStackChanged(listOf("PageA"))
    pageB.onBackStackChanged(listOf("PageB"))
    pageBLifecycle.currentState = Lifecycle.State.RESUMED
    pageALifecycle.currentState = Lifecycle.State.STARTED
    val propagationContext = fixture.scope.propagationContext
    fixture.breadcrumbs.clear()

    pageALifecycle.currentState = Lifecycle.State.RESUMED
    pageBLifecycle.currentState = Lifecycle.State.STARTED

    assertThat(fixture.startedTransactions).isEmpty()
    assertThat(fixture.scope.transaction).isNull()
    assertThat(fixture.scope.screen).isEqualTo("/PageA")
    assertThat(fixture.breadcrumbs.map { it.data }).containsExactly(mapOf("to" to "/PageA"))
    assertThat(fixture.scope.propagationContext).isNotSameInstanceAs(propagationContext)
  }

  @Test
  fun `regaining the nav lease after observing an empty back stack starts no nav transaction`() {
    val fixture = Fixture()
    val pageALifecycle = fixture.resumedLifecycle()
    val pageBLifecycle = fixture.resumedLifecycle().apply { currentState = Lifecycle.State.STARTED }
    val pageA = fixture.getSut(pageALifecycle)
    val pageB = fixture.getSut(pageBLifecycle)
    pageA.onBackStackChanged(listOf("PageA"))
    pageB.onBackStackChanged(emptyList())

    pageBLifecycle.currentState = Lifecycle.State.RESUMED

    assertThat(fixture.startedTransactions.map { it.name }).containsExactly("/PageA")
    assertThat(fixture.scope.transaction).isNull()
  }

  @Test
  fun `dispose() - clears the nav data it published`() {
    val fixture = Fixture()
    val sut = fixture.getSut()
    sut.onBackStackChanged(listOf("Home"))

    sut.dispose()

    assertThat(fixture.scope.screen).isNull()
  }

  @Test
  fun `dispose() - without the nav lease, leaves the leaseholder's nav data and nav transaction alone`() {
    val fixture = Fixture()
    val homeObserver = fixture.getSut()
    val settingsObserver = fixture.getSut()
    homeObserver.onBackStackChanged(listOf("Home"))
    // The settings observer's owner is also resumed, so it takes the nav lease from the home one.
    settingsObserver.onBackStackChanged(listOf("Settings"))
    val settingsTransaction = fixture.startedTransaction("/Settings")

    homeObserver.dispose()

    assertThat(fixture.scope.screen).isEqualTo("/Settings")
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY])
      .isEqualTo(mapOf("Back Stack" to listOf(mapOf("entry" to "/Settings"))))
    assertThat(fixture.scope.transaction).isSameInstanceAs(settingsTransaction)
    assertThat(settingsTransaction.isFinished).isFalse()
  }

  @Test
  fun `dispose() - after its lifecycle is destroyed, leaves the leaseholder's nav data and nav transaction alone`() {
    val fixture = Fixture()
    val homeLifecycle = fixture.resumedLifecycle()
    val homeObserver = fixture.getSut(homeLifecycle)
    val settingsObserver = fixture.getSut()
    homeObserver.onBackStackChanged(listOf("Home"))
    homeLifecycle.currentState = Lifecycle.State.STARTED
    settingsObserver.onBackStackChanged(listOf("Settings"))
    val settingsTransaction = fixture.startedTransaction("/Settings")
    // Destroying the lifecycle before the composition is disposed unregisters the home observer's
    // owner from the coordinator ahead of its dispose() call.
    homeLifecycle.currentState = Lifecycle.State.DESTROYED

    homeObserver.dispose()

    assertThat(fixture.scope.screen).isEqualTo("/Settings")
    assertThat(fixture.scope.contexts[NAVIGATION_CONTEXT_KEY])
      .isEqualTo(mapOf("Back Stack" to listOf(mapOf("entry" to "/Settings"))))
    assertThat(fixture.scope.transaction).isSameInstanceAs(settingsTransaction)
    assertThat(settingsTransaction.isFinished).isFalse()
  }

  private class Fixture {
    val scope =
      Scope(
        SentryOptions.empty().apply {
          dsn = "http://key@localhost/proj"
          tracesSampleRate = 1.0
          idleTimeout = null
          deadlineTimeout = 0
        }
      )
    val coordinator = NavLeaseCoordinator()
    val startedTransactions = mutableListOf<SentryTracer>()
    val breadcrumbs = mutableListOf<Breadcrumb>()
    private val scopes =
      mock<IScopes>().also { scopes ->
        whenever(scopes.options).thenReturn(scope.options)
        doAnswer {
            breadcrumbs += it.arguments[0] as Breadcrumb
            null
          }
          .whenever(scopes)
          .addBreadcrumb(any<Breadcrumb>(), any<Hint>())
        doAnswer {
            (it.arguments[0] as ScopeCallback).run(scope)
            null
          }
          .whenever(scopes)
          .configureScope(any())
        doAnswer {
            val transactionContext = it.arguments[0] as TransactionContext
            val transactionOptions = it.arguments[1] as TransactionOptions
            SentryTracer(transactionContext, scopes, transactionOptions)
              .also(startedTransactions::add)
          }
          .whenever(scopes)
          .startTransaction(any<TransactionContext>(), any<TransactionOptions>())
      }

    fun startedTransaction(name: String): SentryTracer = startedTransactions.single {
      it.name == name
    }

    /** Registers another owner that takes the nav lease and then pauses, keeping the lease. */
    fun givenPausedLeaseholder() {
      val other = Any()
      coordinator.registerCandidate(other, Lifecycle.State.RESUMED, {}, {})
      coordinator.onBackStackUpdate(other, scope)
      coordinator.pauseCandidate(other, scope)
      check(coordinator.hasLease(other))
    }

    fun getSut(
      lifecycle: Lifecycle = resumedLifecycle(),
      options: SentryNavOptions = SentryNavOptions(),
    ): CoordinatedBackStackObserver<String> =
      CoordinatedBackStackObserver(
          scopes = scopes,
          options = options,
          entryMapper =
            ForwardingBackStackEntryMapper {
              BackStackEntryMapper<String> { SentryBackStackEntry(it) }
            },
          coordinator = coordinator,
        )
        .apply { attach(lifecycle) }

    /**
     * Owners of the lifecycles created by [resumedLifecycle]. `LifecycleRegistry` holds its owner
     * weakly and throws once it's collected, so the fixture keeps them alive for the test.
     */
    private val lifecycleOwners = mutableListOf<LifecycleOwner>()

    fun resumedLifecycle(): LifecycleRegistry =
      object : LifecycleOwner {
          override val lifecycle =
            LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        .also(lifecycleOwners::add)
        .lifecycle
  }
}
