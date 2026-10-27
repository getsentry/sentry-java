package io.sentry.compose.navigation3

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.common.truth.Truth.assertThat
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.compose.navigation3.lifecycle.LeasedNavState
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
    // "/Result" was reached without the nav lease, so it has no nav transaction to bind.
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
    private val scopes =
      mock<IScopes>().also { scopes ->
        whenever(scopes.options).thenReturn(scope.options)
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
      val otherLifecycle = resumedLifecycle()
      val other = checkNotNull(coordinator.register(otherLifecycle, scopes, NoOpLeasedNavState))
      other.onBackStackUpdate()
      otherLifecycle.currentState = Lifecycle.State.STARTED
      check(other.hasLease)
    }

    fun getSut(lifecycle: Lifecycle = resumedLifecycle()): CoordinatedBackStackObserver<String> =
      CoordinatedBackStackObserver(
          scopes = scopes,
          options = SentryNavOptions(),
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

  /** Nav state for a competing owner that has nothing to publish. */
  private object NoOpLeasedNavState : LeasedNavState {
    override fun publishTo(scope: IScope) = Unit

    override fun unpublishFrom(scope: IScope) = Unit

    override fun onUnregistered(scope: IScope?) = Unit
  }
}
