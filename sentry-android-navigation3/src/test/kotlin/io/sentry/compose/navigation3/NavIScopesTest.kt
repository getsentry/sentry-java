package io.sentry.compose.navigation3

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.common.truth.Truth.assertThat
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.ScopeType
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NavIScopesTest {

  private data class HomeScreen(val id: String = "home")

  private data class ProfileScreen(val userId: String)

  private class Fixture {
    val lifecycleOwner = mock<LifecycleOwner>()
    val lifecycle = mock<Lifecycle>()
    val scope = Scope(createOptions())
    val scopes = mock<IScopes>()
    val coordinator = NavLeaseCoordinator()
    val breadcrumbs = mutableListOf<Breadcrumb>()
    val transactions = mutableListOf<SentryTracer>()

    init {
      whenever(lifecycle.currentState).thenReturn(Lifecycle.State.CREATED)
      whenever(scopes.options).thenReturn(scope.options)
      whenever(scopes.getSpan()).thenAnswer { scope.span }
      whenever(scopes.getTransaction()).thenAnswer { scope.transaction }
      doAnswer {
          (it.arguments[0] as ScopeCallback).run(scope)
          null
        }
        .whenever(scopes)
        .configureScope(any())
      doAnswer {
          (it.arguments[1] as ScopeCallback).run(scope)
          null
        }
        .whenever(scopes)
        .configureScope(anyOrNull<ScopeType>(), any())
      doAnswer {
          val transactionContext = it.arguments[0] as TransactionContext
          val transactionOptions = it.arguments[1] as TransactionOptions
          SentryTracer(transactionContext, scopes, transactionOptions).also(transactions::add)
        }
        .whenever(scopes)
        .startTransaction(any<TransactionContext>(), any<TransactionOptions>())
      doAnswer {
          breadcrumbs += it.arguments[0] as Breadcrumb
          null
        }
        .whenever(scopes)
        .addBreadcrumb(any<Breadcrumb>(), anyOrNull<Hint>())
    }

    fun newPair(): Pair<NavIScopes, BackStackObserver<Any>> {
      val lifecycleAwareScopes = NavIScopes(scopes, coordinator)
      val observer =
        BackStackObserver(
          scopes = lifecycleAwareScopes,
          options = SentryNavOptions(),
          entryMapper =
            ForwardingBackStackEntryMapper {
              BackStackEntryMapper { entry ->
                SentryBackStackEntry(entry::class.simpleName ?: "<unknown>")
              }
            },
        )
      return lifecycleAwareScopes to observer
    }

    fun resume(scopes: NavIScopes) {
      scopes.onStateChanged(lifecycleOwner, Lifecycle.Event.ON_RESUME)
    }

    fun update(
      scopes: NavIScopes,
      observer: BackStackObserver<Any>,
      backStack: List<Any>,
    ) {
      scopes.runBackStackUpdate { observer.onBackStackChanged(backStack) }
    }

    private companion object {
      fun createOptions(): SentryOptions =
        SentryOptions().apply {
          dsn = "http://key@localhost/proj"
          setTracesSampleRate(1.0)
          isEnableScreenTracking = true
          idleTimeout = null
          deadlineTimeout = 0
        }
    }
  }

  @Test
  fun `created observer starts its transaction and publishes data when resumed`() {
    val fixture = Fixture()
    val (scopes, observer) = fixture.newPair()
    scopes.attach(fixture.lifecycle)

    assertThat(fixture.transactions).isEmpty()
    assertThat(fixture.scope.transaction).isNull()

    fixture.update(scopes, observer, listOf(HomeScreen()))

    assertThat(fixture.transactions).hasSize(1)
    assertThat(fixture.transactions.single().name).isEqualTo("/HomeScreen")
    assertThat(fixture.scope.transaction).isSameInstanceAs(fixture.transactions.single())
    assertThat(fixture.scope.screen).isNull()
    assertThat(fixture.scope.navigationBackStack()).isNull()
    assertThat(fixture.breadcrumbs).isEmpty()

    fixture.resume(scopes)

    assertThat(fixture.scope.screen).isEqualTo("/HomeScreen")
    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(listOf(mapOf("entry" to "/HomeScreen")))
    assertThat(fixture.breadcrumbs).hasSize(1)
    assertThat(fixture.breadcrumbs.single().data["to"]).isEqualTo("/HomeScreen")
  }

  @Test
  fun `paused observer clears published data and restores its latest data without a breadcrumb`() {
    val fixture = Fixture()
    val (scopes, observer) = fixture.newPair()
    scopes.attach(fixture.lifecycle)
    fixture.resume(scopes)
    val home = HomeScreen()

    fixture.update(scopes, observer, listOf(home))
    val initialTransaction = fixture.transactions.single()
    scopes.onStateChanged(fixture.lifecycleOwner, Lifecycle.Event.ON_PAUSE)

    assertThat(fixture.scope.screen).isNull()
    assertThat(fixture.scope.navigationBackStack()).isNull()
    assertThat(initialTransaction.isFinished).isFalse()
    assertThat(fixture.scope.transaction).isSameInstanceAs(initialTransaction)

    fixture.update(scopes, observer, listOf(home, ProfileScreen("123")))

    assertThat(fixture.scope.screen).isNull()
    assertThat(fixture.scope.navigationBackStack()).isNull()
    assertThat(fixture.breadcrumbs).hasSize(1)
    assertThat(initialTransaction.isFinished).isTrue()
    assertThat(fixture.transactions).hasSize(2)
    assertThat(fixture.transactions.last().isFinished).isFalse()

    fixture.resume(scopes)

    assertThat(fixture.scope.screen).isEqualTo("/ProfileScreen")
    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(listOf(mapOf("entry" to "/ProfileScreen"), mapOf("entry" to "/HomeScreen")))
    assertThat(fixture.breadcrumbs).hasSize(1)
  }

  @Test
  fun `new observer replaces the previous transaction on its first back stack update`() {
    val fixture = Fixture()
    val (firstScopes, firstObserver) = fixture.newPair()
    firstScopes.attach(fixture.lifecycle)
    fixture.resume(firstScopes)
    fixture.update(firstScopes, firstObserver, listOf(HomeScreen()))
    val firstTransaction = fixture.transactions.single()

    val (secondScopes, secondObserver) = fixture.newPair()
    secondScopes.attach(fixture.lifecycle)

    assertThat(firstTransaction.isFinished).isFalse()

    fixture.update(secondScopes, secondObserver, listOf(ProfileScreen("123")))
    val secondTransaction = fixture.transactions.last()

    assertThat(firstTransaction.isFinished).isTrue()
    assertThat(secondTransaction.isFinished).isFalse()
    assertThat(fixture.scope.transaction).isSameInstanceAs(secondTransaction)
  }

  @Test
  fun `observer disposed before a back stack update preserves the previous transaction`() {
    val fixture = Fixture()
    val (firstScopes, firstObserver) = fixture.newPair()
    firstScopes.attach(fixture.lifecycle)
    fixture.update(firstScopes, firstObserver, listOf(HomeScreen()))
    val firstTransaction = fixture.transactions.single()

    val (secondScopes, secondObserver) = fixture.newPair()
    secondScopes.attach(fixture.lifecycle)
    secondObserver.cleanup()
    secondScopes.dispose()

    assertThat(firstTransaction.isFinished).isFalse()
    assertThat(fixture.scope.transaction).isSameInstanceAs(firstTransaction)

    fixture.update(firstScopes, firstObserver, listOf(HomeScreen(), ProfileScreen("123")))

    assertThat(firstTransaction.isFinished).isTrue()
    assertThat(fixture.transactions.last().name).isEqualTo("/ProfileScreen")
    assertThat(fixture.transactions.last().isFinished).isFalse()
  }

  @Test
  fun `late cleanup from previous observer preserves current observer state`() {
    val fixture = Fixture()
    val (firstScopes, firstObserver) = fixture.newPair()
    firstScopes.attach(fixture.lifecycle)
    fixture.resume(firstScopes)
    fixture.update(firstScopes, firstObserver, listOf(HomeScreen()))

    val (secondScopes, secondObserver) = fixture.newPair()
    secondScopes.attach(fixture.lifecycle)
    fixture.update(secondScopes, secondObserver, listOf(ProfileScreen("123")))
    fixture.resume(secondScopes)
    val secondTransaction = fixture.transactions.last()

    firstObserver.cleanup()
    firstScopes.dispose()

    assertThat(secondTransaction.isFinished).isFalse()
    assertThat(fixture.scope.transaction).isSameInstanceAs(secondTransaction)
    assertThat(fixture.scope.screen).isEqualTo("/ProfileScreen")
    assertThat(fixture.scope.navigationBackStack())
      .isEqualTo(listOf(mapOf("entry" to "/ProfileScreen")))
  }

  @Test
  fun `destroyed observer finishes its transaction`() {
    val fixture = Fixture()
    val (scopes, observer) = fixture.newPair()
    scopes.attach(fixture.lifecycle)
    fixture.update(scopes, observer, listOf(HomeScreen()))
    val transaction = fixture.transactions.single()

    scopes.onStateChanged(fixture.lifecycleOwner, Lifecycle.Event.ON_DESTROY)

    assertThat(transaction.isFinished).isTrue()
    assertThat(fixture.scope.transaction).isNull()
  }

  private fun IScope.navigationBackStack(): List<Map<String, Any?>>? {
    val navigationContext = contexts[NAVIGATION_CONTEXT_KEY] as? Map<*, *> ?: return null

    @Suppress("UNCHECKED_CAST")
    return navigationContext[BACKSTACK_KEY] as? List<Map<String, Any?>>
  }

  private companion object {
    const val NAVIGATION_CONTEXT_KEY = "navigation"
    const val BACKSTACK_KEY = "backstack"
  }
}
